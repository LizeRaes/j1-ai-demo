package com.example.urgency;

import com.example.urgency.data.DatasetLoader;
import com.example.urgency.embedding.CachedEmbedding;
import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.embedding.EmbeddingCache;
import com.example.urgency.embedding.EmbeddingGenerator;
import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
import com.example.urgency.evaluation.Metrics;
import com.example.urgency.model.Ticket;
import com.example.urgency.training.UrgencyTrainer;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;

public class Main {

    private static final String DEFAULT_DATASET = "training/dataset";
    private static final String DEFAULT_EXPORT = "training/export/model.dnet";
    private static final String DEFAULT_TRAINING_DIR = "training";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            System.err.println("Error:");
            t.printStackTrace(System.err);
            System.exit(1);
        }
        Runtime.getRuntime().halt(0);  // exit without shutdown hooks to avoid Log4j async error
    }

    private static void run(String[] args) throws Exception {
        String datasetPath = DEFAULT_DATASET;
        String exportPath = DEFAULT_EXPORT;
        String trainingDir = DEFAULT_TRAINING_DIR;
        String embeddingProvider = "local";

        for (int i = 0; i < args.length; i++) {
            if ("--dataset".equals(args[i]) && i + 1 < args.length) {
                datasetPath = args[++i];
            } else if ("--export".equals(args[i]) && i + 1 < args.length) {
                exportPath = args[++i];
            } else if ("--training-dir".equals(args[i]) && i + 1 < args.length) {
                trainingDir = args[++i];
            } else if ("--embedding-provider".equals(args[i]) && i + 1 < args.length) {
                embeddingProvider = "openai".equals(args[++i].toLowerCase()) ? "openai" : "local";
            }
        }

        System.out.println("Urgency Training Pipeline");
        System.out.println("  dataset: " + datasetPath);
        System.out.println("  export:  " + exportPath);
        System.out.println("  embedding: " + embeddingProvider);

        runWithCachedEmbeddings(datasetPath, exportPath, trainingDir, embeddingProvider);
        System.out.println("Finished");
    }

    private static void runWithCachedEmbeddings(String datasetPath, String exportPath, String trainingDir,
                                                String provider) throws Exception {
        var loadResult = DatasetLoader.load(Path.of(datasetPath));
        List<Ticket> tickets = loadResult.tickets();
        List<Path> sourceFiles = loadResult.sourceFiles();
        System.out.println("  loaded " + tickets.size() + " tickets from " + sourceFiles.size() + " files");

        EmbeddingCache cache = new EmbeddingCache(Path.of(trainingDir), provider);
        List<CachedEmbedding> cached;
        EmbeddingGenerator embeddingGen = "openai".equals(provider)
                ? new OpenAIEmbeddingGenerator()
                : new DJLEmbeddingGenerator();
        try {
            cached = cache.loadOrCompute(tickets, sourceFiles, embeddingGen);
        } finally {
            if (embeddingGen instanceof AutoCloseable ac) {
                try { ac.close(); } catch (Exception ignored) {}
            }
        }

        // Pair tickets with cached embeddings (same order), shuffle, split
        record Pair(Ticket t, CachedEmbedding c) {}
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < tickets.size(); i++) {
            pairs.add(new Pair(tickets.get(i), cached.get(i)));
        }
        Collections.shuffle(pairs, new java.util.Random());
        int splitIdx = (int) (pairs.size() * 0.8);
        var trainPairs = pairs.subList(0, splitIdx);
        var testPairs = pairs.subList(splitIdx, pairs.size());

        List<CachedEmbedding> trainCached = trainPairs.stream().map(p -> p.c).toList();
        List<CachedEmbedding> testCached = testPairs.stream().map(p -> p.c).toList();

        UrgencyTrainer trainer = new UrgencyTrainer(provider);
        var trainScorerSet = trainer.buildScorerDataSetFromCache(trainCached, "training data (scorer)");
        var trainBinarySet = trainer.buildBinaryDataSetFromCache(trainCached, "training data (binary)");

        FeedForwardNetwork scorerNet = trainer.trainScorer(trainScorerSet);
        FeedForwardNetwork binaryNet = trainer.trainBinary(trainBinarySet);

        System.out.println("\nValidating...");
        System.out.println("Validation results (round to 0.5 before binary critical assessment)");
        System.out.println("────────────────────────────────────────");
        var scorerResult = Metrics.evaluateScorer(scorerNet, testCached);
        scorerResult.printSummary();
        var binaryResult = Metrics.evaluateBinary(binaryNet, testCached);
        binaryResult.printSummary();
        System.out.println("────────────────────────────────────────");

        System.out.println("\n  First 10 validation samples (scorer: 0-10; binary: critical/non-critical):");
        for (int i = 0; i < Math.min(10, testCached.size()); i++) {
            CachedEmbedding c = testCached.get(i);
            float score01 = scorerNet.predict(c.embedding())[0];
            float score10 = (float) (Math.round(score01 * 20) / 2.0);
            float pCritical = binaryNet.predict(c.embedding())[0];
            boolean critical = pCritical >= Metrics.CRITICAL_THRESHOLD;
            double actual10 = c.urgency();
            System.out.printf("    score %.1f %s (actual %.1f) %s%n", score10, critical ? "[CRITICAL]" : "", actual10, c.text());
        }

        scorerResult.printMisclassifications();
        binaryResult.printMisclassifications();

        Path basePath = Path.of(exportPath).getParent();
        basePath.toFile().mkdirs();
        String scorerName = "model-scorer-" + provider + ".dnet";
        String binaryName = "model-binary-" + provider + ".dnet";
        FileIO.writeToFile(scorerNet, basePath.resolve(scorerName).toString());
        FileIO.writeToFile(binaryNet, basePath.resolve(binaryName).toString());
        System.out.println("\nModels saved to " + basePath.resolve(scorerName) + " and " + basePath.resolve(binaryName));
    }
}
