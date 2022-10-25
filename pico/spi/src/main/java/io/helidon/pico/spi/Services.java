/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi;

import java.util.List;

/**
 * The service registry. The service registry generally has knowledge about all the services that are available within your
 * application, along with the contracts (i.e., interfaces) they advertise, the qualifiers that optionally describe them, and oll
 * of each services' dependencies on other service contracts, etc.
 *
 * Collectively these service instances are considered "the managed service instances" under Pico. A {@link ServiceProvider} wrapper
 * provides lifecycle management on the underlying service instances that each provider "manages" in terms of activation, scoping,
 * etc. The service providers are typically created during compile-time processing when the Pico APT processor is applied to your
 * module (i.e., any service annotated using {@link jakarta.inject.Singleton},
 * {@link io.helidon.pico.api.Contract}, {@link jakarta.inject.Inject}, etc.) during compile time. Additionally, they can be built
 * using the Pico maven-plugin. Note also that the maven-plugin can be used to "compute" your applications entire DI model
 * at compile time, generating an {@link io.helidon.pico.spi.Application} class that will be used at startup when found by the
 * Pico framework.
 * <p>
 * This Services interface exposes a read-only set of methods providing access to these "managed service" providers, and available
 * via one of the lookup methods provided. Once you resolve the service provider(s), the service provider can be activated by
 * calling one of its get() methods. This is equivalent to the declarative form just using {@link jakarta.inject.Inject} instead.
 * Note that activation of a service might result in activation chaining. For example, service A injects service B, etc. When
 * service A is activated then service A's dependencies (i.e., injection points) need to be activated as well. To avoid long
 * activation chaining, it is recommended to that users strive to use {@link jakarta.inject.Provider} injection whenever possible.
 * Provider injection (a) breaks long activation chains from occurring by deferring activation until when those services are really
 * needed, and (b) breaks circular references that lead to {@link io.helidon.pico.spi.InjectionException} during activation (i.e.,
 * service A injects B, and service B injects A).
 */
public interface Services {

    /**
     * Retrieve the "first" (i.e., highest {@link io.helidon.common.Weighted} or {@link jakarta.annotation.Priority} service
     * that implements a given contract type with the expectation that there is an implementation available.
     *
     * @param type the criteria to find
     * @param <T> the type of the service
     * @return the best service provider
     */
    default <T> ServiceProvider<T> lookupFirst(Class<T> type) {
        return lookupFirst(type, true);
    }

    /**
     * Retrieve the "first" service that implements a given contract type, throwing
     * an PlatformRuntimeException if the service was not found and it is/was expected.
     * <p>
     * Note that this will retrieve only unnamed service contracts. When looking up
     * singleton service implementations (not contracts) this method will still work without
     * needing to pass the name.
     *
     * @param type the criteria to find
     * @param expected indicates whether the provider should throw if a match is not found
     * @param <T> the type of the service
     * @return the best service provider
     */
    default <T> ServiceProvider<T> lookupFirst(Class<T> type, boolean expected) {
        return lookupFirst(type, null, expected);
    }

    /**
     * Retrieves the named services.
     *
     * @param type the criteria to find
     * @param name the name for the service
     * @param <T> the type of the service
     * @return the best service provider
     */
    default <T> ServiceProvider<T> lookupFirst(Class<T> type, String name) {
        return lookupFirst(type, name, true);
    }

    /**
     * Retrieves the first based upon criteria.
     *
     * @param type the criteria to find
     * @param name the name for the service
     * @param expected indicates whether the provider should throw if a match is not found
     * @param <T> the type of the service
     * @return the best service provider
     */
    <T> ServiceProvider<T> lookupFirst(Class<T> type, String name, boolean expected);

    /**
     * Retrieves the first based upon criteria.
     *
     * @param serviceInfo the criteria to find
     * @param <T> the type of the service
     * @return the best service provider
     */
    default <T> ServiceProvider<T> lookupFirst(ServiceInfo serviceInfo) {
        return lookupFirst(serviceInfo, true);
    }

    /**
     * Retrieves the first based upon criteria.
     *
     * @param criteria the criteria to find
     * @param expected indicates whether the provider should throw if a match is not found
     * @param <T> the type of the service
     * @return the best service provider
     */
    <T> ServiceProvider<T> lookupFirst(ServiceInfo criteria, boolean expected);

    /**
     * Retrieve all services that implement a given contract type.
     * <p>
     * Note that this will retrieve named and unnamed services in {@link jakarta.annotation.Priority} order, or empty list.
     *
     * @param type the criteria to find
     * @param <T> the type of the service
     * @return the list of service providers matching criteria
     */
    <T> List<ServiceProvider<T>> lookup(Class<T> type);

    /**
     * Retrieve all services that match the criteria.
     * <p>
     * The default implementation simply invokes {@link #lookup(ServiceInfo, boolean)}.
     *
     * @param criteria the criteria to find
     * @param <T> the type of the service
     * @return the list of service providers matching criteria
     */
    <T> List<ServiceProvider<T>> lookup(ServiceInfo criteria);

    /**
     * Retrieve all services that match the criteria.
     * <p>
     * Note 1: that this will retrieve named and unnamed services in {@link jakarta.annotation.Priority} order.
     * Note 2: if {@link ServiceInfo#weight()} is provided as non-null to the criteria, then only services with a lesser
     * weight value will be returned.
     *
     * @param criteria the criteria to find
     * @param <T> the type of the service
     * @param expected indicates whether the provider should throw if a match is not found
     *
     * @return the list of service providers matching criteria
     */
    <T> List<ServiceProvider<T>> lookup(ServiceInfo criteria, boolean expected);

    /**
     * Implementors can provide a means to use a "special" services registry that better applies to the target injection
     * point context.
     * <p>
     * The default reference implementation does not implement anything special here.
     *
     * @param ignoredCtx    the injection point context to use to filter the services to what qualifies for this injection point
     *
     * @return the qualifying services relative to the given context
     */
    default Services contextualServices(InjectionPointInfo ignoredCtx) {
        return this;
    }

}
