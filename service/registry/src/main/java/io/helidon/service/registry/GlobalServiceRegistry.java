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

import java.util.function.Supplier;

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.context.ContextSingleton;

/**
 * A global singleton manager for a service registry.
 */
public final class GlobalServiceRegistry {
    private static final ContextSingleton<ServiceRegistry> CONTEXT_VALUE = ContextSingleton.create(
            GlobalServiceRegistry.class,
            ServiceRegistry.class, () -> {
                ServiceRegistryConfig config;
                if (GlobalConfig.configured()) {
                    config = ServiceRegistryConfig.create(GlobalConfig.config().get("registry"));
                } else {
                    config = ServiceRegistryConfig.create();
                }
                return ServiceRegistryManager.create(config).registry();
            });

    private GlobalServiceRegistry() {
    }

    /**
     * Whether a service registry instance is configured.
     *
     * @return {@code true} if a registry instance was already created
     */
    public static boolean configured() {
        return CONTEXT_VALUE.isPresent();
    }

    /**
     * Current global service registry, will create a new instance if one is not configured.
     *
     * @return global service registry
     */
    public static ServiceRegistry registry() {
        return CONTEXT_VALUE.get();
    }

    /**
     * Current global registry if configured, will replace the current global registry with the
     * one provided by supplier if none registered.
     *
     * @param registrySupplier supplier of new global registry if not yet configured
     * @return global service registry
     */
    public static ServiceRegistry registry(Supplier<ServiceRegistry> registrySupplier) {
        return CONTEXT_VALUE.get(registrySupplier);
    }

    /**
     * Set the current global registry.
     * This method always returns the same instance as provided, though the next call to
     * {@link #registry()} may return a different instance if the instance is replaced by another thread.
     *
     * @param newGlobalRegistry global registry to set
     * @return the same instance
     */
    public static ServiceRegistry registry(ServiceRegistry newGlobalRegistry) {
        CONTEXT_VALUE.set(newGlobalRegistry);
        return newGlobalRegistry;
    }
}
