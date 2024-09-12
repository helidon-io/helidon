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

package io.helidon.service.inject.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;

/**
 * Entry point to services with injection support in Helidon.
 * <p>
 * The service registry has knowledge about all the services within your application.
 * <p>
 * This is the full service registry with injection support.
 */
@Service.Contract
public interface InjectRegistry extends io.helidon.service.registry.ServiceRegistry {
    /**
     * {@link io.helidon.service.metadata.DescriptorMetadata#registryType()} for inject services.
     */
    String REGISTRY_TYPE_INJECT = "inject";

    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(InjectRegistry.class);

    /**
     * Get the first service instance matching the lookup with the expectation that there is a match available.
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
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
     *         if the result may contain an unknown provider type
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
     * {@link Injection.ServicesProvider},
     * {@link Injection.InjectionPointProvider}, or similar).
     *
     * @param lookup lookup criteria to find matching services
     * @param <T>    type of the service, if you use any other than {@link java.lang.Object}, make sure
     *               you have configured appropriate contracts in the lookup, as we cannot infer this
     * @return the best service supplier matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.service.registry.ServiceRegistryException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(Lookup lookup);

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
     * The returned instances are either the actual instances registered with the registry, or an inject
     * based wrapper if the service is from core registry. Use {@link InjectServiceInfo#coreInfo()} to get the actual instance
     * if instance equality is required.
     * <p>
     * The registry is optimized for look-ups based on service type and service contracts, all other
     * lookups trigger a full registry scan.
     *
     * @param lookup lookup criteria to find matching services
     * @return a list of service descriptors that match the lookup criteria
     */
    List<InjectServiceInfo> lookupServices(Lookup lookup);

}
