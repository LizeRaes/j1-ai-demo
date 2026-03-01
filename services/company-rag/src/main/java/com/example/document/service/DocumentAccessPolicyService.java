package com.example.document.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;

@ApplicationScoped
public class DocumentAccessPolicyService {

    private static final Logger LOGGER = Logger.getLogger(DocumentAccessPolicyService.class.getName());

    private Map<String, List<String>> accessPolicy = new HashMap<>();

    @ConfigProperty(name = "demo.config.access.location")
    String accessConfigLocation;

    @PostConstruct
    void init() {
        try {
            List<Path> configLocation = scan(accessConfigLocation);
            if (configLocation.isEmpty()) {
                LOGGER.warning("No access policy config file found at: " + accessConfigLocation);
                accessPolicy = new HashMap<>();
                return;
            }

            Path configPath = configLocation.getFirst();
            try (InputStream configStream = Files.newInputStream(configPath)) {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(configStream);
                accessPolicy = parseAccessPolicy(loaded);
            }

            LOGGER.info("Loaded " + accessPolicy.size() + " document access policies from " + configPath);
            accessPolicy.forEach((doc, teams) ->
                    LOGGER.info("Access policy loaded: " + doc + " -> " + (teams.isEmpty() ? "[company-wide]" : teams))
            );
        } catch (Exception e) {
            accessPolicy = new HashMap<>();
            LOGGER.log(Level.SEVERE, "Error loading document access policy: ", e);
        }
    }

    /**
     * Gets the list of teams that have read access to a document.
     * Returns empty list if document not found or no teams specified.
     */
    public List<String> getAccessTeams(String documentName) {
        return accessPolicy.getOrDefault(documentName, new ArrayList<>());
    }

    /**
     * Updates the access policy for a document.
     */
    public void updateDocumentAccess(String documentName, List<String> teams) {
        if (teams == null || teams.isEmpty()) {
            // Company-wide access (empty list means all teams)
            accessPolicy.put(documentName, new ArrayList<>());
        } else {
            accessPolicy.put(documentName, new ArrayList<>(teams));
        }
        // TODO: Persist to YAML file
    }

    /**
     * Removes a document from the access policy.
     */
    public void removeDocument(String documentName) {
        accessPolicy.remove(documentName);
    }

    /**
     * Gets all documents and their access teams.
     */
    public Map<String, List<String>> getAllAccessPolicies() {
        return new HashMap<>(accessPolicy);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseAccessPolicy(Object loaded) {
        if (!(loaded instanceof Map<?, ?> rootMap)) {
            LOGGER.warning("Access policy YAML is not a map. Loaded type: " + (loaded == null ? "null" : loaded.getClass().getName()));
            return new HashMap<>();
        }

        Map<String, List<String>> parsed = new HashMap<>();
        for (Map.Entry<?, ?> entry : rootMap.entrySet()) {
            if (!(entry.getKey() instanceof String docNameRaw)) {
                continue;
            }
            String documentName = docNameRaw.trim();
            if (documentName.isBlank()) {
                continue;
            }

            List<String> teams = extractTeams(entry.getValue());
            parsed.put(documentName, teams);
        }

        return parsed;
    }

    private List<String> extractTeams(Object policyNode) {
        Object teamsNode = policyNode;
        if (policyNode instanceof Map<?, ?> policyMap) {
            teamsNode = policyMap.get("read");
        }

        if (!(teamsNode instanceof List<?> teamList)) {
            return new ArrayList<>();
        }

        return teamList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(toCollection(ArrayList::new));
    }

    private List<Path> scan(String directory) throws URISyntaxException {
        Path dirPath;
        if (directory.startsWith("classpath:/")) {
            String resourceDir = directory.substring("classpath:/".length());
            URL url = Thread.currentThread().getContextClassLoader().getResource(resourceDir);
            dirPath = Paths.get(Objects.requireNonNull(url).toURI());
        } else {
            dirPath = Path.of(directory);
        }

        try (Stream<Path> files = Files.walk(dirPath)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error inspect directory path: ", e);
        }
        return List.of(dirPath);
    }
}
