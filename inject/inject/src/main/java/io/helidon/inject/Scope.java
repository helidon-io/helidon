package io.helidon.inject;

import io.helidon.inject.service.ServiceDescriptor;

/**
 * A scope.
 */
public interface Scope {
    /**
     * Bind a scope specific instance for the service descriptor in this scope.
     * Note that binding to singleton scope is only allowed if {@link io.helidon.inject.InjectionConfig#permitsDynamic()}
     * is set to {@code true}. Other scopes may have different restrictions.
     *
     * @param descriptor service descriptor to bind (such as a {@link io.helidon.inject.service.ServiceDescriptor} instance)
     * @param serviceInstance the instance to bind
     */
    void bind(ServiceDescriptor<?> descriptor, Object serviceInstance);

    /**
     * Stop the scope, and destroy all service instances created within it.
     */
    void close();
}
