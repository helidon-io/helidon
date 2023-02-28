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

package io.helidon.pico;

import java.util.List;
import java.util.Optional;

/**
 * The service registry. The service registry generally has knowledge about all the services that are available within your
 * application, along with the contracts (i.e., interfaces) they advertise, the qualifiers that optionally describe them, and oll
 * of each services' dependencies on other service contracts, etc.
 *
 * Collectively these service instances are considered "the managed service instances" under Pico. A {@link ServiceProvider} wrapper
 * provides lifecycle management on the underlying service instances that each provider "manages" in terms of activation, scoping,
 * etc. The service providers are typically created during compile-time processing when the Pico APT processor is applied to your
 * module (i.e., any service annotated using {@link jakarta.inject.Singleton},
 * {@link Contract}, {@link jakarta.inject.Inject}, etc.) during compile time. Additionally, they can be built
 * using the Pico maven-plugin. Note also that the maven-plugin can be used to "compute" your applications entire DI model
 * at compile time, generating an {@link Application} class that will be used at startup when found by the
 * Pico framework.
 * <p>
 * This Services interface exposes a read-only set of methods providing access to these "managed service" providers, and available
 * via one of the lookup methods provided. Once you resolve the service provider(s), the service provider can be activated by
 * calling one of its get() methods. This is equivalent to the declarative form just using {@link jakarta.inject.Inject} instead.
 * Note that activation of a service might result in activation chaining. For example, service A injects service B, etc. When
 * service A is activated then service A's dependencies (i.e., injection points) need to be activated as well. To avoid long
 * activation chaining, it is recommended to that users strive to use {@link jakarta.inject.Provider} injection whenever possible.
 * Provider injection (a) breaks long activation chains from occurring by deferring activation until when those services are really
 * needed, and (b) breaks circular references that lead to {@link InjectionException} during activation (i.e.,
 * service A injects B, and service B injects A).
 * <p>
 * The services are ranked according to the provider's comparator. Pico will rank according to a strategy that first looks for
 * {@link io.helidon.common.Weighted}, then {@link jakarta.annotation.Priority}, and finally by the alphabetic ordering according
 * to the type name (package and class canonical name).
 */
public interface Services {

    /**
     * Retrieve the "first" service that implements a given contract type with the expectation that there is a match available.
     *
     * @param type the type to find
     * @param <T> the type of the service
     * @return the best service provider matching the criteria
     * @throws io.helidon.pico.PicoException if resolution fails to resolve a match
     */
    default <T> ServiceProvider<T> lookup(
            Class<T> type) {
        return lookupFirst(type, true).get();
    }

    /**
     * Retrieve the "first" named service that implements a given contract type with the expectation that there is a match
     * available.
     *
     * @param type the type criteria to find
     * @param name the name for the service
     * @param <T> the type of the service
     * @return the best service provider matching the criteria
     * @throws io.helidon.pico.PicoException if resolution fails to resolve a match
     */
    default <T> ServiceProvider<T> lookup(
            Class<T> type,
            String name) {
        return lookupFirst(type, name, true).get();
    }

    /**
     * Retrieve the "first" service that implements a given contract type with no expectation that there is a match available
     * unless {@code expected = true}.
     *
     * @param type the type criteria to find
     * @param expected indicates whether the provider should throw if a match is not found
     * @param <T> the type of the service
     * @return the best service provider matching the criteria, or {@code empty} if (@code expected = false) and no match found
     * @throws io.helidon.pico.PicoException if expected=true and resolution fails to resolve a match
     */
    <T> Optional<ServiceProvider<T>> lookupFirst(
            Class<T> type,
            boolean expected);

    /**
     * Retrieve the "first" service that implements a given contract type with no expectation that there is a match available
     * unless {@code expected = true}.
     *
     * @param type the type criteria to find
     * @param name the name for the service
     * @param expected indicates whether the provider should throw if a match is not found
     * @param <T> the type of the service
     * @return the best service provider matching the criteria, or {@code empty} if (@code expected = false) and no match found
     * @throws io.helidon.pico.PicoException if expected=true and resolution fails to resolve a match
     */
    <T> Optional<ServiceProvider<T>> lookupFirst(
            Class<T> type,
            String name,
            boolean expected);

    /**
     * Retrieves the first match based upon the passed service info criteria.
     *
     * @param criteria the criteria to find
     * @param <T> the type of the service
     * @return the best service provider
     * @throws io.helidon.pico.PicoException if resolution fails to resolve a match
     */
    @SuppressWarnings("unchecked")
    default <T> ServiceProvider<T> lookup(
            ServiceInfoCriteria criteria) {
        return (ServiceProvider<T>) lookupFirst(criteria, true).orElseThrow();
    }

    /**
     * Retrieves the first match based upon the passed service info criteria.
     *
     * @param criteria the criteria to find
     * @param expected indicates whether the provider should throw if a match is not found
     * @param <T> the type of the service
     * @return the best service provider matching the criteria, or {@code empty} if (@code expected = false) and no match found
     * @throws io.helidon.pico.PicoException if expected=true and resolution fails to resolve a match
     */
    <T> Optional<ServiceProvider<T>> lookupFirst(
            ServiceInfoCriteria criteria,
            boolean expected);

    /**
     * Retrieves the first match based upon the passed service info criteria.
     * <p>
     * This is the same as calling the following:
     * <pre>
     *     lookupFirst(criteria, true).orElseThrow();
     * </pre>
     *
     * @param criteria the criteria to find
     * @param <T> the type of the service
     * @return the best service provider matching the criteria
     * @throws io.helidon.pico.PicoException if resolution fails to resolve a match
     */
    @SuppressWarnings("unchecked")
    default <T> ServiceProvider<T> lookupFirst(
            ServiceInfoCriteria criteria) {
        return (ServiceProvider<T>) lookupFirst(criteria, true).orElseThrow();
    }

    /**
     * Retrieves the first match based upon the passed service info criteria.
     * <p>
     * This is the same as calling the following:
     * <pre>
     *     lookupFirst(criteria, true).orElseThrow();
     * </pre>
     *
     * @param type the type criteria to find
     * @param <T> the type of the service
     * @return the best service provider matching the criteria
     * @throws io.helidon.pico.PicoException if resolution fails to resolve a match
     */
    @SuppressWarnings("unchecked")
    default <T> ServiceProvider<T> lookupFirst(
            Class<T> type) {
        return lookupFirst(type, true).orElseThrow();
    }

    /**
     * Retrieve all services that implement a given contract type.
     *
     * @param type the type criteria to find
     * @param <T> the type of the service being managed
     * @return the list of service providers matching criteria
     */
    <T> List<ServiceProvider<T>> lookupAll(
            Class<T> type);

    /**
     * Retrieve all services that match the criteria.
     *
     * @param criteria the criteria to find
     * @param <T> the type of the service
     * @return the list of service providers matching criteria
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default <T> List<ServiceProvider<T>> lookupAll(
            ServiceInfoCriteria criteria) {
        return (List) lookupAll(criteria, false);
    }

    /**
     * Retrieve all services that match the criteria.
     *
     * @param criteria the criteria to find
     * @param expected indicates whether the provider should throw if a match is not found
     * @return the list of service providers matching criteria
     */
    List<ServiceProvider<?>> lookupAll(
            ServiceInfoCriteria criteria,
            boolean expected);

    /**
     * Implementors can provide a means to use a "special" services registry that better applies to the target injection
     * point context to apply for sub-lookup* operations. If the provider does not support contextual lookup then the same
     * services instance as this will be returned.
     *
     * @param ctx the injection point context to use to filter the services to what qualifies for this injection point
     * @return the qualifying services relative to the given context
     * @see PicoServicesConfig#supportsContextualLookup()
     */
    default Services contextualServices(
            InjectionPointInfo ctx) {
        return this;
    }

}
