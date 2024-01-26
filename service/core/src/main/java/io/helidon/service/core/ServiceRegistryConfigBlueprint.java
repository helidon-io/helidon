package io.helidon.service.core;

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Helidon service registry configuration.
 */
@Prototype.Blueprint
@Prototype.Configured("service-registry")
interface ServiceRegistryConfigBlueprint {
    /**
     * Whether to discover services from the class path.
     * When set to {@code false}, only services added through {@link #serviceDescriptors()} and/or
     * {@link #serviceInstances()} would be available.
     *
     * @return whether to discover services from classpath
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean discoverServices();

    /**
     * Manually registered service descriptors to add to the registry.
     * This is useful when {@link #discoverServices()} is set to {@code false}, to register only hand-picked services
     * into the registry.
     * <p>
     * Even when service discovery is used, this can be used to add service descriptors that are not part of
     * a service discovery mechanism (such as testing services).
     *
     * @return services to register
     */
    @Option.Singular
    List<ServiceDescriptor<?>> serviceDescriptors();

    /**
     * Manually register initial bindings for some of the services in the registry.
     *
     * @return service instances to register
     */
    @Option.Singular
    @Option.SameGeneric
    Map<ServiceDescriptor<?>, Object> serviceInstances();
}
