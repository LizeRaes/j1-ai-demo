package com.example.urgency.training;

import com.example.urgency.embedding.CachedEmbedding;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.BinaryCrossEntropyLoss;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.BackpropagationTrainer;
import deepnetts.net.train.TrainingEvent;
import deepnetts.net.train.TrainingListener;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Trains two separate models with different goals:
 * 1. Scorer (regression): outputs urgency 0-1 (scale to 0-10), MSE loss, goal low MAE/MSE.
 * 2. Binary classifier: outputs P(critical), BCE loss, goal high recall/precision.
 *    Target: critical = 1 if round(urgency, 0.5) >= 8 else 0.
 */
public class UrgencyTrainer {

    private static final int HIDDEN_1 = 64;
    private static final int HIDDEN_2 = 32;
    private static final int PROGRESS_STEPS = 10;

    private static final float CRITICAL_THRESHOLD = 0.799f;

    private final int embeddingDim;
    private final PrintStream out;

    public UrgencyTrainer(String provider) {
        this.embeddingDim = "openai".equals(provider) ? 1536 : 384;
        this.out = System.out;
    }

    public TabularDataSet<TabularDataSet.Item> buildScorerDataSetFromCache(
        List<CachedEmbedding> cached, String phase) {
        return buildDataSetFromCache(cached, phase, true);
    }

    public TabularDataSet<TabularDataSet.Item> buildBinaryDataSetFromCache(
        List<CachedEmbedding> cached, String phase) {
        return buildDataSetFromCache(cached, phase, false);
    }

    private TabularDataSet<TabularDataSet.Item> buildDataSetFromCache(
        List<CachedEmbedding> cached, String phase, boolean scorer) {
        int dim = cached.isEmpty() ? embeddingDim : cached.get(0).embedding().length;
        TabularDataSet<TabularDataSet.Item> dataSet = new TabularDataSet<>(dim, 1);
        String[] colNames = new String[dim + 1];
        for (int i = 0; i < dim; i++) colNames[i] = "Col_" + i;
        colNames[dim] = "Target";
        dataSet.setColumnNames(colNames);
        dataSet.setAsTargetColumns(new String[]{"Target"});

        if (phase != null && !phase.isBlank()) {
            out.println();
            out.println("──────────────── " + phase + " ────────────────");
        }

        int excluded = 0;
        for (CachedEmbedding c : cached) {
            float u = c.urgency01();
            if (!scorer) {
                if (u == 0.7f || u == 0.75f) { excluded++; continue; }
            }
            float target = scorer ? u : (u >= CRITICAL_THRESHOLD ? 1f : 0f);
            dataSet.add(new TabularDataSet.Item(c.embedding(), new float[]{target}));
        }
        if (excluded > 0) {
            out.println("loaded " + dataSet.size() + " cached embeddings (excluded " + excluded + " gray-zone 7.0/7.5 from binary training)");
        } else {
            out.println("loaded " + cached.size() + " cached embeddings");
        }
        dataSet.shuffle();
        return dataSet;
    }

    /** Scorer: inputDim → 64 → 32 → 1 (Sigmoid), MSE. */
    public FeedForwardNetwork createScorerNetwork(int inputDim) {
        return FeedForwardNetwork.builder()
                .addInputLayer(inputDim)
                .addFullyConnectedLayer(HIDDEN_1, ActivationType.TANH)
                .addFullyConnectedLayer(HIDDEN_2, ActivationType.TANH)
                .addOutputLayer(1, ActivationType.SIGMOID)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(42)
                .build();
    }

    /** Binary classifier: inputDim → 64 → 32 → 1 (Sigmoid), BCE. */
    public FeedForwardNetwork createBinaryNetwork(int inputDim) {
        return FeedForwardNetwork.builder()
                .addInputLayer(inputDim)
                .addFullyConnectedLayer(HIDDEN_1, ActivationType.TANH)
                .addFullyConnectedLayer(HIDDEN_2, ActivationType.TANH)
                .addOutputLayer(1, ActivationType.SIGMOID)
                .lossFunction(LossType.of(BinaryCrossEntropyLoss.class))
                .randomSeed(43)
                .build();
    }

    private FeedForwardNetwork train(
        FeedForwardNetwork net, TabularDataSet<TabularDataSet.Item> dataSet,
        String modelName, float maxError, int maxEpochs) throws IOException {
        BackpropagationTrainer trainer = (BackpropagationTrainer) net.getTrainer();
        trainer.setMaxError(maxError);
        trainer.setMaxEpochs(maxEpochs);
        trainer.setLearningRate(0.1f);

        long maxEpochsVal = trainer.getMaxEpochs();
        trainer.addListener(new TrainingListener() {
            int lastPct = -1;

            @Override
            public void handleEvent(TrainingEvent e) {
                if (e.getType() == TrainingEvent.Type.EPOCH_FINISHED) {
                    int epoch = trainer.getCurrentEpoch();
                    int pct = maxEpochsVal > 0 ? (int) Math.ceil(epoch * (double) PROGRESS_STEPS / maxEpochsVal) : PROGRESS_STEPS;
                    pct = Math.min(pct, PROGRESS_STEPS);
                    if (pct > lastPct && pct >= 1 && pct <= PROGRESS_STEPS) {
                        lastPct = pct;
                        StringBuilder bar = new StringBuilder("[");
                        for (int i = 0; i < PROGRESS_STEPS; i++) bar.append(i < pct ? "=" : " ");
                        bar.append("] ");
                        out.println("  " + modelName + " " + bar + pct + "/" + PROGRESS_STEPS + " (epoch " + epoch + "/" + maxEpochsVal + ")");
                    }
                }
            }
        });

        out.println();
        out.println("Training " + modelName);
        out.println("────────────────────────────────────────");
        out.println("  Samples:    " + dataSet.size());
        out.println("  Max epochs: " + maxEpochsVal);
        out.println("────────────────────────────────────────");
        trainer.train(dataSet);
        out.println("  " + modelName + " " + PROGRESS_STEPS + "/" + PROGRESS_STEPS + " done");
        return net;
    }

    public FeedForwardNetwork trainScorer(TabularDataSet<TabularDataSet.Item> dataSet) throws IOException {
        int dim = dataSet.isEmpty() ? embeddingDim : dataSet.get(0).getInput().getValues().length;
        return train(createScorerNetwork(dim), dataSet, "SCORER", 0.002f, 500);
    }

    public FeedForwardNetwork trainBinary(TabularDataSet<TabularDataSet.Item> dataSet) throws IOException {
        int dim = dataSet.isEmpty() ? embeddingDim : dataSet.get(0).getInput().getValues().length;
        return train(createBinaryNetwork(dim), dataSet, "BINARY", 0.0001f, 1000);
    }
}
