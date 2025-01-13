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
 * Represents an application wide service registry.
 * <p>
 * The registry instance is shared through {@link io.helidon.common.context.Context}, using the current context
 * to obtain a context with classifier {@link #STATIC_CONTEXT_CLASSIFIER}. If such a context is found, the
 * registry instance is obtained/stored there. If the context does not exist, the
 * {@link io.helidon.common.context.Contexts#globalContext()} is used to store the value.
 * <p>
 * The first option is designed for testing, to make sure the global registry is only "global" for a single test class.
 * The second option is the default for application runtime, where we intend to share the registry as a proper, static
 * singleton instance.
 * <p>
 * Helidon testing libraries support this approach and correctly configure appropriate context for each execution.
 */
public final class GlobalServiceRegistry {
    /**
     * Classifier used to register a context that is to serve as the context that holds the
     * global registry instance.
     * <p>
     * This is to allow testing in parallel, where we need the global registry instance restricted to a single test.
     * <p>
     * In normal application runtime we use {@link io.helidon.common.context.Contexts#globalContext()}.
     */
    public static final String STATIC_CONTEXT_CLASSIFIER = "helidon-registry-static-context";

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
            context().register(ContextQualifier.INSTANCE, newGlobalRegistry);
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
                .get(STATIC_CONTEXT_CLASSIFIER, Context.class)
                .orElse(globalContext);
    }

    private static Optional<ServiceRegistry> current() {
        RW_LOCK.readLock().lock();
        try {
            return context().get(ContextQualifier.INSTANCE, ServiceRegistry.class);
        } finally {
            RW_LOCK.readLock().unlock();
        }
    }

    private static final class ContextQualifier {
        private static final ContextQualifier INSTANCE = new ContextQualifier();

        private ContextQualifier() {
        }
    }
}
