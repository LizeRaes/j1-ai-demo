package com.example.document.ai;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.EmbedTextDetails;
import com.oracle.bmc.generativeaiinference.model.EmbedTextResult;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.generativeaiinference.requests.EmbedTextRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OciGenAiEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private final GenerativeAiInferenceClient client;
    private final String compartmentId;
    private final ServingMode servingMode;
    private final EmbedTextDetails.InputType inputType;
    private final String modelName;

    public OciGenAiEmbeddingModel(
            GenerativeAiInferenceClient client,
            String compartmentId,
            ServingMode servingMode,
            EmbedTextDetails.InputType inputType,
            String modelName) {
        this.client = Objects.requireNonNull(client, "client");
        this.compartmentId = Objects.requireNonNull(compartmentId, "compartmentId");
        this.servingMode = Objects.requireNonNull(servingMode, "servingMode");
        this.inputType = inputType;
        this.modelName = modelName;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        Objects.requireNonNull(textSegments, "textSegments");
        if (textSegments.isEmpty()) {
            return Response.from(List.of());
        }

        List<String> inputs = textSegments.stream()
                .map(TextSegment::text)
                .toList();

        EmbedTextDetails.Builder detailsBuilder = EmbedTextDetails.builder()
                .inputs(inputs)
                .servingMode(servingMode)
                .compartmentId(compartmentId)
                .isEcho(false)
                .truncate(EmbedTextDetails.Truncate.End);
        if (inputType != null) {
            detailsBuilder.inputType(inputType);
        }

        EmbedTextResult result = client.embedText(EmbedTextRequest.builder()
                        .embedTextDetails(detailsBuilder.build())
                        .build())
                .getEmbedTextResult();

        if (result == null || result.getEmbeddings() == null) {
            throw new IllegalStateException("OCI GenAI returned no embeddings.");
        }

        List<List<Float>> vectors = result.getEmbeddings();
        if (vectors.size() != textSegments.size()) {
            throw new IllegalStateException("OCI GenAI returned " + vectors.size()
                    + " embeddings for " + textSegments.size() + " inputs.");
        }

        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (List<Float> vector : vectors) {
            embeddings.add(Embedding.from(vector));
        }
        return Response.from(embeddings);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public void close() {
        client.close();
    }
}
