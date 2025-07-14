/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.service.registry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * Entry point to services in Helidon.
 * <p>
 * The service registry has knowledge about all the services within your application.
 */
@Service.Contract
@Service.Describe
public interface ServiceRegistry {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(ServiceRegistry.class);

    /**
     * Get the first service instance matching the contract with the expectation that there is a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> T get(Class<T> contract) {
        return get(TypeName.create(contract));
    }

    /**
     * Get the first named service instance matching the contract with the expectation that there is a match available.
     *
     * @param contract contract to look-up
     * @param name     name qualifier of the instance to get
     * @param <T>      type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> T getNamed(Class<T> contract, String name) {
        Objects.requireNonNull(contract);
        Objects.requireNonNull(name);

        return get(contract, Qualifier.createNamed(name));
    }

    /**
     * Get the first service instance matching the contract and qualifiers with the expectation that there is a match available.
     *
     * @param contract   contract to look-up
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> T get(Class<T> contract, Qualifier... qualifiers) {
        Objects.requireNonNull(contract);
        Objects.requireNonNull(qualifiers);

        return get(Lookup.builder()
                           .addContract(contract)
                           .qualifiers(Set.of(qualifiers))
                           .build());
    }

    /**
     * Get the first service instance matching the contract with the expectation that there is a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract (we will "blindly" cast the result to the expected type, make sure you use the right
     *                 one)
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    <T> T get(TypeName contract);

    /**
     * Get the first named service instance matching the contract with the expectation that there is a match available.
     *
     * @param contract contract to look-up
     * @param name     name qualifier of the instance to get
     * @param <T>      type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> T getNamed(TypeName contract, String name) {
        return get(contract, Qualifier.createNamed(name));
    }

    /**
     * Get the first service instance matching the contract and qualifiers with the expectation that there is a match available.
     *
     * @param contract   contract to look-up
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract (we will "blindly" cast the result to the expected type, make sure you use the right
     *                   one)
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> T get(TypeName contract, Qualifier... qualifiers) {
        return get(Lookup.builder()
                           .addContract(contract)
                           .qualifiers(Set.of(qualifiers))
                           .build());
    }


    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return the best service instance matching the contract, or an empty {@link java.util.Optional} if none match
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> Optional<T> first(Class<T> contract) {
        return first(TypeName.create(contract));
    }

    /**
     * Get the first named service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param name     name qualifier of the instance to get
     * @param <T>      type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> Optional<T> firstNamed(Class<T> contract, String name) {
        return first(contract, Qualifier.createNamed(name));
    }

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract   contract to look-up
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return the best service instance matching the contract, or an empty {@link java.util.Optional} if none match
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> Optional<T> first(Class<T> contract, Qualifier... qualifiers) {
        return first(Lookup.builder()
                             .addContract(contract)
                             .qualifiers(Set.of(qualifiers))
                             .build());
    }

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return the best service instance matching the contract, or an empty {@link java.util.Optional} if none match
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    <T> Optional<T> first(TypeName contract);

    /**
     * Get the first named service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param name     name qualifier of the instance to get
     * @param <T>      type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> Optional<T> firstNamed(TypeName contract, String name) {
        return first(contract, Qualifier.createNamed(name));
    }

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract   contract to look-up
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return the best service instance matching the contract, or an empty {@link java.util.Optional} if none match
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    default <T> Optional<T> first(TypeName contract, Qualifier... qualifiers) {
        return first(Lookup.builder()
                             .addContract(contract)
                             .qualifiers(Set.of(qualifiers))
                             .build());
    }

    /**
     * Get all service instances matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    default <T> List<T> all(Class<T> contract) {
        return all(TypeName.create(contract));
    }

    /**
     * Get all service instances matching the contract with the expectation that there may not be a match available.
     *
     * @param contract   contract to look-up
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    default <T> List<T> all(Class<T> contract, Qualifier... qualifiers) {
        return all(Lookup.builder()
                           .addContract(contract)
                           .qualifiers(Set.of(qualifiers))
                           .build());
    }

    /**
     * Get all service instances matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    <T> List<T> all(TypeName contract);

    /**
     * Get all service instances matching the contract with the expectation that there may not be a match available.
     *
     * @param contract   contract to look-up
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    default <T> List<T> all(TypeName contract, Qualifier... qualifiers) {
        return all(Lookup.builder()
                           .addContract(contract)
                           .qualifiers(Set.of(qualifiers))
                           .build());
    }

    /**
     * Get the first service supplier matching the contract with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.service.registry.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because of scope mismatch, or because an instance was not provided by the service provider.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return the best service supplier matching the lookup
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    default <T> Supplier<T> supply(Class<T> contract) {
        return supply(TypeName.create(contract));
    }

    /**
     * Get the first service supplier matching the contract with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.service.registry.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because of scope mismatch, or because an instance was not provided by the service provider.
     *
     * @param contract   contract to find
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return the best service supplier matching the lookup
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    default <T> Supplier<T> supply(Class<T> contract, Qualifier... qualifiers) {
        return supply(Lookup.builder()
                              .addContract(contract)
                              .qualifiers(Set.of(qualifiers))
                              .build());
    }

    /**
     * Get the first service supplier matching the contract with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.service.registry.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because of scope mismatch, or because an instance was not provided by the service provider.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return the best service supplier matching the lookup
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(TypeName contract);

    /**
     * Get the first service supplier matching the contract with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.service.registry.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because of scope mismatch, or because an instance was not provided by the service provider.
     *
     * @param contract   contract to find
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return the best service supplier matching the lookup
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    default <T> Supplier<T> supply(TypeName contract, Qualifier... qualifiers) {
        return supply(Lookup.builder()
                              .addContract(contract)
                              .qualifiers(Set.of(qualifiers))
                              .build());
    }

    /**
     * Get the first service supplier matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract we look for
     * @param <T>      type of the contract
     * @return supplier of an optional instance
     */
    default <T> Supplier<Optional<T>> supplyFirst(Class<T> contract) {
        return supplyFirst(TypeName.create(contract));
    }

    /**
     * Get the first service supplier matching the contract with the expectation that there may not be a match available.
     *
     * @param contract   contract we look for
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return supplier of an optional instance
     */
    default <T> Supplier<Optional<T>> supplyFirst(Class<T> contract, Qualifier... qualifiers) {
        return supplyFirst(Lookup.builder()
                                   .addContract(contract)
                                   .qualifiers(Set.of(qualifiers))
                                   .build());
    }

    /**
     * Get the first service supplier matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract we look for
     * @param <T>      type of the contract
     * @return supplier of an optional instance
     */
    <T> Supplier<Optional<T>> supplyFirst(TypeName contract);

    /**
     * Get the first service supplier matching the contract with the expectation that there may not be a match available.
     *
     * @param contract   contract we look for
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return supplier of an optional instance
     */
    default <T> Supplier<Optional<T>> supplyFirst(TypeName contract, Qualifier... qualifiers) {
        return supplyFirst(Lookup.builder()
                                   .addContract(contract)
                                   .qualifiers(Set.of(qualifiers))
                                   .build());
    }

    /**
     * Lookup a supplier of a list of instances of the requested contract, with the expectation that there may not be a
     * match available.
     *
     * @param contract contract we look for
     * @param <T>      type of the contract
     * @return a supplier of list of instances
     */
    default <T> Supplier<List<T>> supplyAll(Class<T> contract) {
        return supplyAll(TypeName.create(contract));
    }

    /**
     * Lookup a supplier of a list of instances of the requested contract, with the expectation that there may not be a
     * match available.
     *
     * @param contract   contract we look for
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return a supplier of list of instances
     */
    default <T> Supplier<List<T>> supplyAll(Class<T> contract, Qualifier... qualifiers) {
        return supplyAll(Lookup.builder()
                                 .addContract(contract)
                                 .qualifiers(Set.of(qualifiers))
                                 .build());
    }

    /**
     * Lookup a supplier of a list of instances of the requested contract, with the expectation that there may not be a
     * match available.
     *
     * @param contract contract we look for
     * @param <T>      type of the contract
     * @return a supplier of list of instances
     */
    <T> Supplier<List<T>> supplyAll(TypeName contract);

    /**
     * Lookup a supplier of a list of instances of the requested contract, with the expectation that there may not be a
     * match available.
     *
     * @param contract   contract we look for
     * @param qualifiers qualifiers to find
     * @param <T>        type of the contract
     * @return a supplier of list of instances
     */
    default <T> Supplier<List<T>> supplyAll(TypeName contract, Qualifier... qualifiers) {
        return supplyAll(Lookup.builder()
                                 .addContract(contract)
                                 .qualifiers(Set.of(qualifiers))
                                 .build());
    }

    /**
     * Provide a value for a specific service info instance.
     * This method uses instance equality for service info, so be careful to use the singleton instance
     * from the service descriptor, or instances provided by {@link #allServices(Class)}.
     *
     * @param serviceInfo service info instance
     * @param <T>         type of the expected instance, we just cast to it, so this may cause runtime issues if assigned to
     *                    invalid
     *                    type
     * @return value of the service described by the service info provided (always a single value), as there is support
     *         for providers that are {@link java.util.function.Supplier} of an instance, and that may return
     *         {@link java.util.Optional}, we may not get a value, hence we return {@link java.util.Optional} as well
     */
    <T> Optional<T> get(ServiceInfo serviceInfo);

    /**
     * Get all services for a specific contract. The list may be empty if there are no services available.
     * To get an instance, use {@link #get(ServiceInfo)}.
     *
     * @param contract contract we look for
     * @return list of service metadata of services that satisfy the provided contract
     */
    default List<ServiceInfo> allServices(Class<?> contract) {
        return allServices(TypeName.create(contract));
    }

    /**
     * Get all services for a specific contract. The list may be empty if there are no services available.
     * To get an instance, use {@link #get(ServiceInfo)}.
     *
     * @param contract contract we look for
     * @return list of service metadata of services that satisfy the provided contract
     */
    List<ServiceInfo> allServices(TypeName contract);

    /**
     * Get the first service instance matching the lookup with the expectation that there is a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown instance
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                              resolution to instance failed
     */
    <T> T get(Lookup lookup);

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown instance
     */
    <T> Optional<T> first(Lookup lookup);

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
     * Get the first service supplier matching the lookup with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.service.registry.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because
     * of scope mismatch, or because there is no available instance, and we use a runtime resolution through
     * {@link io.helidon.service.registry.Service.ServicesFactory},
     * {@link io.helidon.service.registry.Service.InjectionPointFactory}, or similar).
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service supplier matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown instance
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(Lookup lookup);

    /**
     * Find the first service matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown instance
     */
    <T> Supplier<Optional<T>> supplyFirst(Lookup lookup);

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
     * A lookup method operating on the service descriptors, rather than service instances.
     * This is useful for tools that need to analyze the structure of the registry,
     * for testing etc.
     * <p>
     * The registry is optimized for look-ups based on service type and service contracts, all other
     * lookups trigger a full registry scan.
     *
     * @param lookup lookup criteria to find matching services
     * @return a list of service descriptors that match the lookup criteria
     */
    List<ServiceInfo> lookupServices(Lookup lookup);

    /**
     * A lookup method that provides a list of qualified instances, rather than just a service instance.
     * This is to align with the possible injection points in services.
     * <p>
     * The registry is optimized for look-ups based on service type and service contracts, all other
     * lookups trigger a full registry scan.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T> type of the expected result, use {@link java.lang.Object} for results with more than one contract
     * @return a list of qualified service instances that match the lookup criteria
     */
    <T> List<ServiceInstance<T>> lookupInstances(Lookup lookup);

    /**
     * Provides registry metrics information.
     *
     * @return registry metrics
     */
    RegistryMetrics metrics();
}
