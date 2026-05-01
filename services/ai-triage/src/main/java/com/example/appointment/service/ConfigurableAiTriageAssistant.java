package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.http.client.jersey3.Jersey3ClientProperties;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiChatModel;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiCohereChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class ConfigurableAiTriageAssistant implements AiTriageAssistant {

    private static final Logger LOG = Logger.getLogger(ConfigurableAiTriageAssistant.class);

    private static final String PROVIDER_OPENAI = "openai";
    private static final String PROVIDER_OCI = "oci";
    private static final String OCI_FAMILY_GENERIC = "generic";
    private static final String OCI_FAMILY_COHERE = "cohere";
    private static final String OCI_SERVING_ON_DEMAND = "on_demand";
    private static final String OCI_SERVING_DEDICATED = "dedicated";
    private static final String OCI_AUTH_API_KEY = "api_key";
    private static final String DEFAULT_OCI_CONFIG_FILE = "~/.oci/config";
    private static final String DEFAULT_OCI_PROFILE = "DEFAULT";
    private static final String DEFAULT_OCI_MODEL_FAMILY = OCI_FAMILY_GENERIC;
    private static final String DEFAULT_OCI_MODEL_NAME = "xai.grok-4-fast-reasoning";
    private static final String DEFAULT_PLACEHOLDER_API_KEY = "not-needed";

    private final Config config = ConfigProvider.getConfig();

    private ChatModel chatModel;
    private AiTriageAssistant delegate;

    @PostConstruct
    void init() {
        this.chatModel = createChatModel();
        this.delegate = AiServices.builder(AiTriageAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    @Override
    public AiTriageResult triage(String userMessage, List<TriageRequest.TicketTypeInfo> allowedTicketTypes) {
        return delegate.triage(userMessage, allowedTicketTypes);
    }

    @PreDestroy
    void destroy() {
        if (chatModel instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.warn("Failed to close AI triage chat model cleanly", e);
            }
        }
    }

    private ChatModel createChatModel() {
        String provider = optionalString("ai-triage.llm.provider")
                .orElse(PROVIDER_OCI)
                .toLowerCase(Locale.ROOT);

        return switch (provider) {
            case PROVIDER_OPENAI -> createOpenAiChatModel();
            case PROVIDER_OCI -> createOciChatModel();
            default -> throw new IllegalStateException(
                    "Unsupported ai-triage.llm.provider: " + provider + ". Expected 'openai' or 'oci'.");
        };
    }

    private ChatModel createOpenAiChatModel() {
        String modelName = requiredString(
                "Configure ai-triage.llm.openai.chat-model.model-name.",
                "ai-triage.llm.openai.chat-model.model-name");

        String baseUrl = optionalString("ai-triage.llm.openai.base-url").orElse(null);
        String apiKey = optionalString("ai-triage.llm.openai.api-key").orElse(null);

        if (isBlank(apiKey)) {
            if (isBlank(baseUrl)) {
                throw new IllegalStateException(
                        "OpenAI provider requires ai-triage.llm.openai.api-key (or OPENAI_API_KEY).");
            }
            apiKey = DEFAULT_PLACEHOLDER_API_KEY;
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .modelName(modelName)
                .apiKey(apiKey);

        if (!isBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        optionalDouble("ai-triage.llm.openai.chat-model.temperature").ifPresent(builder::temperature);
        optionalDuration("ai-triage.llm.openai.chat-model.timeout").ifPresent(builder::timeout);

        LOG.infov("AI triage configured for OpenAI-compatible provider, model={0}, baseUrl={1}",
                modelName, baseUrl == null ? "https://api.openai.com/v1" : baseUrl);
        return builder.build();
    }

    private ChatModel createOciChatModel() {
        String authMethod = optionalString("ai-triage.llm.oci.auth-method")
                .orElse(OCI_AUTH_API_KEY)
                .toLowerCase(Locale.ROOT);
        String configFile = expandHome(optionalString("ai-triage.llm.oci.config-file").orElse(DEFAULT_OCI_CONFIG_FILE));
        String profile = optionalString("ai-triage.llm.oci.profile").orElse(DEFAULT_OCI_PROFILE);
        String compartmentId = requiredString(
                "Configure ai-triage.llm.oci.compartment-id before enabling ai-triage.llm.provider=oci.",
                "ai-triage.llm.oci.compartment-id");
        String modelFamily = optionalString("ai-triage.llm.oci.chat-model.family")
                .orElse(DEFAULT_OCI_MODEL_FAMILY)
                .toLowerCase(Locale.ROOT);
        String modelName = optionalString("ai-triage.llm.oci.chat-model.model-name")
                .orElse(DEFAULT_OCI_MODEL_NAME);
        ServingMode.ServingType servingType = optionalString("ai-triage.llm.oci.chat-model.serving-type")
                .map(this::parseServingType)
                .orElse(ServingMode.ServingType.OnDemand);

        ConfigFileAuthenticationDetailsProvider authProvider = createAuthProvider(authMethod, configFile, profile);
        Region region = optionalString("ai-triage.llm.oci.region")
                .map(this::parseRegion)
                .orElse(authProvider.getRegion());
        GenerativeAiInferenceClient genAiClient = createOciClient(authProvider, region);

        LOG.infov("AI triage configured for OCI GenAI, auth={0}, family={1}, model={2}, region={3}, profile={4}",
                authMethod, modelFamily, modelName, region == null ? "<from client>" : region.getRegionId(), profile);

        return switch (modelFamily) {
            case OCI_FAMILY_GENERIC -> {
                OciGenAiChatModel.Builder builder = OciGenAiChatModel.builder()
                        .genAiClient(genAiClient)
                        .compartmentId(compartmentId)
                        .modelName(modelName)
                        .servingType(servingType);
                optionalDouble("ai-triage.llm.oci.chat-model.temperature").ifPresent(builder::temperature);
                optionalInt("ai-triage.llm.oci.chat-model.max-tokens").ifPresent(builder::maxTokens);
                yield builder.build();
            }
            case OCI_FAMILY_COHERE -> {
                OciGenAiCohereChatModel.Builder builder = OciGenAiCohereChatModel.builder()
                        .genAiClient(genAiClient)
                        .compartmentId(compartmentId)
                        .modelName(modelName)
                        .servingType(servingType);
                optionalDouble("ai-triage.llm.oci.chat-model.temperature").ifPresent(builder::temperature);
                optionalInt("ai-triage.llm.oci.chat-model.max-tokens").ifPresent(builder::maxTokens);
                yield builder.build();
            }
            default -> throw new IllegalStateException(
                    "Unsupported ai-triage.llm.oci.chat-model.family: " + modelFamily
                            + ". Expected 'generic' or 'cohere'.");
        };
    }

    private ConfigFileAuthenticationDetailsProvider createAuthProvider(String authMethod, String configFile, String profile) {
        return switch (authMethod) {
            case OCI_AUTH_API_KEY -> createApiKeyAuthProvider(configFile, profile);
            default -> throw new IllegalStateException(
                    "Unsupported ai-triage.llm.oci.auth-method: " + authMethod + ". Expected api_key.");
        };
    }

    private ConfigFileAuthenticationDetailsProvider createApiKeyAuthProvider(String configFile, String profile) {
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

    private ServingMode.ServingType parseServingType(String servingType) {
        return switch (servingType.trim().toLowerCase(Locale.ROOT)) {
            case OCI_SERVING_ON_DEMAND -> ServingMode.ServingType.OnDemand;
            case OCI_SERVING_DEDICATED -> ServingMode.ServingType.Dedicated;
            default -> throw new IllegalStateException(
                    "Unsupported ai-triage.llm.oci.chat-model.serving-type: " + servingType
                            + ". Expected ON_DEMAND or DEDICATED.");
        };
    }

    private String requiredString(String errorMessage, String key) {
        return optionalString(key).orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    private Optional<String> optionalString(String key) {
        return config.getOptionalValue(key, String.class)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(value -> !isUnresolvedExpression(value));
    }

    private Optional<Double> optionalDouble(String key) {
        return config.getOptionalValue(key, Double.class);
    }

    private Optional<Integer> optionalInt(String key) {
        return config.getOptionalValue(key, Integer.class);
    }

    private Optional<Duration> optionalDuration(String key) {
        return config.getOptionalValue(key, Duration.class);
    }

    private String expandHome(String path) {
        if (path.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), path.substring(2)).toString();
        }
        return path;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isUnresolvedExpression(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }
}
