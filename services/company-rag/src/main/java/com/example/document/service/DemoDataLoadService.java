package com.example.document.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;

import java.util.logging.Logger;


@Startup
public class DemoDataLoadService {

    private static final Logger LOGGER = Logger.getLogger(DemoDataLoadService.class.getName());

    @Inject
    DocumentService documentService;

    @ConfigProperty(name = "demo.data.load")
    boolean loadDemoData;

    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) StartupEvent ev) {
        if (loadDemoData) {
            LOGGER.info("demo.data.load=true: Wiping database and loading/embedding all documents...");
            documentService.wipeAllEmbeddings();
            documentService.embedLocalDocuments();
            LOGGER.info("Startup complete - database wiped and all documents loaded and embedded.");
        } else {
            LOGGER.info("Starting up - preserving existing database (use -Ddemo.data.load=true to wipe and reload)");
        }
    }
}
