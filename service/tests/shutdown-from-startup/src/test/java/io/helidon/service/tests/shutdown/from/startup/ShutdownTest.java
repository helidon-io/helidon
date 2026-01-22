package io.helidon.service.tests.shutdown.from.startup;

import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

class ShutdownTest {
    @Test
    void testShutdown() {
//        ServiceRegistryManager manager = ServiceRegistryManager.start();
//        ShutdownService.MANAGER.set(manager);
//        manager.registry()
//                .get(UsedService.class);
        ServiceRegistryManager.start();
    }
}
