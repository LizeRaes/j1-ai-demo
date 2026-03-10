package com.example.urgency.embedding;

import com.example.urgency.model.Ticket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches embeddings under training/embeddings/{provider}/.
 * If dataset file contents unchanged, loads from cache. Otherwise computes and saves.
 */
public final class EmbeddingCache {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path embeddingsDir;
    private final String provider;

    public EmbeddingCache(Path trainingDir, String provider) {
        this.embeddingsDir = trainingDir.resolve("embeddings").resolve(provider);
        this.provider = provider;
    }

    public List<CachedEmbedding> loadOrCompute(List<Ticket> tickets, List<Path> datasetFiles,
                                               EmbeddingGenerator generator) throws IOException {
        Path manifestPath = embeddingsDir.resolve("manifest.json");
        Path dataPath = embeddingsDir.resolve("embeddings.json");
        Path projectRoot = Path.of("").toAbsolutePath().normalize();

        Map<String, String> currentHashes = new LinkedHashMap<>();
        for (Path p : datasetFiles) {
            String key = relativeToProjectRoot(projectRoot, p);
            currentHashes.put(key, sha256(p));
        }

        if (Files.exists(manifestPath) && Files.exists(dataPath)) {
            Manifest manifest = MAPPER.readValue(manifestPath.toFile(), Manifest.class);
            if (manifest.fileHashes.equals(currentHashes) && manifest.samplesCount == tickets.size()) {
                System.out.println("  using cached embeddings (" + provider + ", " + tickets.size() + " samples)");
                CacheData data = MAPPER.readValue(dataPath.toFile(), CacheData.class);
                List<CachedEmbedding> cached = new ArrayList<>();
                for (int i = 0; i < data.samples.size(); i++) {
                    Sample s = data.samples.get(i);
                    cached.add(new CachedEmbedding(s.embedding, roundToHalf10(s.urgency), s.text));
                }
                return cached;
            }
        }

        List<CachedEmbedding> computed = new ArrayList<>();
        int n = tickets.size();
        for (int i = 0; i < n; i++) {
            Ticket t = tickets.get(i);
            float[] emb = generator.embed(t.text());
            computed.add(new CachedEmbedding(emb, roundToHalf10(t.urgency()), t.text()));
            if ((i + 1) % 50 == 0 || i == n - 1) {
                System.out.println("  embedding " + (i + 1) + "/" + n + " (" + provider + ")");
            }
        }

        Files.createDirectories(embeddingsDir);
        Manifest manifest = new Manifest(
                datasetFiles.stream().map(p -> relativeToProjectRoot(projectRoot, p)).sorted().toList(),
                currentHashes,
                tickets.size()
        );
        MAPPER.writeValue(manifestPath.toFile(), manifest);

        List<Sample> samples = computed.stream()
                .map(c -> new Sample(c.text(), c.urgency(), c.embedding()))
                .toList();
        CacheData data = new CacheData(provider, computed.get(0).embedding().length, samples);
        MAPPER.writeValue(dataPath.toFile(), data);

        System.out.println("  cached " + computed.size() + " embeddings to " + embeddingsDir);
        return computed;
    }

    record Manifest(List<String> datasetFiles, Map<String, String> fileHashes, int samplesCount) {}
    record Sample(String text, double urgency, float[] embedding) {}
    record CacheData(String provider, int dimension, List<Sample> samples) {}

    private static double roundToHalf10(double u) {
        return Math.round(u * 2) / 2.0;
    }

    private static String sha256(Path p) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(p));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String relativeToProjectRoot(Path projectRoot, Path p) {
        Path abs = p.toAbsolutePath().normalize();
        return projectRoot.relativize(abs).toString();
    }
}
