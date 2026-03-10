package com.example.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
public class DocumentChunkingService {

    @ConfigProperty(name = "document.chunking.default.chunk-size")
    int defaultChunkSize;

    /**
     * Chunks a document based on the specified strategy.
     *
     * @param document     The document to chunk
     * @param documentName The name of the document (used to determine strategy)
     * @param strategy     The chunking strategy to use
     * @return List of text segments
     */
    public List<TextSegment> chunkDocument(Document document, String documentName, String strategy) {
        String text = document.text();

        return switch (strategy) {
            case "approved-response-splitter" -> chunkApprovedResponses(text, documentName);
            case String _ -> chunkRecursive(text, documentName, defaultChunkSize);
        };
    }

    /**
     * Chunks Approved Response Templates by splitting on "Template: " markers.
     * Each template is kept as a complete chunk.
     */
    private List<TextSegment> chunkApprovedResponses(String text, String documentName) {
        List<TextSegment> segments = new ArrayList<>();

        Pattern templatePattern = Pattern.compile("(?=Template: )");
        String[] parts = templatePattern.split(text);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }

            if (i > 0 && !part.startsWith("Template: ")) {
                part = "Template: " + part;
            }

            Metadata metadata = Metadata.from(Map.of("documentName", documentName, "chunkIndex", i));

            segments.add(TextSegment.from(part, metadata));
        }

        return segments;
    }

    /**
     * Chunks text using recursive splitting strategy (splits by paragraphs, then sentences, then words).
     */
    private List<TextSegment> chunkRecursive(String text, String documentName, int chunkSize) {
        List<String> packedChunks = packParagraphsIntoChunks(text, chunkSize);

        List<TextSegment> segmentsWithMetadata = new ArrayList<>();
        for (int i = 0; i < packedChunks.size(); i++) {
            Metadata metadata = Metadata.from(Map.of("documentName", documentName, "chunkIndex", i));
            segmentsWithMetadata.add(TextSegment.from(packedChunks.get(i), metadata));
        }

        return segmentsWithMetadata;
    }

    private List<String> packParagraphsIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\R\\s*\\R+");
        StringBuilder current = new StringBuilder();

        for (String rawParagraph : paragraphs) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }

            if (paragraph.length() > chunkSize) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.addAll(splitOversizedBlock(paragraph, chunkSize));
                continue;
            }

            if (current.isEmpty()) {
                current.append(paragraph);
                continue;
            }

            String candidate = current + "\n\n" + paragraph;
            if (candidate.length() <= chunkSize) {
                current.append("\n\n").append(paragraph);
            } else {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(paragraph);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private List<String> splitOversizedBlock(String text, int chunkSize) {
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, 0);
        List<TextSegment> segments = splitter.split(Document.from(text));
        List<String> out = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            out.add(segment.text());
        }
        return out;
    }
}
