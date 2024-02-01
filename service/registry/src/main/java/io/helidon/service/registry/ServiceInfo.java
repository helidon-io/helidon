package io.helidon.service.registry;

import java.util.List;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

/**
 * Service metadata.
 */
public interface ServiceInfo extends Weighted {
    /**
     * Type of the service this descriptor describes.
     *
     * @return service type
     */
    TypeName serviceType();

    /**
     * Type of the service descriptor (usually generated).
     *
     * @return descriptor type
     */
    TypeName descriptorType();

    /**
     * Set of contracts the described service implements.
     *
     * @return set of contracts
     */
    default Set<TypeName> contracts() {
        return Set.of();
    }

    /**
     * List of dependencies required by this service (and possibly by its supertypes).
     * Each dependency is a point of injection of one instance into
     * constructor, method parameter, or a field.
     *
     * @return required dependencies
     */
    default List<Dependency> dependencies() {
        return List.of();
    }

    /**
     * Returns {@code true} for abstract classes and interfaces,
     * returns {@code false} by default.
     *
     * @return whether this descriptor describes an abstract class or interface
     */
    default boolean isAbstract() {
        return false;
    }
}
