package com.example.urgency;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

public class Predict {

    public static void main(String[] args) throws Exception {
        String modelPath = "training/export/model.dnet";
        String text = "I cannot book an appointment";

        FeedForwardNetwork net = (FeedForwardNetwork) FileIO.createFromFile(modelPath, FeedForwardNetwork.class);
        try (DJLEmbeddingGenerator emb = new DJLEmbeddingGenerator()) {
            float[] out = net.predict(emb.embed(text));
            float u = out.length > 1 ? out[1] : out[0];
            System.out.println("Urgency: " + (u * 10) + " / 10");
        }
    }
}
