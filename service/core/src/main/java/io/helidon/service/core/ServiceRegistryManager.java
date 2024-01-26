package io.helidon.service.core;

/**
 * Manager is responsible for managing the state of a {@link io.helidon.service.core.ServiceRegistry},
 * and as a factory of service registries.
 */
public interface ServiceRegistryManager {
    ServiceRegistry registry();
    void shutdown();
}
