package com.example.urgency.service;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.embedding.EmbeddingGenerator;
import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;
import io.helidon.config.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UrgencyInferenceService {

    private static final String PROVIDER_PROPERTY = "urgency.embedding-provider";
    private static final String MODEL_DIR_PROPERTY = "urgency.model.dir";
    private static final String DEFAULT_PROVIDER = "local";
    private static final String DEFAULT_MODEL_DIR = "model";
    private static final Config CONFIG = Config.create();

    private volatile FeedForwardNetwork scorerNet;
    private volatile EmbeddingGenerator embeddingGenerator;

    public UrgencyInferenceService() {
        validateStartupConfiguration();
    }

    public double score(String complaint) {
        if (complaint == null || complaint.isBlank()) {
            throw new IllegalArgumentException("complaint is required");
        }

        String provider = normalizedProvider();
        FeedForwardNetwork net = getScorerNet(provider);
        EmbeddingGenerator generator = getEmbeddingGenerator(provider);

        float[] vec = generator.embed(complaint);
        float score01 = net.predict(vec)[0];
        double score10 = Math.round(score01 * 20.0) / 2.0;
        return Math.max(0.0, Math.min(10.0, score10));
    }

    private void validateStartupConfiguration() {
        String provider = normalizedProvider();
        resolveScorerModelPath(provider);
        if ("openai".equals(provider)) {
            // Triggers key/config validation early for clear startup failure.
            new OpenAIEmbeddingGenerator();
        }
    }

    private String normalizedProvider() {
        String raw = readConfig(PROVIDER_PROPERTY, DEFAULT_PROVIDER);
        return "openai".equalsIgnoreCase(raw) ? "openai" : "local";
    }

    private String modelDir() {
        return readConfig(MODEL_DIR_PROPERTY, DEFAULT_MODEL_DIR);
    }

    private String readConfig(String key, String defaultValue) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty;
        }
        return CONFIG.get(key).asString().orElse(defaultValue);
    }

    private FeedForwardNetwork getScorerNet(String provider) {
        FeedForwardNetwork cached = scorerNet;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (scorerNet == null) {
                Path modelPath = resolveScorerModelPath(provider);
                try {
                    scorerNet = (FeedForwardNetwork) FileIO.createFromFile(modelPath.toString(), FeedForwardNetwork.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load scorer model from " + modelPath, e);
                }
            }
            return scorerNet;
        }
    }

    private EmbeddingGenerator getEmbeddingGenerator(String provider) {
        EmbeddingGenerator cached = embeddingGenerator;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (embeddingGenerator == null) {
                embeddingGenerator = "openai".equals(provider)
                        ? new OpenAIEmbeddingGenerator()
                        : new DJLEmbeddingGenerator();
            }
            return embeddingGenerator;
        }
    }

    private Path resolveScorerModelPath(String provider) {
        Path dir = Path.of(modelDir()).toAbsolutePath().normalize();
        String suffix = provider + ".dnet";

        List<Path> matches = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir, "*.dnet")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.endsWith(suffix)) {
                    matches.add(p);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read model directory: " + dir, e);
        }

        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "No scorer model found for provider '" + provider + "' in " + dir
                            + ". Expected file ending with '" + suffix + "'."
            );
        }

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple scorer models found for provider '" + provider + "' in " + dir
                            + ": " + matches + ". Keep only one to avoid ambiguity."
            );
        }

        return matches.get(0);
    }
}
