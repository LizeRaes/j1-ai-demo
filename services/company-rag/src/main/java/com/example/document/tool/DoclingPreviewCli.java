package com.example.document.tool;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone CLI to preview Docling markdown output for a file in company-documents.
 */
public final class DoclingPreviewCli {

    private static final String DEFAULT_DOCLING_BASE_URL = "http://localhost:8086";
    private static final String DEFAULT_DOCUMENTS_DIR = "company-documents";
    private static final Pattern INLINE_IMAGE_DATA_URI_PATTERN = Pattern.compile(
            "!\\[[^\\]]*]\\(data:image/[^;\\)]+;base64,[^\\)]*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private DoclingPreviewCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String fileName = args[0];
        boolean keepImageBlobs = false;
        Integer maxChars = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--keep-image-blobs".equals(arg)) {
                keepImageBlobs = true;
                continue;
            }
            if (arg.startsWith("--max-chars=")) {
                String value = arg.substring("--max-chars=".length()).trim();
                try {
                    maxChars = Integer.parseInt(value);
                    if (maxChars < 1) {
                        throw new NumberFormatException("must be >= 1");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid --max-chars value: " + value, e);
                }
                continue;
            }
            throw new IllegalArgumentException("Unknown argument: " + arg);
        }

        String baseUrl = System.getProperty("docling.base-url", DEFAULT_DOCLING_BASE_URL);
        Path documentsDir = Path.of(System.getProperty("documents.dir", DEFAULT_DOCUMENTS_DIR)).toAbsolutePath().normalize();

        Path sourcePath = documentsDir.resolve(fileName).normalize();
        if (!sourcePath.startsWith(documentsDir)) {
            throw new IllegalArgumentException("Invalid file path (path traversal): " + fileName);
        }
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("File does not exist: " + sourcePath);
        }

        byte[] bytes = Files.readAllBytes(sourcePath);
        String base64Source = Base64.getEncoder().encodeToString(bytes);

        ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                .source(FileSource.builder()
                        .base64String(base64Source)
                        .filename(sourcePath.getFileName().toString())
                        .build())
                .build();

        DoclingServeApi docling = DoclingServeApi.builder()
                .baseUrl(baseUrl)
                .build();

        ConvertDocumentResponse response = docling.convertSource(request);
        if (response.getDocument() == null || response.getDocument().getMarkdownContent() == null) {
            throw new IllegalStateException("Docling returned empty markdown for " + sourcePath.getFileName());
        }

        String markdown = response.getDocument().getMarkdownContent();
        SanitizedMarkdown sanitized = keepImageBlobs
                ? new SanitizedMarkdown(markdown, 0)
                : stripInlineImageDataBlobs(markdown);

        String output = sanitized.content();
        if (maxChars != null && output.length() > maxChars) {
            output = output.substring(0, maxChars) + "\n\n... (truncated)";
        }

        System.out.println("Docling base URL: " + baseUrl);
        System.out.println("File: " + sourcePath);
        System.out.println("Original markdown length: " + markdown.length());
        System.out.println("Inline image blobs removed: " + sanitized.removedBlobs());
        System.out.println("Output length: " + sanitized.content().length());
        System.out.println();
        System.out.println("----- BEGIN DOCLING MARKDOWN -----");
        System.out.println(output);
        System.out.println("----- END DOCLING MARKDOWN -----");
    }

    private static SanitizedMarkdown stripInlineImageDataBlobs(String markdown) {
        Matcher matcher = INLINE_IMAGE_DATA_URI_PATTERN.matcher(markdown);
        StringBuffer sanitized = new StringBuffer();
        int removed = 0;
        while (matcher.find()) {
            removed++;
            matcher.appendReplacement(sanitized, "");
        }
        matcher.appendTail(sanitized);
        return new SanitizedMarkdown(sanitized.toString(), removed);
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  DoclingPreviewCli <filename> [--max-chars=<N>] [--keep-image-blobs]");
        System.err.println();
        System.err.println("System properties:");
        System.err.println("  -Ddocling.base-url=http://localhost:8086");
        System.err.println("  -Ddocuments.dir=company-documents");
    }

    private record SanitizedMarkdown(String content, int removedBlobs) {
    }
}
