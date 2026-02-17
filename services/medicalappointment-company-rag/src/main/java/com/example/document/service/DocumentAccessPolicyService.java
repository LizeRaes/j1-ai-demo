package com.example.document.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

@ApplicationScoped
public class DocumentAccessPolicyService {

    private static final Logger LOGGER = Logger.getLogger(DocumentAccessPolicyService.class.getName());

    private final Map<String, List<String>> accessPolicy = new HashMap<>();

    @ConfigProperty(name = "oracleai.embedding.metadata.column", defaultValue = "metadata")
    String metadataColumn;

    @PostConstruct
    void init() {
        try {
            InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("config/document_access_policy.yaml");

            if (inputStream == null) {
                LOGGER.severe("Warning: document_access_policy.yaml not found, using empty policy");
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Map<String, List<String>>> data = yaml.load(inputStream);

            if (data != null) {
                for (Map.Entry<String, Map<String, List<String>>> entry : data.entrySet()) {
                    String docName = entry.getKey();
                    Map<String, List<String>> value = entry.getValue();

                    if (value != null) {
                        List<String> readObj = value.get("read");

                        if (readObj != null) {
                            accessPolicy.put(docName, readObj);
                        }
                    }
                }
            }

            inputStream.close();
        } catch (Exception e) {
            LOGGER.severe("Error loading document access policy: " + e.getMessage());
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
        return accessPolicy;
    }
}
