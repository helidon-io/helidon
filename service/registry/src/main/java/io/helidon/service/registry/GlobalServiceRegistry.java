/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

/**
 * Application wide service registry backed by {@link io.helidon.common.context.Context}.
 */
public final class GlobalServiceRegistry {

    private static final ReadWriteLock RW_LOCK = new ReentrantReadWriteLock();

    private GlobalServiceRegistry() {
    }

    /**
     * Whether a service registry instance is configured.
     *
     * @return {@code true} if a registry instance was already created
     */
    public static boolean configured() {
        return current().isPresent();
    }

    /**
     * Current global service registry, will create a new instance if one is not configured.
     *
     * @return global service registry
     */
    public static ServiceRegistry registry() {
        var current = current();
        if (current.isPresent()) {
            return current.get();
        }

        RW_LOCK.writeLock().lock();
        try {
            current = current();
            if (current.isPresent()) {
                return current.get();
            }
            ServiceRegistryConfig config = ServiceRegistryConfig.create();
            ServiceRegistry newInstance = ServiceRegistryManager.create(config).registry();
            registry(newInstance);
            return newInstance;
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    /**
     * Current global registry if configured, will replace the current global registry with the
     * one provided by supplier if none registered.
     *
     * @param registrySupplier supplier of new global registry if not yet configured
     * @return global service registry
     */
    public static ServiceRegistry registry(Supplier<ServiceRegistry> registrySupplier) {
        var current = current();
        if (current.isPresent()) {
            return current.get();
        }

        RW_LOCK.writeLock().lock();
        try {
            current = current();
            if (current.isPresent()) {
                return current.get();
            }
            ServiceRegistry newInstance = registrySupplier.get();
            if (newInstance == null) {
                throw new ServiceRegistryException("Global registry supplier cannot return null.");
            }
            registry(newInstance);
            return newInstance;
        } finally {
            RW_LOCK.writeLock().unlock();
        }
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
        RW_LOCK.writeLock().lock();
        try {
            context().register("helidon-registry", newGlobalRegistry);
        } finally {
            RW_LOCK.writeLock().unlock();
        }
        return newGlobalRegistry;
    }

    private static Context context() {
        Context globalContext = Contexts.globalContext();

        // this is the context we expect to get (and set global instances)
        return Contexts.context()
                .orElse(globalContext)
                .get("helidon-registry-static-context", Context.class)
                .orElse(globalContext);
    }

    private static Optional<ServiceRegistry> current() {
        RW_LOCK.readLock().lock();
        try {
            return context().get("helidon-registry", ServiceRegistry.class);
        } finally {
            RW_LOCK.readLock().unlock();
        }
    }
}
