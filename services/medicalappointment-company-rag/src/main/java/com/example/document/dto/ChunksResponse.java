package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ChunksResponse {
    @JsonProperty("chunks")
    private List<ChunkInfo> chunks;

    public ChunksResponse() {
    }

    public ChunksResponse(List<ChunkInfo> chunks) {
        this.chunks = chunks;
    }

    public List<ChunkInfo> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkInfo> chunks) {
        this.chunks = chunks;
    }

    public static class ChunkInfo {
        @JsonProperty("documentName")
        private String documentName;

        @JsonProperty("chunkIndex")
        private Integer chunkIndex;

        @JsonProperty("text")
        private String text;

        @JsonProperty("vector")
        private float[] vector;

        public ChunkInfo() {
        }

        public ChunkInfo(String documentName, Integer chunkIndex, String text, float[] vector) {
            this.documentName = documentName;
            this.chunkIndex = chunkIndex;
            this.text = text;
            this.vector = vector;
        }

        public String getDocumentName() {
            return documentName;
        }

        public void setDocumentName(String documentName) {
            this.documentName = documentName;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public float[] getVector() {
            return vector;
        }

        public void setVector(float[] vector) {
            this.vector = vector;
        }
    }
}
