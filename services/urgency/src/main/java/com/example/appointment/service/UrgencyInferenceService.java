package com.example.appointment.service;

import com.example.appointment.embedding.DJLEmbeddingGenerator;
import com.example.appointment.embedding.EmbeddingGenerator;
import com.example.appointment.embedding.OpenAIEmbeddingGenerator;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class UrgencyInferenceService {

    @ConfigProperty(name = "urgency.model.dir")
    String modelDir;

    @ConfigProperty(name = "urgency.embedding-provider", defaultValue = "local")
    String embeddingProvider;

    private volatile FeedForwardNetwork scorerNet;
    private volatile EmbeddingGenerator embeddingGenerator;

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

    private String normalizedProvider() {
        return "openai".equalsIgnoreCase(embeddingProvider) ? "openai" : "local";
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
        Path dir = Path.of(modelDir).toAbsolutePath().normalize();
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

    @PreDestroy
    void close() {
        EmbeddingGenerator eg = embeddingGenerator;
        if (eg instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception ignored) {
            }
        }
    }
}
