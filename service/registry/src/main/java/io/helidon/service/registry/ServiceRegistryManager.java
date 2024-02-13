package io.helidon.service.registry;

/**
 * Manager is responsible for managing the state of a {@link io.helidon.service.registry.ServiceRegistry},
 * and as a factory of service registries.
 */
public interface ServiceRegistryManager {

    static ServiceRegistryManager create() {
        return create(ServiceRegistryConfig.create());
    }

    static ServiceRegistryManager create(ServiceRegistryConfig config) {
        return ServiceRegistryManagerDiscovery.create(config);
    }

    ServiceRegistry registry();

    void shutdown();
}
