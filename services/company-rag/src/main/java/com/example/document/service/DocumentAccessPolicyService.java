package com.example.document.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toCollection;

@ApplicationScoped
public class DocumentAccessPolicyService {

    private static final Logger LOGGER = Logger.getLogger(DocumentAccessPolicyService.class.getName());

    private Map<String, List<String>> accessPolicy = new HashMap<>();

    @ConfigProperty(name = "demo.config.access.path")
    String accessConfigPath;

    private Path accessPolicyFilePath;

    @PostConstruct
    void init() {
        try {
            accessPolicyFilePath = Path.of(accessConfigPath).toAbsolutePath().normalize();

            if (Files.exists(accessPolicyFilePath) && Files.isRegularFile(accessPolicyFilePath)) {
                try (InputStream configStream = Files.newInputStream(accessPolicyFilePath)) {
                    Yaml yaml = new Yaml();
                    Object loaded = yaml.load(configStream);
                    accessPolicy = parseAccessPolicy(loaded);
                }
                LOGGER.info("Loaded " + accessPolicy.size() + " document access policies from " + accessPolicyFilePath);
            } else {
                accessPolicy = new HashMap<>();
                LOGGER.info("No access policy file at " + accessPolicyFilePath + "; all documents visible to all");
            }
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
        persistToYaml();
    }

    /**
     * Removes a document from the access policy.
     */
    public void removeDocument(String documentName) {
        accessPolicy.remove(documentName);
        persistToYaml();
    }

    private void persistToYaml() {
        if (accessPolicyFilePath == null) {
            return;
        }
        try {
            Files.createDirectories(accessPolicyFilePath.getParent());
            Map<String, Map<String, List<String>>> yamlRoot = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : accessPolicy.entrySet()) {
                Map<String, List<String>> read = new LinkedHashMap<>();
                read.put("read", new ArrayList<>(entry.getValue()));
                yamlRoot.put(entry.getKey(), read);
            }
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            Files.writeString(accessPolicyFilePath, yaml.dump(yamlRoot));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist access policy to " + accessPolicyFilePath, e);
        }
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
}
