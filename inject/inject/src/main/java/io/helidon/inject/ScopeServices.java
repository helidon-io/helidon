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

package io.helidon.inject;

import java.lang.System.Logger.Level;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

/**
 * Services for a specific scope.
 * This type is owned by Helidon Injection, and cannot be customized.
 * When a scope is properly accessible through its {@link io.helidon.inject.ScopeHandler}, {@link #activate()}
 * must be invoked by its control, to make sure all eager services are correctly activated.
 */
public class ScopeServices {
    private final ReadWriteLock serviceProvidersLock = new ReentrantReadWriteLock();
    private final Map<ServiceInfo, ManagedService<?>> serviceProviders = new IdentityHashMap<>();

    private final System.Logger logger;
    private final TypeName scope;
    private final String id;
    private final List<ServiceManager<?>> eagerServices;
    private boolean active = true;

    @SuppressWarnings({"rawtypes", "unchecked"})
    ScopeServices(Services services,
                  TypeName scope,
                  String id, List<ServiceManager<?>> eagerServices,
                  Map<ServiceDescriptor<?>, Object> initialBindings) {
        this.logger = System.getLogger(ScopeServices.class.getName() + "." + scope.className());
        this.scope = scope;
        this.id = id;
        this.eagerServices = eagerServices;

        initialBindings.forEach((descriptor, value) -> {
            ServiceProvider provider = new ServiceProvider<>(services,
                                                             descriptor);
            ManagedService<?> fixedService = ManagedService.create(provider, value);
            serviceProviders.put(descriptor, fixedService);
        });
    }

    /**
     * Activate this scope This method must be called just once,
     * at the time the scope is active and instances can be created within it.
     */
    public void activate() {
        eagerServices.forEach(ServiceManager::activate);
    }

    /**
     * Close this services instance.
     * The returned map contains a map of all service types to a de-activation result that was done.
     * Note that only services that were in a state that supports de-activation are listed.
     *
     * @return map of de-activation results
     */
    public Map<TypeName, ActivationResult> close() {
        try {
            serviceProvidersLock.writeLock().lock();
            if (!active) {
                return Map.of();
            }

            List<ManagedService<?>> toShutdown = serviceProviders.values()
                    .stream()
                    .filter(it -> it.phase().eligibleForDeactivation())
                    .sorted(shutdownComparator())
                    .toList();

            Map<TypeName, ActivationResult> result = new LinkedHashMap<>();
            DeActivationRequest request = DeActivationRequest.builder()
                    .throwIfError(false)
                    .build();
            for (ManagedService<?> managedService : toShutdown) {
                Phase startingActivationPhase = managedService.phase();
                try {
                    ActivationResult activationResult = managedService.deactivate(request);
                    if (activationResult.failure() && logger.isLoggable(Level.DEBUG)) {
                        if (activationResult.error().isPresent()) {
                            logger.log(Level.DEBUG,
                                       "[" + id + "] Failed to deactivate " + managedService.description(),
                                       activationResult.error().get());
                        } else {
                            logger.log(Level.DEBUG,
                                       "[" + id + "] Failed to deactivate " + managedService.description() + ", deactivation "
                                               + "result: " + result);
                        }
                    }
                    result.put(managedService.descriptor().serviceType(), activationResult);
                } catch (Exception e) {
                    if (logger.isLoggable(Level.DEBUG)) {
                        logger.log(Level.DEBUG, "[" + id + "] Failed to deactivate service provider: " + managedService, e);
                    }

                    ActivationResult activationResult = ActivationResult.builder()
                            .serviceProvider(managedService)
                            .startingActivationPhase(startingActivationPhase)
                            .targetActivationPhase(Phase.DESTROYED)
                            .finishingActivationPhase(managedService.phase())
                            .finishingStatus(ActivationStatus.FAILURE)
                            .error(e)
                            .build();
                    result.put(managedService.descriptor().serviceType(), activationResult);
                }
            }

            active = false;

            return result;
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    <T> ManagedService<T> serviceProvider(ServiceManager<T> serviceManager) {
        ServiceDescriptor<T> descriptor = serviceManager.descriptor();

        try {
            serviceProvidersLock.readLock().lock();
            checkActive();
            ManagedService<?> serviceProvider = serviceProviders.get(descriptor);
            if (serviceProvider != null) {
                return (ManagedService<T>) serviceProvider;
            }
        } finally {
            serviceProvidersLock.readLock().unlock();
        }

        // failed to get instance, now let's obtain a write lock and do it again
        try {
            serviceProvidersLock.writeLock().lock();
            checkActive();
            return (ManagedService<T>) serviceProviders.computeIfAbsent(descriptor,
                                                                        desc -> serviceManager.supplyManagedService());
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    private static Comparator<? super ManagedService<?>> shutdownComparator() {
        return Comparator
                .<ManagedService<?>>comparingInt(it -> it.descriptor().runLevel())
                .thenComparing(it -> it.descriptor().weight());
    }

    private void checkActive() {
        if (!active) {
            throw new InjectionException("Injection scope " + scope.fqName() + "[" + id + "] is no longer active.");
        }
    }
}
