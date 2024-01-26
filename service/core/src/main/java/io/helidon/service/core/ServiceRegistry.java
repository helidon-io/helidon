/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.core;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * Entry point to services in Helidon.
 * <p>
 * The service registry has knowledge about all the services within your application.
 * <p>
 * There are two levels of support for services in Helidon:
 * <ul>
 *     <li>{@code core} - when only this module is used, only services annotated with annotations from
 *     {@link io.helidon.service.core.Service} are discovered and available for lookup</li>
 *     <li>{@code inject} - also all services that support injection are available (requires an additional runtime module),
 *     services can use annotations from {@link io.helidon.service.core.Injection}</li>
 * </ul>
 */
public interface ServiceRegistry {
    /**
     * Type name of this interface.
     */
    TypeName TYPE_NAME = TypeName.create(ServiceRegistry.class);

    /**
     * Get the first service instance matching the contract with the expectation that there is a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return the best service instance matching the contract
     * @throws io.helidon.service.core.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                          resolution to instance failed
     */
    <T> T get(Class<T> contract);

    /**
     * Get the first service instance matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return the best service instance matching the contract, or an empty {@link java.util.Optional} if none match
     * @throws io.helidon.service.core.ServiceRegistryException if there is no service that could satisfy the lookup, or the
     *                                                          resolution to instance failed
     */
    <T> Optional<T> first(Class<T> contract);

    /**
     * Get all service instances matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract to look-up
     * @param <T>      type of the contract
     * @return list of services matching the criteria, may be empty if none matched, or no instances were provided
     */
    <T> List<T> all(Class<T> contract);

    /**
     * Get the first service supplier matching the contract with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.service.core.ServiceRegistryException} in case the matching service cannot provide a value (either
     * because of scope mismatch, or because an instance was not provided by the service provider.
     *
     * @param contract contract to find
     * @param <T>      type of the contract
     * @return the best service supplier matching the lookup
     * @throws io.helidon.service.core.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(Class<T> contract);

    /**
     * Get the first service supplier matching the contract with the expectation that there may not be a match available.
     *
     * @param contract contract we look for
     * @param <T>      type of the contract
     * @return supplier of an optional instance
     */
    <T> Supplier<Optional<T>> supplyFirst(Class<T> contract);

    /**
     * Lookup a supplier of a list of instances of the requested contract, with the expectation that there may not be a
     * match available.
     *
     * @param contract contract we look for
     * @param <T>      type of the contract
     * @return a supplier of list of instances
     */
    <T> Supplier<List<T>> supplyAll(Class<T> contract);
}
