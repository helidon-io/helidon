/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.common.Weighted;
import io.helidon.inject.service.ServiceBinder;
import io.helidon.inject.service.ServiceInfo;

/**
 * The service registry. The service registry generally has knowledge about all the services that are available within your
 * application, along with the contracts (i.e., interfaces) they advertise, the qualifiers that optionally describe them, and oll
 * of each services' dependencies on other service contracts, etc.
 * <p>
 * Collectively these service instances are considered "the managed service instances" under Injection.
 * <p>
 * Services are described through a (code generated) {@link io.helidon.inject.service.ServiceDescriptor}, and this registry
 * will manage their lifecycle as required by their annotations (such as {@link io.helidon.inject.service.Injection.Singleton}).
 * <p>
 * This Services interface exposes a read-only set of methods providing access to these "managed service" providers, and
 * available
 * via one of the lookup methods provided. Once you resolve the service provider(s), the service provider can be activated by
 * calling one of its get() methods. This is equivalent to the declarative form just using
 * {@link io.helidon.inject.service.Injection.Inject} instead.
 * Note that activation of a service might result in activation chaining. For example, service A injects service B, etc. When
 * service A is activated then service A's dependencies (i.e., injection points) need to be activated as well. To avoid long
 * activation chaining, it is recommended to that users strive to use {@link java.util.function.Supplier} injection whenever
 * possible.
 * Supplier injection (a) breaks long activation chains from occurring by deferring activation until when those services are
 * really needed, and (b) breaks circular references that lead to {@link io.helidon.inject.InjectionException} during activation
 * (i.e., service A injects B, and service B injects A).
 * <p>
 * The services are ranked according to the provider's comparator. The Injection framework will rank according to a strategy that
 * first looks for
 * {@link io.helidon.common.Weighted}, and finally by the alphabetic ordering according
 * to the type name (package and class canonical name).
 */
public interface Services {

    /**
     * Default weight used by Helidon Injection components.
     * It is lower than the default, so it is easy to override service with custom providers.
     */
    double INJECT_WEIGHT = Weighted.DEFAULT_WEIGHT - 1;

    /**
     * Get the first service provider matching the lookup with the expectation that there is a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.InjectionException if resolution fails to resolve a match
     */
    default <T> Supplier<T> get(Lookup lookup) {
        return this.<T>first(lookup)
                .orElseThrow(() -> new InjectionException("There are no services matching " + lookup));
    }

    /**
     * Get the first service provider matching the provided type.
     *
     * @param type type of the expected service provider
     * @param <T>  service type or service contract
     * @return the best service provider matching the lookup
     * @throws io.helidon.inject.InjectionException if resolution fails to resolve a match
     */
    default <T> Supplier<T> get(Class<T> type) {
        return this.first(type)
                .orElseThrow(() -> new InjectionException("There are no services with type (or contract) of " + type.getName()));
    }

    /**
     * Find the first service provider matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    <T> Optional<Supplier<T>> first(Lookup lookup);

    /**
     * Find the first service provider matching the provided type with the expectation that there may not be a match available.
     *
     * @param type type of the expected service provider
     * @param <T>  service type or service contract
     * @return the best service provider matching the lookup
     */
    default <T> Optional<Supplier<T>> first(Class<T> type) {
        return first(Lookup.builder()
                            .addContract(type)
                            .build());
    }

    /**
     * Get all service suppliers matching the lookup with the expectation that there may not be a match available.
     *
     * @param type type of the expected service provider
     * @param <T>  type of the expected service suppliers
     * @return list of service suppliers ordered, may be empty if there is no match
     */
    default <T> List<Supplier<T>> all(Class<T> type) {
        return all(Lookup.builder()
                           .addContract(type)
                           .build());
    }

    /**
     * Get all service suppliers matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service suppliers, use {@code Object} if not known, or may contain a mix of types
     * @return list of service suppliers ordered, may be empty if there is no match, cast to the expected type; please use a
     *         {@code Object} as the type if the result may contain unknown provider types (or a mixture of them)
     */
    <T> List<Supplier<T>> all(Lookup lookup);

    /**
     * Find the first service provider matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    default <T> Optional<ServiceProvider<T>> firstProvider(Lookup lookup) {
        return this.<T>allProviders(lookup)
                .stream()
                .findFirst();
    }

    /**
     * Find the first service provider matching the lookup with the expectation that there must be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    default <T> ServiceProvider<T> getProvider(Lookup lookup) {
        return this.<T>firstProvider(lookup)
                .orElseThrow(() -> new InjectionException("There are no services matching " + lookup));
    }

    /**
     * Get all service providers matching the lookup. This is an advanced use case method, use
     * {@link #all(Lookup)} to lookup services in registry.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known, or may contain a mix of types
     * @return list of service providers
     */
    <T> List<ServiceProvider<T>> allProviders(Lookup lookup);

    /**
     * Get a service provider for a descriptor.
     *
     * @param serviceInfo service information (metadata of the service)
     * @param <T>         type of the expected service providers, use {@code Object} if not known, or may contain a mix of types
     * @return service provider created for the descriptor
     * @throws java.util.NoSuchElementException in case the descriptor is not part of this registry
     */
    <T> ServiceProvider<T> serviceProvider(ServiceInfo serviceInfo);

    /**
     * Injection services this instance is managed by.
     *
     * @return injection services
     */
    InjectionServices injectionServices();

    /**
     * Provides a binder for this service registry.
     * Note that by default you can only bind services from {@link io.helidon.inject.service.ModuleComponent} instances that
     * are code generated at build time.
     * <p>
     * This binder is only allowed if you enable dynamic binding. Although this may be tempting, you are breaking the
     * deterministic behavior of the service registry, and may encounter runtime errors that are otherwise impossible.
     *
     * @return service binder that allows binding into this service registry
     */
    ServiceBinder binder();

    /**
     * Limit runtime phase.
     *
     * @return phase to activate to
     * @see io.helidon.inject.InjectionConfig#limitRuntimePhase()
     */
    default Phase limitRuntimePhase() {
        return injectionServices().config().limitRuntimePhase();
    }
}
