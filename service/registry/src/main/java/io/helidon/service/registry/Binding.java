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

import io.helidon.common.types.TypeName;

/**
 * A binding instance, if available at runtime, will be expected to provide a plan for all service provider's injection
 * points.
 * <p>
 * Implementations of this contract are normally code generated, although then can be programmatically written by the developer
 * for special cases.
 * <p>
 * Binding instances MUST NOT have injection points.
 */
public interface Binding {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(Binding.class);

    /**
     * Name of this application binding.
     *
     * @return binding name
     */
    String name();

    /**
     * For each service in this application, bind services that satisfy its injection points.
     *
     * @param binder the binder used to register the service provider dependency injection plan
     */
    void binding(DependencyPlanBinder binder);

    /**
     * Register all services with the configuration.
     * When application binding is available to the registry, automatic discovery of services is disabled, and only services
     * registered in this method will be used by the registry.
     * <p>
     * The services registered in this method must be aligned with {@link #binding(DependencyPlanBinder)}, as otherwise
     * inconsistent registry would be created.
     *
     * @param builder configuration builder to register service descriptors
     */
    void configure(ServiceRegistryConfig.Builder builder);
}
