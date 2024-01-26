package io.helidon.inject.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * The service registry. The service registry generally has knowledge about all the services that are available within your
 * application, along with the contracts (i.e., interfaces) they advertise, the qualifiers that optionally describe them, and oll
 * of each services' dependencies on other service contracts, etc.
 * <p>
 * Collectively these service instances are considered "the managed service instances".
 * <p>
 * Services are described through a (code generated) {@link io.helidon.inject.service.ServiceDescriptor}, and this registry
 * will manage their lifecycle as required by their annotations (such as {@link io.helidon.inject.service.Injection.Singleton}).
 * <p>
 * This interface exposes a read-only set of methods providing access to these "managed service" instances,
 * suppliers, or service descriptors.
 * As service is activated when its supplier's {@code get} method is called.This is equivalent to the declarative form just using
 * {@link io.helidon.inject.service.Injection.Inject} instead.
 * Note that activation of a service might result in activation chaining. For example, service A injects service B, etc. When
 * service A is activated then service A's dependencies (i.e., injection points) need to be activated as well. To avoid long
 * activation chaining, it is recommended to that users strive to use {@link java.util.function.Supplier} injection whenever
 * possible.
 * Supplier injection (a) breaks long activation chains from occurring by deferring activation until when those services are
 * really needed, and (b) breaks circular references that lead to {@link io.helidon.inject.service.ServiceRegistryException}
 * during activation (i.e., service A injects B, and service B injects A).
 * <p>
 * The order of services depends on their declared {@link io.helidon.common.Weight}, presence of qualifiers (unqualified
 * services are first), and their fully qualified class name (if all else is equal).
 */
@Injection.Contract
public interface ServiceRegistry {
    /**
     * Type name of this interface.
     */
    TypeName TYPE_NAME = TypeName.create(ServiceRegistry.class);

    /**
     * Get the first service instance matching the lookup with the expectation that there is a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.service.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                            resolution to instance failed
     */
    <T> T get(Lookup lookup);

    /**
     * Get the first service instance matching the contract with the expectation that there is a match available.
     *
     * @param type contract to look-up
     * @param <T>  type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.inject.service.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                            resolution to instance failed
     */
    <T> T get(Class<T> type);

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    <T> Optional<T> first(Lookup lookup);

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param type contract to look-up
     * @param <T>  type of the contract
     * @return the best service instance matching the contract, or an empty {@link java.util.Optional} if none match
     */
    <T> Optional<T> first(Class<T> type);

    /**
     * Get all service instances matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    <T> List<T> all(Lookup lookup);

    /**
     * Get all service instances matching the lookup with the expectation that there may not be a match available.
     *
     * @param type contract to look-up
     * @param <T>  type of the contract
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    <T> List<T> all(Class<T> type);

    /**
     * Get the first service supplier matching the lookup with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.inject.service.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because
     * of scope mismatch, or because there is no available instance, and we use a runtime resolution through
     * {@link io.helidon.inject.service.ServicesProvider}, {@link io.helidon.inject.service.InjectionPointProvider}, or similar).
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service supplier matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.service.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(Lookup lookup);

    /**
     * Get the first service supplier matching the lookup with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.inject.service.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because
     * of scope mismatch, or because there is no available instance, and we use a runtime resolution through
     * {@link io.helidon.inject.service.ServicesProvider}, {@link io.helidon.inject.service.InjectionPointProvider}, or similar).
     *
     * @param type contract to find
     * @param <T>  type of the contract
     * @return the best service supplier matching the lookup
     * @throws io.helidon.inject.service.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(Class<T> type);

    /**
     * Find the first service provider matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    <T> Supplier<Optional<T>> supplyFirst(Lookup lookup);

    /**
     * Lookup a supplier of an optional service instance.
     *
     * @param type contract we look for
     * @param <T>  type of the contract
     * @return supplier of an optional instance
     */
    <T> Supplier<Optional<T>> supplyFirst(Class<T> type);

    /**
     * Lookup a supplier of all services matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return supplier of list of services ordered, may be empty if there is no match
     */
    <T> Supplier<List<T>> supplyAll(Lookup lookup);

    /**
     * Lookup a supplier of a list of instances of the requested type.
     * This is the preferred way of looking up instances, as the list may be computed at runtime, and may
     * differ in time (as some services may be {@link io.helidon.inject.service.ServicesProvider}).
     *
     * @param type contract we look for
     * @param <T>  type of the contract
     * @return a supplier of list of contracts
     */
    <T> Supplier<List<T>> supplyAll(Class<T> type);

    /**
     * Get a supplier for the provided service info. The service info must already be known by this registry,
     * either through a code generated module, or registered when creating the registry.
     * This method bypasses lookup.
     *
     * @param descriptor service descriptor to get a supplier for, please use the singleton instance (usually code generated
     *                   into a public {@code __ServiceDescriptor} class), we use instance equality to discover services
     * @param <T>        type of the service, if you use any other than {@link java.lang.Object}, make sure
     *                   you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return supplier of an instance for the descriptor provided
     */
    <T> Supplier<T> supply(ServiceInfo descriptor);

    /**
     * A lookup method that returns target instances with metadata.
     * As this method resolves actual instances, scopes of all discovered services must be active
     * (this would be always true if you only use singleton and "service" scopes).
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return list of registry instances (with metadata)
     */
    <T> List<ServiceInstance<T>> lookupInstances(Lookup lookup);

    /**
     * A lookup method operating on the service descriptors, rather than service instances.
     * This is a useful tool for tools that need to analyze the structure of the registry,
     * for testing etc.
     * The returned instances are always the same instances registered with this registry, and these
     * are expected to be the singleton instances from code generated {@link io.helidon.inject.service.ServiceDescriptor}.
     * <p>
     * The registry is optimized for look-ups based on service type and service contracts, all other
     * lookups trigger a full registry scan.
     *
     * @param lookup lookup criteria to find matching services
     * @return a list of service descriptors that match the lookup criteria
     */
    List<ServiceInfo> lookupServices(Lookup lookup);
}
