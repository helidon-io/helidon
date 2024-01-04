/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ServiceBinder;

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
    TypeName TYPE_NAME = TypeName.create(Services.class);
    /**
     * Default weight used by Helidon Injection components.
     * It is lower than the default, so it is easy to override service with custom providers.
     */
    double INJECT_WEIGHT = Weighted.DEFAULT_WEIGHT - 1;

    /**
     * Get the first service instance matching the lookup with the expectation that there is a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service, use {@code Object} if not known
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.InjectionException if there is no service that could satisfy the lookup, or the resolution to
     *                                              instance failed
     */
    default <T> T get(Lookup lookup) {
        return this.<T>supply(lookup).get();
    }

    default <T> T get(Class<T> type) {
        return this.get(Lookup.create(type));
    }

    default <T> Optional<T> first(Lookup lookup) {
        return this.<T>supplyFirst(lookup).get();
    }

    default <T> Optional<T> first(Class<T> type) {
        return this.first(Lookup.create(type));
    }

    default <T> List<T> all(Lookup lookup) {
        return this.<T>supplyAll(lookup).get();
    }

    default <T> List<T> all(Class<T> type) {
        return this.all(Lookup.create(type));
    }

    /**
     * Get the first service provider matching the lookup with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.inject.InjectionException} in case the matching service cannot provide a value (either because
     * of scope mismatch, or because there is no available instance, and we use a runtime resolution through
     * {@link io.helidon.inject.ServiceProviderProvider}, {@link io.helidon.inject.service.InjectionPointProvider}, or similar).
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.InjectionException if there is no service that could satisfy the lookup
     */
    <T> Supplier<T> supply(Lookup lookup);

    default <T> Supplier<T> supply(Class<T> type) {
        return this.supply(Lookup.create(type));
    }

    /**
     * Find the first service provider matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    <T> Supplier<Optional<T>> supplyFirst(Lookup lookup);

    default <T> Supplier<Optional<T>> supplyFirst(Class<T> type) {
        return this.supplyFirst(Lookup.create(type));
    }

    /**
     * Supply all services matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service suppliers
     * @return supplier of list of services ordered, may be empty if there is no match
     */
    <T> Supplier<List<T>> supplyAll(Lookup lookup);

    default <T> Supplier<List<T>> supplyAll(Class<T> type) {
        return this.supplyAll(Lookup.create(type));
    }

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
