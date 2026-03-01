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

    @ConfigProperty(name = "sync-demo-data", defaultValue = "false")
    boolean syncDemoData;

    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) StartupEvent ev) {
        if (loadDemoData) {
            LOGGER.info("demo.data.load=true: Wiping database, then syncing/embedding folder documents...");
            documentService.wipeAllEmbeddings();
        }
        if (loadDemoData || syncDemoData) {
            LOGGER.info("Startup sync enabled: Loading/embedding all documents from folder...");
            documentService.embedLocalDocuments();
            LOGGER.info("Startup complete - folder documents embedded.");
        } else {
            LOGGER.info("Starting up - preserving existing database and skipping startup sync (use -Dsync-demo-data=true to embed folder documents).");
        }
    }
}
