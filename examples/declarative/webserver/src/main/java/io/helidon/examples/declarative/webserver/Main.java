package io.helidon.examples.declarative.webserver;

import io.helidon.logging.common.LogConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;

public class Main {
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        InjectRegistryManager m = InjectRegistryManager.create();
        InjectRegistry registry = m.registry();
        registry.all(Lookup.builder()
                             .runLevel(Injection.RunLevel.STARTUP)
                             .build());
    }
}
