package com.example.document.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;

import java.util.logging.Logger;


@ApplicationScoped
public class StartupService {

    private static final Logger LOGGER = Logger.getLogger(StartupService.class.getName());

    @Inject
    DocumentService documentService;

    @ConfigProperty(name = "demo.data.load")
    boolean loadDemoData;

    void onStart(@Observes StartupEvent ev) {
        if (loadDemoData) {
            LOGGER.info("demo.data.load=true: Wiping database and loading/embedding all documents...");
            documentService.wipeAllEmbeddings();
            documentService.embedLoadedDocuments();
            LOGGER.info("Startup complete - database wiped and all documents loaded and embedded.");
        } else {
            LOGGER.info("Starting up - preserving existing database (use -Ddemo.data.load=true to wipe and reload)");
        }
    }
}
