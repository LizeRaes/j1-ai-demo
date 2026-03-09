package com.example.urgency.inference;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;

/**
 * Translator for ONNX urgency model: float[384] -> NDList -> float[1].
 */
public class EmbeddingToUrgencyTranslator implements NoBatchifyTranslator<float[], float[]> {

    @Override
    public NDList processInput(TranslatorContext ctx, float[] embedding) {
        NDArray array = ctx.getNDManager().create(embedding, new Shape(1, 384));
        return new NDList(array);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        return list.get(0).toFloatArray();
    }
}
