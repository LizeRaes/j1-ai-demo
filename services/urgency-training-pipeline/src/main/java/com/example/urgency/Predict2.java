package com.example.urgency;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.evaluation.Metrics;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.NeuralNetwork;
import deepnetts.util.FileIO;
import deepnetts.util.Tensor;

import java.nio.file.Path;

/**
 * Workaround for DeepNetts bug: predict() on deserialized networks throws NPE
 * (inputTensor is null). Uses setInput(Tensor)+forward()+getOutput() instead.
 */
public class Predict2 {

    private static final String DEFAULT_EXPORT_DIR = "training/export";
    private static final String DEFAULT_TEXT = "I cannot book an appointment";

    private static float[] predict(FeedForwardNetwork net, float[] input) {
        Tensor t = new Tensor(input);
        ((NeuralNetwork<?>) net).setInput(t);
        net.forward();
        return net.getOutput();
    }

    public static void main(String[] args) throws Exception {
        String exportDir = DEFAULT_EXPORT_DIR;
        String text = DEFAULT_TEXT;

        for (int i = 0; i < args.length; i++) {
            if ("--model-dir".equals(args[i]) && i + 1 < args.length) {
                exportDir = args[++i];
            } else if (!args[i].startsWith("-")) {
                text = String.join(" ", java.util.Arrays.copyOfRange(args, i, args.length));
                break;
            }
        }

        Path dir = Path.of(exportDir);
        FeedForwardNetwork scorerNet = (FeedForwardNetwork) FileIO.createFromFile(dir.resolve("model-scorer.dnet").toString(), FeedForwardNetwork.class);
        FeedForwardNetwork binaryNet = (FeedForwardNetwork) FileIO.createFromFile(dir.resolve("model-binary.dnet").toString(), FeedForwardNetwork.class);

        try (DJLEmbeddingGenerator emb = new DJLEmbeddingGenerator()) {
            float[] vec = emb.embed(text);
            float score01 = predict(scorerNet, vec)[0];
            float score10 = (float) (Math.round(score01 * 20) / 2.0);
            float pCritical = predict(binaryNet, vec)[0];
            boolean critical = pCritical >= Metrics.CRITICAL_THRESHOLD;
            System.out.printf("Score: %.1f / 10  %s%n", score10, critical ? "[CRITICAL]" : "");
        }
        Runtime.getRuntime().halt(0);
    }
}
