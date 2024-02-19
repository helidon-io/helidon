package io.helidon.service.registry;

/**
 * Manager is responsible for managing the state of a {@link io.helidon.service.registry.ServiceRegistry}.
 * Each manager instances owns a single service registry.
 * <p>
 * To use a singleton service across application, either pass it through parameters, or use
 * {@link io.helidon.service.registry.GlobalServiceRegistry}.
 */
public interface ServiceRegistryManager {
    /**
     * Create a new service registry manager with default configuration.
     *
     * @return a new service registry manager
     */
    static ServiceRegistryManager create() {
        return create(ServiceRegistryConfig.create());
    }

    /**
     * Create a new service registry manager with custom configuration.
     *
     * @param config configuration of this registry manager
     * @return a new configured service registry manager
     */
    static ServiceRegistryManager create(ServiceRegistryConfig config) {
        return ServiceRegistryManagerDiscovery.create(config);
    }

    /**
     * Get (or initialize and get) the service registry managed by this manager.
     *
     * @return service registry ready to be used
     */
    ServiceRegistry registry();

    /**
     * Shutdown the managed service registry.
     */
    void shutdown();
}
