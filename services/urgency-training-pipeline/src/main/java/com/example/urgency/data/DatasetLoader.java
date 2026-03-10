package com.example.urgency.data;

import com.example.urgency.model.Ticket;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class DatasetLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DatasetLoader() {}

    public static LoadResult load(Path path) throws Exception {
        List<Path> files = new ArrayList<>();
        List<Ticket> all = new ArrayList<>();

        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.json")) {
                for (Path f : stream) {
                    files.add(f);
                }
            }
            files = files.stream().sorted().collect(Collectors.toList());
            for (Path f : files) {
                all.addAll(MAPPER.readValue(f.toFile(), new TypeReference<>() {}));
            }
        } else {
            files.add(path);
            all.addAll(MAPPER.readValue(path.toFile(), new TypeReference<>() {}));
        }

        return new LoadResult(all, files);
    }

    public record LoadResult(List<Ticket> tickets, List<Path> sourceFiles) {}
}
