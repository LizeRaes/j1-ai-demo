package com.example.urgency;

import com.example.urgency.data.HelpdeskDemoLoader;
import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.evaluation.Metrics;
import com.example.urgency.model.Ticket;
import com.example.urgency.training.UrgencyTrainer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

import java.util.Collections;
import java.util.List;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class Main {

    private static final String DEFAULT_DATASET = "training/dataset";
    private static final String DEFAULT_EXPORT = "training/export/model.dnet";
    /** Path to helpdesk demo-data (relative to project root or absolute). */
    private static final String DEFAULT_DEMO_DATA = "../helpdesk/src/main/resources/demo-data";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            System.err.println("Error:");
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        String datasetPath = DEFAULT_DATASET;
        String exportPath = DEFAULT_EXPORT;
        boolean useDemoData = false;

        for (int i = 0; i < args.length; i++) {
            if ("--dataset".equals(args[i]) && i + 1 < args.length) {
                datasetPath = args[++i];
            } else if ("--export".equals(args[i]) && i + 1 < args.length) {
                exportPath = args[++i];
            } else if ("--demo-data".equals(args[i])) {
                useDemoData = true;
                datasetPath = (i + 1 < args.length && !args[i + 1].startsWith("-"))
                        ? args[++i] : DEFAULT_DEMO_DATA;
            }
        }

        System.out.println("Urgency Training Pipeline");
        System.out.println("  dataset: " + datasetPath);
        System.out.println("  export:  " + exportPath);

        List<Ticket> tickets = useDemoData
                ? HelpdeskDemoLoader.loadFromDirectory(Path.of(datasetPath))
                : loadTickets(Path.of(datasetPath));
        System.out.println("  loaded " + tickets.size() + " tickets");

        Collections.shuffle(tickets, new java.util.Random(42));
        int splitIdx = (int) (tickets.size() * 0.8);
        var trainTickets = tickets.subList(0, splitIdx);
        var testTickets = tickets.subList(splitIdx, tickets.size());

        try (DJLEmbeddingGenerator embeddingGen = new DJLEmbeddingGenerator()) {
            UrgencyTrainer trainer = new UrgencyTrainer(datasetPath, exportPath);

            var trainSet = trainer.buildDataSet(trainTickets, embeddingGen, "training data");
            var testSet = trainer.buildDataSet(testTickets, embeddingGen, "validation data");

            FeedForwardNetwork net = trainer.train(trainSet);

            System.out.println("\nValidating...");
            var result = Metrics.evaluateWithTickets(net, testTickets, embeddingGen);
            result.printSummary();

            // First 10 validation samples with predicted score (0-10 scale)
            System.out.println("\n  First 10 validation samples (urgency 0-10):");
            for (int i = 0; i < Math.min(10, testTickets.size()); i++) {
                Ticket t = testTickets.get(i);
                float[] emb = embeddingGen.embed(t.text());
                float[] predArr = net.predict(emb);
                float pred01 = predArr.length > 1 ? predArr[1] : predArr[0];
                float pred10 = pred01 * 10;
                double actual10 = t.urgency() > 1 ? t.urgency() : t.urgency() * 10;
                System.out.printf("    [%.1f] (actual %.1f) %s%n", pred10, actual10, t.text());
            }

            // False positives (non-critical said critical) and false negatives (critical missed)
            result.printMisclassifications();

            // Save last, so you see metrics/output even if save fails
            String dnetPath = exportPath.endsWith(".dnet") ? exportPath : Path.of(exportPath).getParent().resolve("model.dnet").toString();
            Path.of(dnetPath).getParent().toFile().mkdirs();
            FileIO.writeToFile(net, dnetPath);
            System.out.println("\nModel saved to " + dnetPath);
        }

        System.out.println("Done.");
    }

    private static List<Ticket> loadTickets(Path path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Ticket> all = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.json")) {
                for (Path f : stream) {
                    List<Ticket> batch = mapper.readValue(f.toFile(), new TypeReference<>() {});
                    all.addAll(batch);
                }
            }
        } else {
            all.addAll(mapper.readValue(path.toFile(), new TypeReference<>() {}));
        }
        return all;
    }

    /** Manual split to avoid DataSets.trainTestSplit NPE (columnNames null). */
    private static TabularDataSet<TabularDataSet.Item> split(
            TabularDataSet<TabularDataSet.Item> data, int from, int to) {
        TabularDataSet<TabularDataSet.Item> out = new TabularDataSet<>(384, 1);
        for (int i = from; i < to; i++) {
            var item = data.get(i);
            out.add(item);
        }
        return out;
    }
}
