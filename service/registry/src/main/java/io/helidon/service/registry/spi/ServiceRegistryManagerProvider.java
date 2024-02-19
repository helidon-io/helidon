package io.helidon.service.registry.spi;

import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

/**
 * A {@link java.util.ServiceLoader} provider that enables a different type of service registry.
 * In Helidon this could be a service registry with full injection support.
 */
public interface ServiceRegistryManagerProvider {
    /**
     * Create a new registry manager.
     *
     * @param config              configuration as provided to {@link io.helidon.service.registry.ServiceRegistryManager}
     * @param serviceDiscovery    service discovery to load service instances
     * @param coreRegistryManager core service registry manager, if it would be used as a backing one for the one provided by this
     *                            service
     * @return a new service registry manager
     */
    ServiceRegistryManager create(ServiceRegistryConfig config,
                                  ServiceDiscovery serviceDiscovery,
                                  ServiceRegistryManager coreRegistryManager);
}
