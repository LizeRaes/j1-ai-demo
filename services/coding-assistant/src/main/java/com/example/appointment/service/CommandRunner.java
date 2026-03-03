package com.example.appointment.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApplicationScoped
final class CommandRunner {

    record CommandResult(int exitCode, List<String> stdoutLines) {}

    CommandResult run(Path workingDir, List<String> command, Consumer<String> onStdoutLine)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (onStdoutLine != null) {
                    onStdoutLine.accept(line);
                }
            }
        }

        int exit = process.waitFor();
        return new CommandResult(exit, lines);
    }

    List<String> runOrThrow(Path workingDir, List<String> command, String context, Consumer<String> logger)
            throws IOException, InterruptedException {
        CommandResult result = run(workingDir, command, line -> logger.accept("[cmd] " + line));
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    context + " failed with exit code " + result.exitCode() + ". Command: " + String.join(" ", command));
        }
        return result.stdoutLines();
    }
}
