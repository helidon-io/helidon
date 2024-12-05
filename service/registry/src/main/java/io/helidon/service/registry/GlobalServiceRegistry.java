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

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A global singleton manager for a service registry.
 * <p>
 * Note that when using this registry, testing is a bit more complicated, as the registry is shared
 * statically.
 */
public final class GlobalServiceRegistry {
    private static final AtomicReference<ServiceRegistry> INSTANCE = new AtomicReference<>();
    private static final ReadWriteLock RW_LOCK = new ReentrantReadWriteLock();

    private GlobalServiceRegistry() {
    }

    /**
     * Whether a service registry instance is configured.
     *
     * @return {@code true} if a registry instance was already created
     */
    public static boolean configured() {
        return INSTANCE.get() != null;
    }

    /**
     * Current global service registry, will create a new instance if one is not configured.
     *
     * @return global service registry
     */
    public static ServiceRegistry registry() {
        try {
            RW_LOCK.readLock().lock();
            ServiceRegistry currentInstance = INSTANCE.get();
            if (currentInstance != null) {
                return currentInstance;
            }
        } finally {
            RW_LOCK.readLock().unlock();
        }
        try {
            RW_LOCK.writeLock().lock();
            ServiceRegistryConfig config = ServiceRegistryConfig.create();
            ServiceRegistry newInstance = ServiceRegistryManager.create(config).registry();
            INSTANCE.set(newInstance);
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
        try {
            RW_LOCK.readLock().lock();
            ServiceRegistry currentInstance = INSTANCE.get();
            if (currentInstance != null) {
                return currentInstance;
            }
        } finally {
            RW_LOCK.readLock().unlock();
        }
        try {
            RW_LOCK.writeLock().lock();
            ServiceRegistry newInstance = registrySupplier.get();
            INSTANCE.set(newInstance);
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
        INSTANCE.set(newGlobalRegistry);
        return newGlobalRegistry;
    }

}
