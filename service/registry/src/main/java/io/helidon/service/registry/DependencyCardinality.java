package io.helidon.service.registry;

/**
 * Cardinality of the injection point.
 */
public enum DependencyCardinality {
    /**
     * Optional instance (the dependency is declared as {@code Optional<MyContract>}, this does not imply nullability.
     */
    OPTIONAL,
    /**
     * Required instance.
     */
    REQUIRED,
    /**
     * List of instances.
     */
    LIST
}
