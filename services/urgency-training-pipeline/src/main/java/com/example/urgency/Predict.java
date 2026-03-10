package com.example.urgency;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.embedding.EmbeddingGenerator;
import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
import com.example.urgency.evaluation.Metrics;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

import java.nio.file.Path;

public class Predict {

    private static final String DEFAULT_EXPORT_DIR = "training/export";
    private static final String DEFAULT_TEXT = "I cannot book an appointment";

    public static void main(String[] args) throws Exception {
        String exportDir = DEFAULT_EXPORT_DIR;
        String text = DEFAULT_TEXT;
        String embeddingProvider = "local";

        for (int i = 0; i < args.length; i++) {
            if ("--model-dir".equals(args[i]) && i + 1 < args.length) {
                exportDir = args[++i];
            } else if ("--embedding-provider".equals(args[i]) && i + 1 < args.length) {
                embeddingProvider = "openai".equals(args[++i].toLowerCase()) ? "openai" : "local";
            } else if (!args[i].startsWith("-")) {
                text = String.join(" ", java.util.Arrays.copyOfRange(args, i, args.length));
                break;
            }
        }

        Path dir = Path.of(exportDir);
        FeedForwardNetwork scorerNet = (FeedForwardNetwork) FileIO.createFromFile(
                dir.resolve("model-scorer-" + embeddingProvider + ".dnet").toString(),
                FeedForwardNetwork.class
        );
        FeedForwardNetwork binaryNet = (FeedForwardNetwork) FileIO.createFromFile(
                dir.resolve("model-binary-" + embeddingProvider + ".dnet").toString(),
                FeedForwardNetwork.class
        );

        EmbeddingGenerator emb = "openai".equals(embeddingProvider)
                ? new OpenAIEmbeddingGenerator()
                : new DJLEmbeddingGenerator();
        try {
            float[] vec = emb.embed(text);
            float score01 = scorerNet.predict(vec)[0];
            float score10 = (float) (Math.round(score01 * 20) / 2.0);
            float pCritical = binaryNet.predict(vec)[0];
            boolean critical = pCritical >= Metrics.CRITICAL_THRESHOLD;
            System.out.printf("Score: %.1f / 10  %s%n", score10, critical ? "[CRITICAL]" : "");
        } finally {
            if (emb instanceof AutoCloseable ac) {
                ac.close();
            }
        }
        Runtime.getRuntime().halt(0);  // exit without shutdown hooks to avoid Log4j async error
    }

}
