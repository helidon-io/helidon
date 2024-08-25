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

/**
 * A global singleton manager for a service registry.
 * <p>
 * Note that when using this registry, testing is a bit more complicated, as the registry is shared
 * statically. You need to re-set it after tests (this is done automatically when using Helidon testing annotations).
 */
@SuppressWarnings("removal")
public final class GlobalServiceRegistry {
    private GlobalServiceRegistry() {
    }

    /**
     * Whether a service registry instance is configured.
     *
     * @return {@code true} if a registry instance was already created
     */
    public static boolean configured() {
        return io.helidon.common.GlobalInstances.current(ServiceRegistryHolder.class)
                .isPresent();
    }

    /**
     * Current global service registry, will create a new instance if one is not configured.
     *
     * @return global service registry
     */
    public static ServiceRegistry registry() {
        return io.helidon.common.GlobalInstances.get(ServiceRegistryHolder.class, () -> {
                    ServiceRegistryConfig config;
                    if (GlobalConfig.configured()) {
                        config = ServiceRegistryConfig.create(GlobalConfig.config().get("registry"));
                    } else {
                        config = ServiceRegistryConfig.create();
                    }
                    ServiceRegistryManager serviceRegistryManager = ServiceRegistryManager.create(config);
                    ServiceRegistry newInstance = serviceRegistryManager.registry();
                    return new ServiceRegistryHolder(serviceRegistryManager, newInstance);
                })
                .registry();
    }

    /**
     * Current global registry if configured, will replace the current global registry with the
     * one provided by supplier if none registered.
     *
     * @param registrySupplier supplier of new global registry if not yet configured
     * @return global service registry
     */
    public static ServiceRegistry registry(Supplier<ServiceRegistry> registrySupplier) {
        return io.helidon.common.GlobalInstances.get(ServiceRegistryHolder.class,
                                                     () -> new ServiceRegistryHolder(registrySupplier.get()))
                .registry();
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
        io.helidon.common.GlobalInstances.set(ServiceRegistryHolder.class, new ServiceRegistryHolder(newGlobalRegistry));
        return newGlobalRegistry;
    }

    private record ServiceRegistryHolder(ServiceRegistryManager manager, ServiceRegistry registry)
            implements io.helidon.common.GlobalInstances.GlobalInstance {
        ServiceRegistryHolder(ServiceRegistry registry) {
            this(null, registry);
        }

        @Override
        public void close() {
            if (manager != null) {
                manager.shutdown();
            }
        }
    }
}
