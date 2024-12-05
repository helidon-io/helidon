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

package io.helidon.service.registry;

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Helidon service registry configuration.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(ServiceRegistryConfigSupport.CustomMethods.class)
interface ServiceRegistryConfigBlueprint {
    /**
     * Whether to discover services from the class path.
     * When set to {@code false}, only services added through {@link #serviceDescriptors()} and/or
     * {@link #serviceInstances()} would be available.
     *
     * @return whether to discover services from classpath, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
    boolean discoverServices();

    /**
     * Whether to discover services from Java service loader.
     * See {@link io.helidon.service.registry.ServiceDiscovery#SERVICES_LOADER_RESOURCE}.
     *
     * @return whether to discover Java {@link java.util.ServiceLoader} services from classpath (a curated list only),
     *         defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
    boolean discoverServicesFromServiceLoader();

    /**
     * Whether to allow binding via methods, such as {@link io.helidon.service.registry.Services#set(Class, Object[])}.
     * When disabled, attempts at late binding will throw an exception.
     *
     * @return whether late binding is enabled, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
    boolean allowLateBinding();

    /**
     * Manually registered service descriptors to add to the registry.
     * This is useful when {@link #discoverServices()} is set to {@code false}, to register only hand-picked services
     * into the registry.
     * <p>
     * Even when service discovery is used, this can be used to add service descriptors that are not part of
     * a service discovery mechanism (such as testing services).
     *
     * @return services to register
     */
    @Option.Singular
    List<ServiceDescriptor<?>> serviceDescriptors();

    /**
     * Manually register initial bindings for some of the services in the registry.
     *
     * @return service instances to register
     */
    @Option.Singular
    @Option.SameGeneric
    Map<ServiceDescriptor<?>, Object> serviceInstances();

    /**
     * Flag indicating whether service lookups
     * (i.e., via {@link io.helidon.service.registry.ServiceRegistry#first(io.helidon.service.registry.Lookup)}) are cached.
     *
     * @return the flag indicating whether service lookups are cached, defaults to {@code false}
     */
    boolean lookupCacheEnabled();

    /**
     * Size of the lookup cache when {@link #lookupCacheEnabled()} is set to {@code true}.
     *
     * @return cache size
     */
    @Option.DefaultInt(10000)
    int lookupCacheSize();

    /**
     * Flag indicating whether runtime interception is enabled.
     * If set to {@code false}, methods will be invoked without any interceptors, even if interceptors are available.
     *
     * @return whether to intercept calls at runtime, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
    boolean interceptionEnabled();

    /**
     * In certain conditions Injection services should be initialized but not started (i.e., avoiding calls to
     * {@code PostConstruct}
     * etc.). This can be used in special cases where the normal Injection startup should limit lifecycle up to a given phase.
     *
     * @return the phase to stop at during lifecycle
     */
    @Option.Default("ACTIVE")
    ActivationPhase limitActivationPhase();

    /**
     * Flag indicating whether compile-time generated {@link io.helidon.service.registry.Binding}'s
     * should be used at initialization.
     * Even if set to {@code true}, this is effective only if an {@link io.helidon.service.registry.Binding}
     * was generated using Helidon Service Maven Plugin.
     *
     * @return the flag indicating whether the provider is permitted to use binding generated code from compile-time,
     *         defaults to {@code true}
     * @see io.helidon.service.registry.Binding
     */
    @Option.DefaultBoolean(true)
    boolean useBinding();
}
