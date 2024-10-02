package io.helidon.service.codegen;

/**
 * Described service type.
 * <p>
 * Core services (services defined for core service registry) can be only {@link #SERVICE} or {@link #SUPPLIER}.
 * <p>
 * This enum is duplicated in Inject API, as we do not want to have a common dependency.
 */
enum ProviderType {
    /**
     * This is just a descriptor that cannot instantiate anything.
     */
    NONE,
    /**
     * Direct implementation of a service.
     * <p>
     * This is the case when service does not implement any of the service provider interfaces, but it does
     * implement at least one contract.
     */
    SERVICE,
    /**
     * The service implements a {@link java.util.function.Supplier} of a contract.
     */
    SUPPLIER,
    /**
     * The service implements a provider of a list of contract instances.
     */
    SERVICES_PROVIDER,
    /**
     * The service implements a provider that satisfies a specific injection point (either a single contract,
     * or a list of contract instances).
     */
    IP_PROVIDER,
    /**
     * The service implements a provider that is called for specific qualifiers (either a single contract,
     * or a list of contract instances).
     */
    QUALIFIED_PROVIDER
}
