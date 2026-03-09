package com.example.urgency.training;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.model.Ticket;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.BackpropagationTrainer;
import deepnetts.net.train.TrainingEvent;
import deepnetts.net.train.TrainingListener;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Trains urgency classifier: embeddings (384) → MLP (64→16→2) → softmax.
 * Architecture: Input 384, Hidden 64 (ReLU), Hidden 16 (ReLU), Output 2 (Softmax).
 * Output [1-u, u] for non-critical/critical; use output[1] as urgency score (0-1, scale to 0-10 for display).
 */
public class UrgencyTrainer {

    private static final int INPUT_DIM = 384;
    private static final int HIDDEN_1 = 64;
    private static final int HIDDEN_2 = 16;
    private static final int OUTPUT_DIM = 2;
    private static final int PROGRESS_STEPS = 10;

    private final String datasetPath;
    private final String exportPath;
    private final PrintStream out;

    public UrgencyTrainer(String datasetPath, String exportPath) {
        this(datasetPath, exportPath, System.out);
    }

    public UrgencyTrainer(String datasetPath, String exportPath, PrintStream out) {
        this.datasetPath = datasetPath;
        this.exportPath = exportPath;
        this.out = out;
    }

    /** Normalize urgency to 0-1 (handles both 0-1 and 0-10 input). */
    private static float normalizeUrgency(double u) {
        return (float) (u > 1.0 ? u / 10.0 : u);
    }

    /**
     * Build TabularDataSet from tickets: embeddings as input, [1-u, u] as output (binary classification).
     * phase: "Training data" or "Validation data" - printed as header, then embedding 1/10, 2/10, ...
     */
    public TabularDataSet<TabularDataSet.Item> buildDataSet(List<Ticket> tickets, DJLEmbeddingGenerator embeddingGen, String phase) {
        TabularDataSet<TabularDataSet.Item> dataSet = new TabularDataSet<>(INPUT_DIM, OUTPUT_DIM);
        int n = tickets.size();
        int lastPrinted = -1;

        if (phase != null && !phase.isBlank()) {
            out.println();
            out.println("──────────────── " + phase + " ────────────────");
        }

        for (int i = 0; i < n; i++) {
            Ticket t = tickets.get(i);
            float[] input = embeddingGen.embed(t.text());
            float u = normalizeUrgency(t.urgency());
            float[] output = new float[] { 1 - u, u };  // [non-critical, critical]
            dataSet.add(new TabularDataSet.Item(input, output));

            int pct = n > 0 ? (int) Math.ceil((i + 1) * (double) PROGRESS_STEPS / n) : PROGRESS_STEPS;
            if (pct > lastPrinted && pct >= 1 && pct <= PROGRESS_STEPS) {
                lastPrinted = pct;
                out.println("embedding " + pct + "/" + PROGRESS_STEPS);
            }
        }
        if (lastPrinted < PROGRESS_STEPS) out.println("embedding " + PROGRESS_STEPS + "/" + PROGRESS_STEPS);

        dataSet.shuffle();
        return dataSet;
    }

    /**
     * Create MLP: 384 → 64 (ReLU) → 16 (ReLU) → 2 (Softmax).
     */
    public FeedForwardNetwork createNetwork() {
        return FeedForwardNetwork.builder()
                .addInputLayer(INPUT_DIM)
                .addFullyConnectedLayer(HIDDEN_1, ActivationType.RELU)
                .addFullyConnectedLayer(HIDDEN_2, ActivationType.RELU)
                .addOutputLayer(OUTPUT_DIM, ActivationType.SOFTMAX)
                .lossFunction(LossType.CROSS_ENTROPY)  // Binary classification (critical vs non-critical)
                .randomSeed(42)
                .build();
    }

    /**
     * Train model. Returns trained network for evaluation.
     * Caller is responsible for saving (after metrics/output).
     */
    public FeedForwardNetwork train(TabularDataSet<TabularDataSet.Item> dataSet) throws IOException {
        FeedForwardNetwork net = createNetwork();
        BackpropagationTrainer trainer = (BackpropagationTrainer) net.getTrainer();

        trainer.setMaxError(0.01f);
        trainer.setMaxEpochs(500);
        trainer.setLearningRate(0.01f);

        long maxEpochs = trainer.getMaxEpochs();
        trainer.addListener(new TrainingListener() {
            int lastPct = -1;

            @Override
            public void handleEvent(TrainingEvent e) {
                if (e.getType() == TrainingEvent.Type.EPOCH_FINISHED) {
                    int epoch = trainer.getCurrentEpoch();
                    int pct = maxEpochs > 0 ? (int) Math.ceil(epoch * (double) PROGRESS_STEPS / maxEpochs) : PROGRESS_STEPS;
                    if (pct > lastPct && pct >= 1 && pct <= PROGRESS_STEPS) {
                        lastPct = pct;
                        StringBuilder bar = new StringBuilder("[");
                        for (int i = 0; i < PROGRESS_STEPS; i++) bar.append(i < pct ? "=" : " ");
                        bar.append("] ");
                        out.println("  TRAINING " + bar + pct + "/" + PROGRESS_STEPS + " (epoch " + epoch + "/" + maxEpochs + ")");
                    }
                }
            }
        });

        out.println();
        out.println("Training");
        out.println("────────────────────────────────────────");
        out.println("  Model        384 → 64 (ReLU) → 16 (ReLU) → 2 (Softmax)");
        out.println("  Samples      " + dataSet.size());
        out.println("  Max epochs   " + maxEpochs);
        out.println("  (Multiple passes over all samples)");
        out.println("────────────────────────────────────────");
        trainer.train(dataSet);
        out.println("  TRAINING " + PROGRESS_STEPS + "/" + PROGRESS_STEPS + " done");

        return net;
    }
}
