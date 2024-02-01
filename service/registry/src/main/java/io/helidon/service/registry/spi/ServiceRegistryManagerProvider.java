package io.helidon.service.registry.spi;

import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

public interface ServiceRegistryManagerProvider {
    ServiceRegistryManager create(ServiceRegistryConfig config,
                                  ServiceDiscovery serviceDiscovery,
                                  ServiceRegistryManager coreRegistryManager);
}
