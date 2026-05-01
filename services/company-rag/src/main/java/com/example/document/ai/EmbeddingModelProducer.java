package com.example.document.ai;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.model.EmbedTextDetails;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.http.client.jersey3.Jersey3ClientProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class EmbeddingModelProducer {

    private static final Logger LOGGER = Logger.getLogger(EmbeddingModelProducer.class.getName());

    private static final String PROVIDER_OCI = "oci";
    private static final String PROVIDER_OPENAI = "openai";
    private static final String OCI_AUTH_API_KEY = "api_key";
    private static final String OCI_SERVING_ON_DEMAND = "on_demand";
    private static final String OCI_SERVING_DEDICATED = "dedicated";
    private static final String DEFAULT_OCI_MODEL_ID = "cohere.embed-english-v3.0";
    private static final String DEFAULT_OCI_EMBEDDING_REGION = "us-chicago-1";
    private static final String DEFAULT_OPENAI_MODEL = "text-embedding-3-large";

    @Inject
    Config config;

    @Produces
    @ApplicationScoped
    EmbeddingModel embeddingModel() {
        String provider = optionalString("company-rag.embedding.provider")
                .orElse(PROVIDER_OCI)
                .toLowerCase(Locale.ROOT);

        return switch (provider) {
            case PROVIDER_OCI -> createOciEmbeddingModel();
            case PROVIDER_OPENAI -> createOpenAiEmbeddingModel();
            default -> throw new IllegalStateException(
                    "Unsupported company-rag.embedding.provider: " + provider + ". Expected 'oci' or 'openai'.");
        };
    }

    void closeEmbeddingModel(@Disposes EmbeddingModel model) {
        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.warning("Failed to close embedding model cleanly: " + e.getMessage());
            }
        }
    }

    private EmbeddingModel createOpenAiEmbeddingModel() {
        String modelName = optionalString(
                "company-rag.embedding.openai.model-name",
                "quarkus.langchain4j.openai.embedding-model.model-name")
                .orElse(DEFAULT_OPENAI_MODEL);
        String apiKey = optionalString(
                "company-rag.embedding.openai.api-key",
                "quarkus.langchain4j.openai.api-key")
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI embeddings require company-rag.embedding.openai.api-key or OPENAI_API_KEY."));
        String baseUrl = optionalString("company-rag.embedding.openai.base-url").orElse(null);

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        LOGGER.info("Company RAG embeddings configured for OpenAI-compatible provider, model=" + modelName);
        return builder.build();
    }

    private EmbeddingModel createOciEmbeddingModel() {
        String authMethod = optionalString("company-rag.embedding.oci.auth-method")
                .orElse(OCI_AUTH_API_KEY)
                .toLowerCase(Locale.ROOT);
        if (!OCI_AUTH_API_KEY.equals(authMethod)) {
            throw new IllegalStateException(
                    "Unsupported company-rag.embedding.oci.auth-method: " + authMethod + ". Expected api_key.");
        }

        String configFile = expandHome(optionalConfigOrEnv("company-rag.embedding.oci.config-file", "OCI_CONFIG_FILE")
                .orElse(Path.of(System.getProperty("user.home"), ".oci", "config").toString()));
        String profile = optionalConfigOrEnv("company-rag.embedding.oci.profile", "OCI_PROFILE").orElse("DEFAULT");
        String compartmentId = requiredOciCompartmentId(
                "Configure company-rag.embedding.oci.compartment-id before using OCI embeddings.",
                "company-rag.embedding.oci.compartment-id",
                "OCI_COMPARTMENT_ID");
        String modelId = optionalConfigOrEnv("company-rag.embedding.oci.model-id", "OCI_EMBEDDING_MODEL_ID")
                .orElse(DEFAULT_OCI_MODEL_ID);
        String servingType = optionalConfigOrEnv("company-rag.embedding.oci.serving-type",
                        "OCI_EMBEDDING_SERVING_TYPE")
                .orElse(OCI_SERVING_ON_DEMAND)
                .toLowerCase(Locale.ROOT);

        ConfigFileAuthenticationDetailsProvider authProvider = createAuthProvider(configFile, profile);
        Region region = resolveRegion(authProvider, servingType);
        GenerativeAiInferenceClient client = createOciClient(authProvider, region);
        ServingMode servingMode = createServingMode(modelId, servingType);
        EmbedTextDetails.InputType inputType = optionalConfigOrEnv("company-rag.embedding.oci.input-type",
                        "OCI_EMBEDDING_INPUT_TYPE")
                .map(this::parseInputType)
                .orElse(null);

        LOGGER.info("Company RAG embeddings configured for OCI GenAI, model=" + modelId
                + ", region=" + (region == null ? "<from client>" : region.getRegionId())
                + ", profile=" + profile
                + ", compartment=" + maskOcid(compartmentId));

        return new OciGenAiEmbeddingModel(client, compartmentId, servingMode, inputType, modelId);
    }

    private ServingMode createServingMode(String modelId, String servingType) {
        return switch (servingType) {
            case OCI_SERVING_ON_DEMAND -> OnDemandServingMode.builder()
                    .modelId(modelId)
                    .build();
            case OCI_SERVING_DEDICATED -> DedicatedServingMode.builder()
                    .endpointId(requiredConfigOrEnv(
                            "Configure company-rag.embedding.oci.endpoint-id for dedicated OCI embeddings.",
                            "company-rag.embedding.oci.endpoint-id",
                            "OCI_EMBEDDING_ENDPOINT_ID"))
                    .build();
            default -> throw new IllegalStateException(
                    "Unsupported company-rag.embedding.oci.serving-type: " + servingType
                            + ". Expected ON_DEMAND or DEDICATED.");
        };
    }

    private Region resolveRegion(ConfigFileAuthenticationDetailsProvider authProvider, String servingType) {
        Optional<String> configuredRegion = optionalConfigOrEnv("company-rag.embedding.oci.region",
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
                    "Unsupported company-rag.embedding.oci.input-type: " + inputType);
        };
    }

    private String requiredString(String errorMessage, String key) {
        return optionalString(key).orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    private String requiredConfigOrEnv(String errorMessage, String key, String... environmentKeys) {
        return optionalConfigOrEnv(key, environmentKeys)
                .orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    private String requiredOciCompartmentId(String errorMessage, String key, String... environmentKeys) {
        String value = requiredConfigOrEnv(errorMessage, key, environmentKeys);
        if (!value.startsWith("ocid1.compartment.") && !value.startsWith("ocid1.tenancy.")) {
            throw new IllegalStateException(errorMessage
                    + " Value must be a compartment OCID, or a tenancy OCID when using the root compartment.");
        }
        return value;
    }

    private Optional<String> optionalString(String key) {
        return config.getOptionalValue(key, String.class)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(value -> !isUnresolvedExpression(value));
    }

    private Optional<String> optionalString(String firstKey, String secondKey) {
        return optionalString(firstKey).or(() -> optionalString(secondKey));
    }

    private Optional<String> optionalConfigOrEnv(String key, String... environmentKeys) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return Optional.of(fromSystemProperty.trim());
        }
        for (String environmentKey : environmentKeys) {
            String fromEnvironment = System.getenv(environmentKey);
            if (fromEnvironment != null && !fromEnvironment.isBlank()) {
                return Optional.of(fromEnvironment.trim());
            }
        }
        return optionalString(key);
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

    private String maskOcid(String ocid) {
        if (ocid == null || ocid.length() <= 12) {
            return "<set>";
        }
        return ocid.substring(0, Math.min(ocid.length(), 18)) + "..." + ocid.substring(ocid.length() - 8);
    }
}
