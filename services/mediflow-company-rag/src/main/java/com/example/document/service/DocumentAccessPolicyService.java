package com.example.document.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@ApplicationScoped
public class DocumentAccessPolicyService {

    private Map<String, List<String>> accessPolicy = new HashMap<>();

    public DocumentAccessPolicyService() {
        loadPolicy();
    }

    /**
     * Loads the document access policy from YAML file.
     */
    private void loadPolicy() {
        try {
            InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("config/document_access_policy.yaml");

            if (inputStream == null) {
                System.err.println("Warning: document_access_policy.yaml not found, using empty policy");
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String docName = entry.getKey();
                    Object value = entry.getValue();

                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> docConfig = (Map<String, Object>) value;
                        Object readObj = docConfig.get("read");

                        if (readObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> teams = (List<String>) readObj;
                            accessPolicy.put(docName, new ArrayList<>(teams));
                        }
                    }
                }
            }

            inputStream.close();
        } catch (Exception e) {
            System.err.println("Error loading document access policy: " + e.getMessage());
            e.printStackTrace();
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
        // TODO: Persist to YAML file
    }

    /**
     * Gets all documents and their access teams.
     */
    public Map<String, List<String>> getAllAccessPolicies() {
        return new HashMap<>(accessPolicy);
    }
}
