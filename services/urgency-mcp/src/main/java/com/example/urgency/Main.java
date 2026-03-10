package com.example.urgency;

import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

@Service.GenerateBinding
public class Main {

    static {
        LogConfig.initClass();
    }

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        ServiceRegistryManager.start(ApplicationBinding.create());
    }
}
