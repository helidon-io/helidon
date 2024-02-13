package io.helidon.service.inject.api;

import java.util.function.Supplier;

import io.helidon.service.registry.ServiceInfo;

/**
 * Service registry of a specific scope.
 */
public interface ScopedRegistry {
    /**
     * Activate this registry instance. This method will prepare this registry for use.
     */
    void activate();

    /**
     * Deactivate this registry instance. This method will deactivate all active instances
     *
     * @throws io.helidon.service.registry.ServiceRegistryException in case one or more services failed to deactivate
     */
    void deactivate();

    /**
     * Provides either an existing activator, if one is already available in this scope, or adds a new activator instance.
     *
     * @param descriptor        service descriptor
     * @param activatorSupplier supplier of new activators to manage service instances
     * @param <T>               type of the instances supported by the descriptor
     * @return activator for the service, either an existing one, or a new one created from the supplier
     */
    <T> Activator<T> activator(ServiceInfo descriptor,
                               Supplier<Activator<T>> activatorSupplier);
}
