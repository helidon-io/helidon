package io.helidon.service.tests.shutdown.from.startup;

import io.helidon.service.registry.ServiceRegistryManager;

public class Main {
    public static void main(String[] args) {
        ServiceRegistryManager.start();
    }
}
