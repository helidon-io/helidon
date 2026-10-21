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
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
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

    private final ReadWriteLock serviceProvidersLock = new ReentrantReadWriteLock();
    private final Map<ServiceInfo, Activator<?>> activators = new IdentityHashMap<>();
    private final Map<ServiceInfo, Set<ServiceInfo>> injectedDependencies = new IdentityHashMap<>();

    private final TypeName scope;
    private final String id;
    private boolean active = false;

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

            fixedService = Activators.createActive(provider, value);

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

            List<Activator<?>> toShutdown = shutdownOrder(activators.values()
                    .stream()
                    .filter(it -> it.phase().eligibleForDeactivation())
                    .toList());

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

    @SuppressWarnings("unchecked")
    <T> Optional<List<QualifiedInstance<T>>> instances(ServiceInfo descriptor,
                                                    Supplier<Activator<T>> activatorSupplier,
                                                    Lookup lookup,
                                                    ServiceInfo dependent) {
        Activator<T> activator;
        boolean dependencyRecorded;
        try {
            serviceProvidersLock.readLock().lock();
            checkActive();
            activator = (Activator<T>) activators.get(descriptor);
            dependencyRecorded = injectedDependencies.getOrDefault(dependent, Set.of()).contains(descriptor);
        } finally {
            serviceProvidersLock.readLock().unlock();
        }

        if (activator == null) {
            activator = activator(descriptor, activatorSupplier);
        }
        Optional<List<QualifiedInstance<T>>> instances = activator.instances(lookup);
        // An existing edge will be included in the shutdown snapshot; only new edges need recording after resolution.
        if (!dependencyRecorded && instances.map(it -> !it.isEmpty()).orElse(false)) {
            dependency(descriptor, dependent);
        }
        return instances;
    }

    void dependency(ServiceInfo descriptor, ServiceInfo dependent) {
        try {
            serviceProvidersLock.writeLock().lock();
            checkActive();
            // Keep the owning descriptor, since inherited injection points can resolve differently for each subclass.
            injectedDependencies.computeIfAbsent(dependent, key -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(descriptor);
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    <T> Optional<Activator<T>> existingActivator(ServiceInfo descriptor) {
        try {
            serviceProvidersLock.readLock().lock();
            checkActive();
            return Optional.ofNullable((Activator<T>) activators.get(descriptor));
        } finally {
            serviceProvidersLock.readLock().unlock();
        }
    }

    private static Comparator<? super Activator<?>> shutdownComparator() {
        return Comparator
                .<Activator<?>>comparingDouble(it -> it.descriptor().runLevel().orElse(Service.RunLevel.NORMAL))
                .reversed()
                .thenComparing(it -> it.descriptor().weight());
    }

    private List<Activator<?>> shutdownOrder(List<Activator<?>> services) {
        Map<Activator<?>, Set<Activator<?>>> dependencies = new IdentityHashMap<>();
        Map<Activator<?>, Integer> dependents = new IdentityHashMap<>();
        services.forEach(it -> dependents.put(it, 0));
        for (Activator<?> service : services) {
            Set<Activator<?>> targets = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ServiceInfo descriptor : injectedDependencies.getOrDefault(service.descriptor(), Set.of())) {
                Activator<?> target = activators.get(descriptor);
                if (target != service && dependents.containsKey(target) && targets.add(target)) {
                    dependents.compute(target, (key, count) -> count + 1);
                }
            }
            dependencies.put(service, targets);
        }

        // Preserve run-level and weight ordering among services whose dependents have already shut down.
        var ready = new PriorityQueue<Activator<?>>(shutdownComparator());
        dependents.forEach((service, count) -> {
            if (count == 0) {
                ready.add(service);
            }
        });
        List<Activator<?>> result = new ArrayList<>(services.size());
        while (!ready.isEmpty()) {
            Activator<?> service = ready.remove();
            result.add(service);
            dependents.remove(service);
            for (Activator<?> dependency : dependencies.get(service)) {
                if (dependents.compute(dependency, (key, count) -> count - 1) == 0) {
                    ready.add(dependency);
                }
            }
        }
        if (!dependents.isEmpty()) {
            // A cyclic graph cannot satisfy dependency ordering; still attempt cleanup of every remaining service.
            LOGGER.log(Level.WARNING, "[" + id + "] Cyclic service dependencies during shutdown: " + dependents.keySet());
            dependents.keySet().stream().sorted(shutdownComparator()).forEach(result::add);
        }
        return result;
    }

    private void checkActive() {
        if (!active) {
            throw new ScopeNotActiveException("Injection scope " + scope.fqName() + "[" + id + "] is not active.", scope);
        }
    }
}
