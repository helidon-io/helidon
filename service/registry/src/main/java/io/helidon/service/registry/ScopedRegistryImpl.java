/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service.QualifiedInstance;

/**
 * Services for a specific scope.
 * This type is owned by Helidon Service Registry, and cannot be customized.
 * When a scope is properly accessible through its {@link Service.ScopeHandler},
 * {@link #activate()}
 * must be invoked by its control, to make sure all eager services are correctly activated.
 */
class ScopedRegistryImpl implements ScopedRegistry {
    private static final System.Logger LOGGER = System.getLogger(ScopedRegistryImpl.class.getName());
    private static final ThreadLocal<ScopedRegistryImpl> PRE_DESTROY_SCOPE = new ThreadLocal<>();

    private final ReadWriteLock serviceProvidersLock = new ReentrantReadWriteLock();
    private final Map<ServiceInfo, Activator<?>> activators = new IdentityHashMap<>();

    private final TypeName scope;
    private final String id;
    private volatile boolean activationAllowed;
    private RegistryState state = RegistryState.INACTIVE;

    @SuppressWarnings({"rawtypes", "unchecked"})
    ScopedRegistryImpl(CoreServiceRegistry registry,
                       TypeName scope,
                       String id,
                       Map<ServiceDescriptor<?>, Object> initialBindings) {
        this.scope = scope;
        this.id = id;

        for (Map.Entry<ServiceDescriptor<?>, Object> entry : initialBindings.entrySet()) {
            ServiceDescriptor<?> key = entry.getKey();
            ServiceProvider provider = new ServiceProvider<>(registry,
                                                             key
            );
            Object value = entry.getValue();
            Activator<?> fixedService;

            fixedService = scopedActivator(Activators.createActive(provider, value));

            activators.put(key, fixedService);
        }
    }

    /**
     * Activate this scope This method must be called just once,
     * at the time the scope is active and instances can be created within it.
     */
    public void activate() {
        try {
            serviceProvidersLock.writeLock().lock();
            state = RegistryState.ACTIVE;
            activationAllowed = true;
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    @Override
    public void deactivate() {
        List<Activator<?>> toShutdown;
        try {
            serviceProvidersLock.writeLock().lock();
            if (state != RegistryState.ACTIVE) {
                return;
            }

            activationAllowed = false;
            state = RegistryState.DEACTIVATING;
            // Include INIT activators that may already have been handed to a lookup before this snapshot.
            toShutdown = activators.values()
                    .stream()
                    .filter(it -> it.phase() != ActivationPhase.DESTROYED)
                    .sorted(shutdownComparator())
                    .toList();
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }

        // Deactivation may invoke user lifecycle code and wait for activator instance locks. The scope must already
        // reject new activators, and its lock must not be held while that code runs.
        List<Throwable> exceptions = new ArrayList<>();

        try {
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
        } finally {
            try {
                serviceProvidersLock.writeLock().lock();
                state = RegistryState.INACTIVE;
            } finally {
                serviceProvidersLock.writeLock().unlock();
            }
        }

        if (exceptions.isEmpty()) {
            return;
        }
        ServiceRegistryException failure = new ServiceRegistryException("Deactivation failed");
        exceptions.forEach(failure::addSuppressed);
        throw failure;
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
                                                             _ -> scopedActivator(activatorSupplier.get()));
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    <T> Optional<Activator<T>> existingActivator(ServiceInfo descriptor) {
        try {
            serviceProvidersLock.readLock().lock();
            Activator<?> activator = activators.get(descriptor);
            if (activator != null && availableForActiveLookup(activator)) {
                return Optional.of((Activator<T>) activator);
            }
            checkActive();
            return Optional.empty();
        } finally {
            serviceProvidersLock.readLock().unlock();
        }
    }

    boolean activationAllowed() {
        return activationAllowed;
    }

    @SuppressWarnings("unchecked")
    <T> Optional<List<QualifiedInstance<T>>> cleanupInstances(ServiceInfo descriptor, Lookup lookup) {
        if (PRE_DESTROY_SCOPE.get() != this) {
            return Optional.empty();
        }

        Activator<?> activator;
        try {
            serviceProvidersLock.readLock().lock();
            if (state != RegistryState.DEACTIVATING) {
                return Optional.empty();
            }
            activator = activators.get(descriptor);
        } finally {
            serviceProvidersLock.readLock().unlock();
        }

        if (activator instanceof Activators.BaseActivator<?> baseActivator) {
            return ((Activators.BaseActivator<T>) baseActivator).cachedInstances(lookup);
        }
        return Optional.empty();
    }

    void preDestroy(Runnable callback) {
        ScopedRegistryImpl previous = PRE_DESTROY_SCOPE.get();
        PRE_DESTROY_SCOPE.set(this);
        try {
            callback.run();
        } finally {
            if (previous == null) {
                PRE_DESTROY_SCOPE.remove();
            } else {
                PRE_DESTROY_SCOPE.set(previous);
            }
        }
    }

    private static Comparator<? super Activator<?>> shutdownComparator() {
        return Comparator
                .<Activator<?>>comparingDouble(it -> it.descriptor().runLevel().orElse(Service.RunLevel.NORMAL))
                .reversed()
                .thenComparing(it -> it.descriptor().weight());
    }

    private void checkActive() {
        if (state != RegistryState.ACTIVE) {
            throw new ScopeNotActiveException("Injection scope " + scope.fqName() + "[" + id + "] is not active.", scope);
        }
    }

    private Activator<?> scopedActivator(Activator<?> activator) {
        if (activator instanceof Activators.BaseActivator<?> baseActivator) {
            baseActivator.scopedRegistry(this);
        }
        return activator;
    }

    private boolean availableForActiveLookup(Activator<?> activator) {
        return state == RegistryState.ACTIVE
                || (state == RegistryState.DEACTIVATING && activator.phase() == ActivationPhase.ACTIVE);
    }

    private enum RegistryState {
        ACTIVE,
        DEACTIVATING,
        INACTIVE
    }
}
