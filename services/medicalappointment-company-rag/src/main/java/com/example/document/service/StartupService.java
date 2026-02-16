package com.example.document.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class StartupService {

    @Inject
    DocumentService documentService;

    @ConfigProperty(name = "DemoData", defaultValue = "false")
    boolean demoData;

    void onStart(@Observes StartupEvent ev) {
        if (demoData) {
            System.out.println("DemoData=true: Wiping database and loading/embedding all documents...");
            documentService.wipeAllEmbeddings();
            documentService.loadAndEmbedAllDocuments();
            System.out.println("Startup complete - database wiped and all documents loaded and embedded.");
        } else {
            System.out.println("Starting up - preserving existing database (use -DDemoData=true to wipe and reload)");
            // Just ensure collection exists, but don't load documents
            documentService.ensureCollectionExists();
        }
    }
}
