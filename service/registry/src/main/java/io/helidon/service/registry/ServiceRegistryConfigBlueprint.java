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
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

/**
 * Helidon service registry configuration.
 */
@SuppressWarnings("removal")
@Prototype.Blueprint
@Prototype.Configured("registry")
@Prototype.CustomMethods(ServiceRegistryConfigSupport.CustomMethods.class)
interface ServiceRegistryConfigBlueprint {
    /**
     * Whether to discover services from the class path.
     * When set to {@code false}, only services added through {@link #serviceDescriptors()} and/or
     * {@link #serviceInstances()} would be available.
     *
     * @return whether to discover services from classpath, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean discoverServices();

    /**
     * Whether to discover services from Java service loader.
     * See {@link io.helidon.service.registry.ServiceDiscovery#SERVICES_LOADER_RESOURCE}.
     *
     * @return whether to discover Java {@link java.util.ServiceLoader} services from classpath (a curated list only),
     *         defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean discoverServicesFromServiceLoader();

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
    List<GeneratedService.Descriptor<?>> serviceDescriptors();

    /**
     * Manually register initial bindings for some of the services in the registry.
     *
     * @return service instances to register
     */
    @Option.Singular
    @Option.SameGeneric
    Map<GeneratedService.Descriptor<?>, Object> serviceInstances();

    /**
     * Config instance used to configure this registry configuration.
     * DO NOT USE for application configuration!
     *
     * @return config node used to configure this service registry config instance (if any)
     */
    Optional<Config> config();
}
