package com.example.ticket.ai;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.model.EmbedTextDetails;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.http.client.jersey3.Jersey3ClientProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

@Service.Singleton
@Service.Named("ticket-embedding-model")
public class ConfigurableEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(ConfigurableEmbeddingModel.class.getName());

    private static final String PROVIDER_OCI = "oci";
    private static final String PROVIDER_OPENAI = "openai";
    private static final String OCI_SERVING_ON_DEMAND = "on_demand";
    private static final String OCI_SERVING_DEDICATED = "dedicated";
    private static final String DEFAULT_OCI_MODEL_ID = "cohere.embed-english-v3.0";
    private static final String DEFAULT_OCI_EMBEDDING_REGION = "us-chicago-1";
    private static final String DEFAULT_OPENAI_MODEL = "text-embedding-3-large";

    private final EmbeddingModel delegate;

    @Service.Inject
    public ConfigurableEmbeddingModel(Config config) {
        String provider = read(config, "similar-tickets.embedding.provider", PROVIDER_OCI)
                .toLowerCase(Locale.ROOT);
        this.delegate = switch (provider) {
            case PROVIDER_OCI -> createOciEmbeddingModel(config);
            case PROVIDER_OPENAI -> createOpenAiEmbeddingModel(config);
            default -> throw new IllegalStateException(
                    "Unsupported similar-tickets.embedding.provider: " + provider
                            + ". Expected 'oci' or 'openai'.");
        };
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return delegate.embedAll(textSegments);
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private EmbeddingModel createOpenAiEmbeddingModel(Config config) {
        String modelName = read(config, "similar-tickets.embedding.openai.model-name", DEFAULT_OPENAI_MODEL);
        String apiKey = read(config, "similar-tickets.embedding.openai.api-key", System.getenv("OPENAI_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI embeddings require similar-tickets.embedding.openai.api-key or OPENAI_API_KEY.");
        }
        String baseUrl = read(config, "similar-tickets.embedding.openai.base-url", "");

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (!baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private EmbeddingModel createOciEmbeddingModel(Config config) {
        String configFile = expandHome(read(config, "similar-tickets.embedding.oci.config-file",
                Path.of(System.getProperty("user.home"), ".oci", "config").toString(), "OCI_CONFIG_FILE"));
        String profile = read(config, "similar-tickets.embedding.oci.profile", "DEFAULT", "OCI_PROFILE");
        String compartmentId = requiredOciCompartmentId(read(config,
                        "similar-tickets.embedding.oci.compartment-id", "", "OCI_COMPARTMENT_ID"),
                "Configure similar-tickets.embedding.oci.compartment-id or OCI_COMPARTMENT_ID "
                        + "before using OCI embeddings.");
        String modelId = read(config, "similar-tickets.embedding.oci.model-id", DEFAULT_OCI_MODEL_ID,
                "OCI_EMBEDDING_MODEL_ID");
        String servingType = read(config, "similar-tickets.embedding.oci.serving-type", OCI_SERVING_ON_DEMAND,
                "OCI_EMBEDDING_SERVING_TYPE").toLowerCase(Locale.ROOT);

        ConfigFileAuthenticationDetailsProvider authProvider = createAuthProvider(configFile, profile);
        Region region = resolveRegion(config, authProvider, servingType);
        GenerativeAiInferenceClient client = createOciClient(authProvider, region);
        ServingMode servingMode = createServingMode(config, modelId, servingType);
        EmbedTextDetails.InputType inputType = readOptional(config, "similar-tickets.embedding.oci.input-type",
                        "OCI_EMBEDDING_INPUT_TYPE")
                .map(this::parseInputType)
                .orElse(null);

        LOGGER.info("Similar tickets embeddings configured for OCI GenAI, model=" + modelId
                + ", region=" + regionName(region)
                + ", profile=" + profile
                + ", compartment=" + maskOcid(compartmentId));
        return new OciGenAiEmbeddingModel(client, compartmentId, servingMode, inputType, modelId);
    }

    private ServingMode createServingMode(Config config, String modelId, String servingType) {
        return switch (servingType) {
            case OCI_SERVING_ON_DEMAND -> OnDemandServingMode.builder()
                    .modelId(modelId)
                    .build();
            case OCI_SERVING_DEDICATED -> DedicatedServingMode.builder()
                    .endpointId(required(read(config, "similar-tickets.embedding.oci.endpoint-id", "",
                                    "OCI_EMBEDDING_ENDPOINT_ID"),
                            "Configure similar-tickets.embedding.oci.endpoint-id "
                                    + "or OCI_EMBEDDING_ENDPOINT_ID for dedicated OCI embeddings."))
                    .build();
            default -> throw new IllegalStateException(
                    "Unsupported similar-tickets.embedding.oci.serving-type: " + servingType
                            + ". Expected ON_DEMAND or DEDICATED.");
        };
    }

    private Region resolveRegion(
            Config config,
            ConfigFileAuthenticationDetailsProvider authProvider,
            String servingType) {
        Optional<String> configuredRegion = readOptional(config, "similar-tickets.embedding.oci.region",
                "OCI_GENAI_REGION", "OCI_REGION");
        if (configuredRegion.isPresent()) {
            return parseRegion(configuredRegion.get());
        }
        if (OCI_SERVING_DEDICATED.equals(servingType)) {
            return authProvider.getRegion();
        }
        return parseRegion(DEFAULT_OCI_EMBEDDING_REGION);
    }

    private ConfigFileAuthenticationDetailsProvider createAuthProvider(String configFile, String profile) {
        try {
            return new ConfigFileAuthenticationDetailsProvider(configFile, profile);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load OCI API key config from " + configFile + " using profile " + profile + ".", e);
        }
    }

    private GenerativeAiInferenceClient createOciClient(
            ConfigFileAuthenticationDetailsProvider authProvider, Region region) {
        GenerativeAiInferenceClient client = new GenerativeAiInferenceClient(
                authProvider,
                null,
                builder -> builder.property(
                        Jersey3ClientProperties.USE_JERSEY_DEFAULT_EXECUTOR_SERVICE_PROVIDER, true));
        if (region != null) {
            client.setRegion(region);
        }
        return client;
    }

    private Region parseRegion(String regionId) {
        try {
            return Region.fromRegionCodeOrId(regionId);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unsupported OCI region: " + regionId, e);
        }
    }

    private EmbedTextDetails.InputType parseInputType(String inputType) {
        String normalized = inputType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SEARCH_DOCUMENT" -> EmbedTextDetails.InputType.SearchDocument;
            case "SEARCH_QUERY" -> EmbedTextDetails.InputType.SearchQuery;
            case "CLASSIFICATION" -> EmbedTextDetails.InputType.Classification;
            case "CLUSTERING" -> EmbedTextDetails.InputType.Clustering;
            case "IMAGE" -> EmbedTextDetails.InputType.Image;
            default -> throw new IllegalStateException(
                    "Unsupported similar-tickets.embedding.oci.input-type: " + inputType);
        };
    }

    private String read(Config config, String key, String defaultValue, String... environmentKeys) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty.trim();
        }
        for (String environmentKey : environmentKeys) {
            String fromEnvironment = System.getenv(environmentKey);
            if (fromEnvironment != null && !fromEnvironment.isBlank()) {
                return fromEnvironment.trim();
            }
        }
        return config.get(key).asString()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(value -> !isUnresolvedExpression(value))
                .orElse(defaultValue == null ? "" : defaultValue);
    }

    private Optional<String> readOptional(Config config, String key, String... environmentKeys) {
        String value = read(config, key, "", environmentKeys);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String required(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }

    private String requiredOciCompartmentId(String value, String errorMessage) {
        String required = required(value, errorMessage);
        if (!required.startsWith("ocid1.compartment.") && !required.startsWith("ocid1.tenancy.")) {
            throw new IllegalStateException(errorMessage
                    + " Value must be a compartment OCID, or a tenancy OCID when using the root compartment.");
        }
        return required;
    }

    private String expandHome(String path) {
        if (path.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), path.substring(2)).toString();
        }
        return path;
    }

    private boolean isUnresolvedExpression(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    private String regionName(Region region) {
        return region == null ? "<from client>" : region.getRegionId();
    }

    private String maskOcid(String ocid) {
        if (ocid == null || ocid.length() <= 12) {
            return "<set>";
        }
        return ocid.substring(0, Math.min(ocid.length(), 18)) + "..." + ocid.substring(ocid.length() - 8);
    }
}
