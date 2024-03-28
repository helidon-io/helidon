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

package io.helidon.service.inject.api;

import java.util.function.Supplier;

import io.helidon.service.registry.ServiceInfo;

/**
 * Service registry of a specific scope.
 */
public interface ScopedRegistry {
    /**
     * Activate this registry instance. This method will prepare this registry for use.
     */
    void activate();

    /**
     * Deactivate this registry instance. This method will deactivate all active instances
     *
     * @throws io.helidon.service.registry.ServiceRegistryException in case one or more services failed to deactivate
     */
    void deactivate();

    /**
     * Provides either an existing activator, if one is already available in this scope, or adds a new activator instance.
     *
     * @param descriptor        service descriptor
     * @param activatorSupplier supplier of new activators to manage service instances
     * @param <T>               type of the instances supported by the descriptor
     * @return activator for the service, either an existing one, or a new one created from the supplier
     */
    <T> Activator<T> activator(ServiceInfo descriptor,
                               Supplier<Activator<T>> activatorSupplier);
}
