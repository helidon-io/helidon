/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.List;
import java.util.Optional;

import io.helidon.inject.service.ServiceInfo;

/**
 * This is an advanced service registry providing access to service providers, rather than just
 * {@link java.util.function.Supplier}.
 */
public interface ServiceProviderRegistry {
    /**
     * Find the first service provider matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    default <T> Optional<ServiceProvider<T>> first(Lookup lookup) {
        return this.<T>all(lookup)
                .stream()
                .findFirst();
    }

    /**
     * Find the first service provider matching the provided type with the expectation that there may not be a match available.
     *
     * @param type type of the expected service provider
     * @param <T>  service type or service contract
     * @return the best service provider matching the lookup
     */
    default <T> Optional<ServiceProvider<T>> first(Class<T> type) {
        return first(Lookup.builder()
                             .addContract(type)
                             .build());
    }

    /**
     * Find the first service provider matching the lookup with the expectation that there must be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service provider, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    default <T> ServiceProvider<T> get(Lookup lookup) {
        return this.<T>first(lookup)
                .orElseThrow(() -> new InjectionException("There are no services matching " + lookup));
    }

    /**
     * Find the first service provider matching the provided type with the expectation that there must be a match available.
     *
     * @param type type of the expected service provider
     * @param <T>  service type or service contract
     * @return the best service provider matching the lookup
     */
    default <T> ServiceProvider<T> get(Class<T> type) {
        return get(Lookup.create(type));
    }

    /**
     * Get all service providers matching the lookup.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known, or may contain a mix of types
     * @return list of service providers
     */
    <T> List<ServiceProvider<T>> all(Lookup lookup);

    /**
     * Get all service providers matching the lookup with the expectation that there may not be a match available.
     *
     * @param type type of the expected service providers
     * @param <T>  type of the expected service providers
     * @return list of service providers ordered, may be empty if there is no match
     */
    default <T> List<ServiceProvider<T>> all(Class<T> type) {
        return all(Lookup.builder()
                           .addContract(type)
                           .build());
    }

    /**
     * Get a service provider for a descriptor.
     *
     * @param serviceInfo service information (metadata of the service)
     * @param <T>         type of the expected service providers, use {@code Object} if not known, or may contain a mix of types
     * @return service provider created for the descriptor
     * @throws java.util.NoSuchElementException in case the descriptor is not part of this registry
     */
    <T> ServiceProvider<T> get(ServiceInfo serviceInfo);
}
