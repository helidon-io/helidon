package io.helidon.service.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.service.registry.GeneratedService.Descriptor;

/**
 * Helidon service registry configuration.
 */
@Prototype.Blueprint
@Prototype.Configured("service-registry")
@Prototype.CustomMethods(ServiceRegistryConfigSupport.CustomMethods.class)
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
    List<Descriptor<?>> serviceDescriptors();

    /**
     * Manually register initial bindings for some of the services in the registry.
     *
     * @return service instances to register
     */
    @Option.Singular
    @Option.SameGeneric
    Map<Descriptor<?>, Object> serviceInstances();

    /**
     * Config instance used to configure this registry configuration.
     * DO NOT USE for application configuration!
     *
     * @return config node used to configure this service registry config instance (if any)
     */
    Optional<Config> config();
}
