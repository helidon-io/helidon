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

package io.helidon.service.inject;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.ActivationResult;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.ScopedRegistry;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Services for a specific scope.
 * This type is owned by Helidon Injection, and cannot be customized.
 * When a scope is properly accessible through its {@link io.helidon.service.inject.api.Injection.ScopeHandler},
 * {@link #activate()}
 * must be invoked by its control, to make sure all eager services are correctly activated.
 */
class ScopedRegistryImpl implements ScopedRegistry {
    private static final System.Logger LOGGER = System.getLogger(ScopedRegistryImpl.class.getName());

    private final ReadWriteLock serviceProvidersLock = new ReentrantReadWriteLock();
    private final Map<ServiceInfo, Activator<?>> activators = new IdentityHashMap<>();

    private final TypeName scope;
    private final String id;
    private boolean active = false;

    @SuppressWarnings({"rawtypes", "unchecked"})
    ScopedRegistryImpl(InjectServiceRegistryImpl registry,
                       TypeName scope,
                       String id,
                       Map<ServiceInfo, Object> initialBindings) {
        this.scope = scope;
        this.id = id;

        for (Map.Entry<ServiceInfo, Object> entry : initialBindings.entrySet()) {
            ServiceInfo key = entry.getKey();
            ServiceProvider provider = new ServiceProvider<>(registry,
                                                             key
            );
            Object value = entry.getValue();
            Activator<?> fixedService;

            fixedService = Activators.create(provider, value);

            activators.put(key, fixedService);
        }
    }

    /**
     * Activate this scope This method must be called just once,
     * at the time the scope is active and instances can be created within it.
     */
    public void activate() {
        active = true;
    }

    @Override
    public void deactivate() {
        try {
            serviceProvidersLock.writeLock().lock();
            if (!active) {
                return;
            }

            List<Activator<?>> toShutdown = activators.values()
                    .stream()
                    .filter(it -> it.phase().eligibleForDeactivation())
                    .sorted(shutdownComparator())
                    .toList();

            List<Throwable> exceptions = new ArrayList<>();

            for (Activator<?> managedService : toShutdown) {
                try {
                    ActivationResult activationResult = managedService.deactivate();
                    if (activationResult.failure() && LOGGER.isLoggable(Level.DEBUG)) {
                        if (activationResult.error().isPresent()) {
                            LOGGER.log(Level.DEBUG,
                                       "[" + id + "] Failed to deactivate " + managedService.description(),
                                       activationResult.error().get());
                            exceptions.add(activationResult.error().get());
                        } else {
                            LOGGER.log(Level.DEBUG,
                                       "[" + id + "] Failed to deactivate " + managedService.description());
                            exceptions.add(new ServiceRegistryException("Failed to deactivate " + managedService.description()
                                                                                + ", no exception received."));
                        }
                    }
                } catch (Exception e) {
                    if (LOGGER.isLoggable(Level.DEBUG)) {
                        LOGGER.log(Level.DEBUG, "[" + id + "] Failed to deactivate service provider: " + managedService, e);
                    }
                    exceptions.add(new ServiceRegistryException("Failed to deactivate " + managedService.description(), e));
                }
            }

            active = false;

            if (exceptions.isEmpty()) {
                return;
            }
            ServiceRegistryException failure = new ServiceRegistryException("Deactivation failed");
            exceptions.forEach(failure::addSuppressed);
            throw failure;
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Activator<T> activator(ServiceInfo descriptor, Supplier<Activator<T>> activatorSupplier) {
        try {
            serviceProvidersLock.readLock().lock();
            checkActive();
            Activator<?> activator = activators.get(descriptor);
            if (activator != null) {
                return (Activator<T>) activator;
            }
        } finally {
            serviceProvidersLock.readLock().unlock();
        }

        // failed to get instance, now let's obtain a write lock and do it again
        try {
            serviceProvidersLock.writeLock().lock();
            checkActive();
            return (Activator<T>) activators.computeIfAbsent(descriptor,
                                                             desc -> activatorSupplier.get());
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    private static Comparator<? super Activator<?>> shutdownComparator() {
        return Comparator
                .<Activator<?>>comparingDouble(it -> it.descriptor().runLevel().orElse(Injection.RunLevel.NORMAL))
                .thenComparing(it -> it.descriptor().weight());
    }

    private void checkActive() {
        if (!active) {
            throw new ServiceRegistryException("Injection scope " + scope.fqName() + "[" + id + "] is not active.");
        }
    }
}
