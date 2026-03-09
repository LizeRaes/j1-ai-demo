package com.example.urgency.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.AbstractLayer;
import deepnetts.net.layers.FullyConnectedLayer;
import deepnetts.net.layers.InputLayer;
import deepnetts.net.layers.OutputLayer;
import deepnetts.util.Tensor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports DeepNetts FeedForwardNetwork weights to JSON for ONNX conversion.
 * Skips InputLayer; exports weights and biases from FC and Output layers.
 */
public final class WeightsExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WeightsExporter() {}

    /**
     * Export network weights to JSON. Format: { "layers": [ { "weights": [...], "biases": [...] }, ... ] }
     * Weights are stored as row-major 2D (rows, cols). DeepNetts uses (out, in); we export as-is for Python to transpose if needed.
     */
    public static void export(FeedForwardNetwork net, Path outputPath) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode layers = root.putArray("layers");

        List<AbstractLayer> layerList = net.getLayers();
        for (AbstractLayer layer : layerList) {
            if (layer instanceof InputLayer) continue;

            ObjectNode layerNode = MAPPER.createObjectNode();
            Tensor w = layer.getWeights();
            float[] biases = layer.getBiases();

            if (w != null) {
                float[] vals = w.getValues();
                int rows = w.getRows();
                int cols = w.getCols();
                ArrayNode weightsArr = layerNode.putArray("weights");
                for (int r = 0; r < rows; r++) {
                    ArrayNode row = weightsArr.addArray();
                    for (int c = 0; c < cols; c++) {
                        row.add(vals[r * cols + c]);
                    }
                }
            }
            if (biases != null) {
                ArrayNode biasArr = layerNode.putArray("biases");
                for (float b : biases) biasArr.add(b);
            }
            layers.add(layerNode);
        }

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), root);
    }
}
