/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * This is a temporary solution used before we migrate all our global singletons to service registry.
 *
 * @deprecated create a {@code io.helidon.service.registry.Service.Provider} to handle a registry service, and
 *              then use {@code io.helidon.service.registry.GlobalServiceRegistry#registry()} to get an instance.
 *              This will work nicely in testing (as global singletons are hard to re-set).
 */
@Deprecated(forRemoval = true, since = "4.2.0")
@SuppressWarnings("unchecked")
public final class GlobalInstances {
    private static final Map<Class<?>, GlobalInstance> BACKING_MAP = new IdentityHashMap<>();
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private GlobalInstances() {
    }
    /**
     * Get current global instance (if specified).
     *
     * @param type type of the instance
     * @return instance if specified, empty otherwise
     * @param <T> type of the instance
     */
    public static <T extends GlobalInstance> Optional<T> current(Class<T> type) {
        LOCK.readLock().lock();
        try {
            return Optional.ofNullable((T) BACKING_MAP.get(type));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * Get current global instance, if it does not exist, set it using the provided supplier.
     *
     * @param type type of the instance
     * @param instanceSupplier supplier of instance to call if there is no global instance registered
     * @return instance that was in the registry, or a new instance created from supplier
     * @param <T> type of the instance
     */
    public static <T extends GlobalInstance> T get(Class<T> type, Supplier<T> instanceSupplier) {
        Optional<T> current = current(type);
        if (current.isPresent()) {
            return current.get();
        }
        LOCK.writeLock().lock();
        try {
            T existing = (T) BACKING_MAP.get(type);
            if (existing == null) {
                existing = instanceSupplier.get();
                BACKING_MAP.put(type, existing);
            }
            return existing;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Register a new global instance.
     *
     * @param type type of the instance
     * @param instance instance to register
     * @return current instance (if any)
     * @param <T> type of the instance
     */
    public static <T extends GlobalInstance> Optional<T> set(Class<T> type, T instance) {
        LOCK.writeLock().lock();
        try {
            return Optional.ofNullable((T) BACKING_MAP.put(type, instance));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Unregister a global instance.
     *
     * @param type type of the instance
     * @return current instance (if any)
     * @param <T> type of the instance
     */
    public static <T extends GlobalInstance> Optional<T> remove(Class<T> type) {
        LOCK.writeLock().lock();
        try {
            return Optional.ofNullable((T) BACKING_MAP.remove(type));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Close all instances.
     * Calls {@link io.helidon.common.GlobalInstances.GlobalInstance#close()} on all registered
     * instances.
     */
    public static void clear() {
        LOCK.writeLock().lock();
        try {
            BACKING_MAP.values().forEach(GlobalInstance::close);
            BACKING_MAP.clear();
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Global instance.
     * Temporary solution until all Helidon global instances get migrated to service registry.
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    public interface GlobalInstance {
        /**
         * Close the instance, and release associated resources.
         */
        void close();
    }
}
