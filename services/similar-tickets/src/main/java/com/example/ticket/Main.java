package com.example.ticket;

import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;

@Service.GenerateBinding
public class Main {

	public static void main(String[] args) {
        LogConfig.configureRuntime();
        // Start Helidon
        ServiceRegistryManager.start(ApplicationBinding.create());
		System.out.println("Server started at http://localhost:" + Services.get(WebServer.class).port());
	}
}
