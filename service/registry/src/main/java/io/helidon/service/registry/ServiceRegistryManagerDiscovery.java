package io.helidon.service.registry;

import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.service.registry.spi.ServiceRegistryManagerProvider;

class ServiceRegistryManagerDiscovery {
    private static final Optional<ServiceRegistryManagerProvider> REGISTRY_MANAGER_PROVIDER =
            HelidonServiceLoader.builder(ServiceLoader.load(ServiceRegistryManagerProvider.class))
                    .build()
                    .stream()
                    .findFirst();

    static ServiceRegistryManager create(ServiceRegistryConfig config) {
        ServiceDiscovery discovery = config.discoverServices()
                ? CoreServiceDiscovery.instance()
                : CoreServiceDiscovery.noop();

        ServiceRegistryManager coreRegistryManager = new CoreServiceRegistryManager(config, discovery);
        return REGISTRY_MANAGER_PROVIDER.map(it -> it.create(config, discovery, coreRegistryManager))
                .orElse(coreRegistryManager);
    }
}
