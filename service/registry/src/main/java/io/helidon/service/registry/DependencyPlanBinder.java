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

/**
 * Responsible for registering the injection plan to the services in the service registry.
 * <p>
 * IMPORTANT: all methods must be called with {@link io.helidon.service.registry.ServiceDescriptor} singleton
 * instances for {@link io.helidon.service.registry.ServiceInfo} parameter, as the registry depends on instance
 * equality. All generated code is done this way.
 */
public interface DependencyPlanBinder {

    /**
     * Bind an injection plan to a service provider instance.
     *
     * @param descriptor the service to receive the injection plan.
     * @return the binder to use for binding the injection plan to the service provider
     */
    Binder service(ServiceInfo descriptor);

    /**
     * Bind all discovered interceptors.
     *
     * @param descriptors interceptor services
     */
    void interceptors(ServiceInfo... descriptors);

    /**
     * The binder builder for the service plan.
     * The caller must be aware of cardinality and type (whether to inject {@link java.util.function.Supplier} or instance)
     * of injections.
     *
     * @see io.helidon.service.registry.Dependency
     */
    interface Binder {

        /**
         * Binds a single service to the injection point identified by the id.
         * The injection point expects a single service instance.
         *
         * @param dependency  the injection point identity
         * @param descriptor  the service descriptor to bind to this identity
         * @return the binder builder
         */
        Binder bind(Dependency dependency,
                    ServiceInfo... descriptor);

    }

}
