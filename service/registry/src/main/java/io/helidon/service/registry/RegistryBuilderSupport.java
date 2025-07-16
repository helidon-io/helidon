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
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Methods used from generated code in builders when the service registry (without Config) is used.
 */
public class RegistryBuilderSupport {
    private RegistryBuilderSupport() {
    }

    /**
     * Discover services from the registry.
     *
     * @param registry    service registry to use, if provided explicitly
     * @param contract    contract that is requested
     * @param useRegistry whether to use the service registry at all
     * @param <T>         type of the contract
     * @return a list of contract implementation from the registry
     */
    public static <T> List<T> serviceList(Optional<ServiceRegistry> registry,
                                          TypeName contract,
                                          boolean useRegistry) {
        return serviceList(registry, contract, useRegistry, Optional.empty());
    }

    /**
     * Discover services from the registry.
     *
     * @param registry        service registry to use, if provided explicitly
     * @param contract        contract that is requested
     * @param useRegistry     whether to use the service registry at all
     * @param namedQualifier an optional qualifier name to filter the services
     * @param <T>             type of the contract
     * @return a list of contract implementation from the registry
     */
    public static <T> List<T> serviceList(Optional<ServiceRegistry> registry,
                                          TypeName contract,
                                          boolean useRegistry,
                                          Optional<String> namedQualifier) {

        if (!useRegistry) {
            return List.of();
        }

        return lookupAll(registry, contract, namedQualifier);
    }

    /**
     * Discover services from the registry.
     *
     * @param registry    service registry to use, if provided explicitly
     * @param contract    contract that is requested
     * @param useRegistry whether to use the service registry at all
     * @param <T>         type of the contract
     * @return a set of contract implementation
     */
    public static <T> Set<T> serviceSet(Optional<ServiceRegistry> registry,
                                        TypeName contract,
                                        boolean useRegistry) {
        return serviceSet(registry, contract, useRegistry, Optional.empty());
    }

    /**
     * Discover services from the registry.
     *
     * @param registry       service registry to use, if provided explicitly
     * @param contract       contract that is requested
     * @param useRegistry    whether to use the service registry at all
     * @param namedQualifier an optional qualifier name to filter the services
     * @param <T>            type of the contract
     * @return a set of contract implementation
     */
    public static <T> Set<T> serviceSet(Optional<ServiceRegistry> registry,
                                        TypeName contract,
                                        boolean useRegistry,
                                        Optional<String> namedQualifier) {
        if (!useRegistry) {
            return Set.of();
        }

        return Set.copyOf(lookupAll(registry, contract, namedQualifier));
    }

    /**
     * Get the first service from the registry if not configured in the builder.
     *
     * @param registry      service registry to use, if provided explicitly
     * @param contract      contract that is requested
     * @param existingValue current values configured on the builder
     * @param useRegistry   whether to use the service registry at all
     * @param <T>           type of the contract
     * @return a list of contract implementation, combined from what user provided in builder and what was discovered in registry
     */
    public static <T> Optional<T> service(Optional<ServiceRegistry> registry,
                                          TypeName contract,
                                          Optional<T> existingValue,
                                          boolean useRegistry) {
        return service(registry, contract, existingValue, useRegistry, Optional.empty());
    }

    /**
     * Retrieves the first matching service based on the provided contract, qualifiers,
     * and the current configuration. Allows for combining explicitly configured values
     * with discovered values from a registry.
     *
     * @param <T>            the type of the service contract
     * @param registry       an optional service registry to use, if explicitly provided
     * @param contract       the contract that is requested
     * @param existingValue  an optional existing value, to be used if already present
     * @param useRegistry    a flag indicating whether to use the service registry
     * @param namedQualifier an optional qualifier name to filter the services
     * @return an optional containing the matching service, if found
     */
    public static <T> Optional<T> service(Optional<ServiceRegistry> registry,
                                          TypeName contract,
                                          Optional<T> existingValue,
                                          boolean useRegistry,
                                          Optional<String> namedQualifier) {
        if (existingValue.isPresent() || !useRegistry) {
            return existingValue;
        }

        if (namedQualifier.isEmpty()) {
            return registry.orElseGet(GlobalServiceRegistry::registry).first(contract);
        }

        return namedQualifier
                .map(Qualifier::createNamed)
                .map(qualifier -> Lookup.builder()
                        .addContract(contract)
                        .addQualifier(qualifier)
                        .build())
                .flatMap(l -> registry.orElseGet(GlobalServiceRegistry::registry).first(l)
                );
    }

    private static <T> List<T> lookupAll(Optional<ServiceRegistry> registry,
                                         TypeName contract,
                                         Optional<String> namedQualifier) {

        if (namedQualifier.isEmpty()) {
            return registry.orElseGet(GlobalServiceRegistry::registry).all(contract);
        }

        return namedQualifier
                .map(Qualifier::createNamed)
                .map(qualifier -> Lookup.builder()
                        .addContract(contract)
                        .addQualifier(qualifier)
                        .build())
                .map(value -> registry.orElseGet(GlobalServiceRegistry::registry).<T>all(value))
                .orElseGet(List::of);
    }
}
