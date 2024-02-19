package io.helidon.service.registry;

import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Metadata of a single service descriptor.
 * This information is stored within the Helidon specific {code META-INF} services file.
 */
public interface DescriptorMetadata {
    /**
     * {@link #registryType()} for core services.
     */
    String REGISTRY_TYPE_CORE = "core";

    /**
     * Type of registry, such as {@code core} or {@code inject}.
     *
     * @return registry type this descriptor is created for
     */
    String registryType();

    /**
     * Descriptor type name.
     *
     * @return descriptor type
     */
    TypeName descriptorType();

    /**
     * Contracts of the service.
     *
     * @return contracts the service implements/provides.
     */
    Set<TypeName> contracts();

    /**
     * Weight of the service.
     *
     * @return service weight
     * @see io.helidon.common.Weight
     */
    double weight();

    /**
     * Descriptor instance.
     *
     * @return the descriptor
     */
    GeneratedService.Descriptor<?> descriptor();
}
