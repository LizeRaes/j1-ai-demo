package com.example.document.tool;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.PictureDescriptionApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

public final class DoclingPreviewCli {

    private static final String DOCLING_URL = "http://localhost:5001";
    private static final String DOCUMENTS_DIR = "company-documents";
    private static final String OUTPUT_FILE = "target/docling-preview.md";

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_MODEL = "gpt-4o-mini";

    private static final String DEFAULT_PROMPT =
            "Describe the image in detail including visible text, charts, tables and layout.";

    private static final String FLAG_KEEP_IMAGE_BLOBS = "--keep-image-blobs";
    private static final String FLAG_REMOVE_IMAGE_BLOBS = "--remove-image-blobs";

    private DoclingPreviewCli() {}

    public static void main(String[] args) throws Exception {

        List<String> positional = Arrays.stream(args)
                .filter(a -> !a.equals(FLAG_KEEP_IMAGE_BLOBS) && !a.equals(FLAG_REMOVE_IMAGE_BLOBS))
                .toList();
        boolean removeImageBlobs = !Arrays.asList(args).contains(FLAG_KEEP_IMAGE_BLOBS);

        if (positional.size() < 2) {
            System.err.println("Usage: DoclingPreviewCli <file> <OPENAI_API_KEY> [--keep-image-blobs]");
            System.exit(1);
        }

        String fileName = positional.get(0);
        String openAiKey = positional.get(1);

        Path sourcePath = Path.of(DOCUMENTS_DIR).resolve(fileName).toAbsolutePath();
        byte[] bytes = Files.readAllBytes(sourcePath);

        String base64Source = Base64.getEncoder().encodeToString(bytes);

        ConvertDocumentRequest request =
                ConvertDocumentRequest.builder()
                        .source(FileSource.builder()
                                .base64String(base64Source)
                                .filename(sourcePath.getFileName().toString())
                                .build())
                        .options(
                                ConvertDocumentOptions.builder()
                                        .doOcr(true)
                                        .doTableStructure(true)
                                        .imageExportMode(removeImageBlobs ? ImageRefMode.PLACEHOLDER : ImageRefMode.EMBEDDED)
                                        .doPictureDescription(true)
                                        .pictureDescriptionApi(buildOpenAiApi(openAiKey))
                                        .build()
                        )
                        .build();

        DoclingServeApi docling =
                DoclingServeApi.builder()
                        .baseUrl(DOCLING_URL)
                        .build();

        ConvertDocumentResponse response;
        try {
            response = docling.convertSource(request);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("enable_remote_services")) {
                throw new IllegalStateException(
                        "Docling rejected remote picture-description calls. " +
                                "Set DOCLING_SERVE_ENABLE_REMOTE_SERVICES=true on the docling-serve container " +
                                "and retry. Original error: " + msg,
                        e
                );
            }
            throw e;
        }

        if (response.getDocument() == null) {
            throw new RuntimeException("Docling returned empty result");
        }

        String markdown = response.getDocument().getMarkdownContent();
        Path outputPath = Path.of(OUTPUT_FILE).toAbsolutePath();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(
                outputPath,
                markdown,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        System.out.println("----- BEGIN DOCLING MARKDOWN -----");
        System.out.println(markdown);
        System.out.println("----- END DOCLING MARKDOWN -----");
        System.out.println("Written to: " + outputPath);
    }

    private static PictureDescriptionApi buildOpenAiApi(String apiKey) {

        Map<String, Object> params = new HashMap<>();
        params.put("model", OPENAI_MODEL);

        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);

        return PictureDescriptionApi.builder()
                .url(URI.create(OPENAI_API_URL))
                .params(params)
                .headers(headers)
                .prompt(DEFAULT_PROMPT)
                .timeout(java.time.Duration.ofSeconds(120))
                .build();
    }
}