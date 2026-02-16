package com.example.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class DocumentChunkingService {

    @ConfigProperty(name = "document.chunking.default.chunk-size", defaultValue = "300")
    int defaultChunkSize;

    /**
     * Chunks a document based on the specified strategy.
     *
     * @param document The document to chunk
     * @param documentName The name of the document (used to determine strategy)
     * @param strategy The chunking strategy to use
     * @return List of text segments
     */
    public List<TextSegment> chunkDocument(Document document, String documentName, String strategy) {
        String text = document.text();

        switch (strategy) {
            case "approved-response-splitter":
                return chunkApprovedResponses(text, documentName);
            case "recursive":
            default:
                return chunkRecursive(text, documentName, defaultChunkSize);
        }
    }

    /**
     * Chunks Approved Response Templates by splitting on "Template: " markers.
     * Each template is kept as a complete chunk.
     */
    private List<TextSegment> chunkApprovedResponses(String text, String documentName) {
        List<TextSegment> segments = new ArrayList<>();

        // Split on "Template: " pattern
        Pattern templatePattern = Pattern.compile("(?=Template: )");
        String[] parts = templatePattern.split(text);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }

            // Add "Template: " prefix back if it's not the first part
            if (i > 0 && !part.startsWith("Template: ")) {
                part = "Template: " + part;
            }

            // Create metadata with document name
            dev.langchain4j.data.document.Metadata metadata =
                dev.langchain4j.data.document.Metadata.from(
                    java.util.Map.of("documentName", documentName, "chunkIndex", i)
                );

            segments.add(TextSegment.from(part, metadata));
        }

        return segments;
    }

    /**
     * Chunks text using recursive splitting strategy (splits by paragraphs, then sentences, then words).
     */
    private List<TextSegment> chunkRecursive(String text, String documentName, int chunkSize) {
        Document doc = Document.from(text);
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, 0);

        List<TextSegment> segments = splitter.split(doc);

        // Add document name metadata to each segment
        List<TextSegment> segmentsWithMetadata = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment original = segments.get(i);
            dev.langchain4j.data.document.Metadata metadata =
                dev.langchain4j.data.document.Metadata.from(
                    java.util.Map.of("documentName", documentName, "chunkIndex", i)
                );
            segmentsWithMetadata.add(TextSegment.from(original.text(), metadata));
        }

        return segmentsWithMetadata;
    }
}
