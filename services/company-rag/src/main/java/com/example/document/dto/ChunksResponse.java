package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChunksResponse (@JsonProperty("chunks") List<ChunkInfo> chunks) {

    public record ChunkInfo( @JsonProperty("documentName") String documentName,
                             @JsonProperty("chunkIndex")Integer chunkIndex,
                             @JsonProperty("text") String text,
                             @JsonProperty("vector") float[] vector) {

    }
}
