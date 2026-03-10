package com.example.urgency.training;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.evaluation.Metrics;
import com.example.urgency.model.Ticket;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.BackpropagationTrainer;
import deepnetts.net.train.TrainingEvent;
import deepnetts.net.train.TrainingListener;

import java.util.List;

public class UrgencyTrainer {

    private static final int INPUT_DIM = 384;

    private static final int HIDDEN_1 = 64;
    private static final int HIDDEN_2 = 16;

    private static final int OUTPUT_DIM = 1;
    private static final int MAX_EPOCHS = 500;

    private static float normalizeUrgency(double u) {
        return (float) (u / 10.0);
    }

    public TabularDataSet<TabularDataSet.Item> buildDataSet(
            List<Ticket> tickets,
            DJLEmbeddingGenerator embeddingGen) {

        TabularDataSet<TabularDataSet.Item> ds =
                new TabularDataSet<>(INPUT_DIM, OUTPUT_DIM);

        for (Ticket t : tickets) {
            float[] input = embeddingGen.embed(t.text());
            float target = normalizeUrgency(t.urgency());
            ds.add(new TabularDataSet.Item(input, new float[]{target}));
        }

        ds.shuffle();

        return ds;
    }

    public FeedForwardNetwork createNetwork() {

        return FeedForwardNetwork.builder()

                .addInputLayer(INPUT_DIM)

                .addFullyConnectedLayer(HIDDEN_1, ActivationType.RELU)

                .addFullyConnectedLayer(HIDDEN_2, ActivationType.RELU)

                .addOutputLayer(OUTPUT_DIM, ActivationType.SIGMOID)  // 0–1 regression

                .lossFunction(LossType.MEAN_SQUARED_ERROR)

                .randomSeed(42)

                .build();
    }

    public FeedForwardNetwork train(
            TabularDataSet<TabularDataSet.Item> trainSet,
            TabularDataSet<TabularDataSet.Item> validationSet) {

        FeedForwardNetwork net = createNetwork();
        BackpropagationTrainer trainer = (BackpropagationTrainer) net.getTrainer();

        trainer.setLearningRate(0.01f);
        trainer.setMaxEpochs(MAX_EPOCHS);

        int printEvery = Math.max(1, MAX_EPOCHS / 10);
        double[] prevMse = {-1};

        trainer.addListener((TrainingListener) event -> {
            if (event.getType() != TrainingEvent.Type.EPOCH_FINISHED) return;

            int epoch = trainer.getCurrentEpoch();
            var result = Metrics.evaluateFromDataSet(net, validationSet);

            if (epoch % printEvery == 0 || epoch == MAX_EPOCHS) {
                System.out.printf("  Epoch %4d  MSE %.4f  MAE %.4f  critRecall %.3f  fpRate %.3f%n",
                        epoch, result.mse(), result.mae(),
                        result.criticalRecall(), result.fpRate());
            }

            if (result.shouldStopTraining()) {
                System.out.println("  Stopping: criteria met (MSE<" + Metrics.STOP_MSE_THRESHOLD
                        + ", critRecall>=" + Metrics.STOP_CRITICAL_RECALL
                        + ", fpRate<" + Metrics.STOP_FP_RATE_MAX + ")");
                trainer.stop();
            } else if (prevMse[0] >= 0 && result.improvementTooSmall(prevMse[0])) {
                System.out.println("  Stopping: MSE improvement < " + Metrics.STOP_MIN_IMPROVEMENT_MSE);
                trainer.stop();
            }
            prevMse[0] = result.mse();
        });

        trainer.train(trainSet);
        return net;
    }
}