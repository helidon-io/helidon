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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Activators.BaseActivator;
import io.helidon.service.registry.Service.QualifiedInstance;

/*
Manager of a single service. There is one instance per service provider (and per service descriptor).
 */
class ServiceManager<T> {
    private final ServiceProvider<T> provider;
    private final boolean skipBindingPlan;
    private final boolean fixedInstance;
    private final Supplier<Activator<T>> activatorSupplier;
    private final CoreServiceRegistry registry;
    private final Supplier<Scope> scopeSupplier;

    ServiceManager(CoreServiceRegistry registry,
                   Supplier<Scope> scopeSupplier,
                   ServiceProvider<T> provider,
                   boolean skipBindingPlan,
                   Supplier<Activator<T>> activatorSupplier) {
        this(registry, scopeSupplier, provider, skipBindingPlan, skipBindingPlan, activatorSupplier);
    }

    ServiceManager(CoreServiceRegistry registry,
                   Supplier<Scope> scopeSupplier,
                   ServiceProvider<T> provider,
                   boolean skipBindingPlan,
                   boolean fixedInstance,
                   Supplier<Activator<T>> activatorSupplier) {
        this.registry = registry;
        this.scopeSupplier = scopeSupplier;
        this.provider = provider;
        this.skipBindingPlan = skipBindingPlan;
        this.fixedInstance = fixedInstance;
        this.activatorSupplier = activatorSupplier;
    }

    @Override
    public String toString() {
        return provider.descriptor().serviceType().classNameWithEnclosingNames();
    }

    void ensureBindingPlan() {
        if (skipBindingPlan) {
            // late-bound instances and descriptors are not part of the build-time binding plan
            return;
        }
        registry.bindings()
                .bindingPlan(provider.descriptor())
                .ensure();
    }

    ServiceInstance<T> registryInstance(Lookup lookup, QualifiedInstance<T> instance) {
        return new ServiceInstanceImpl<>(provider.descriptor(),
                                         provider.contracts(lookup),
                                         instance);
    }

    Optional<List<ServiceInstance<T>>> activeInstances(Lookup lookup) {
        Activator<T> serviceActivator;
        if (fixedInstance) {
            serviceActivator = activatorSupplier.get();
        } else {
            Optional<Activator<T>> existingActivator = existingActivator();
            if (existingActivator.isEmpty()) {
                return Optional.empty();
            }
            serviceActivator = existingActivator.get();
        }

        if (serviceActivator.phase() != ActivationPhase.ACTIVE) {
            if (fixedInstance) {
                return explicitInstances(serviceActivator, lookup);
            }
            return Optional.empty();
        }

        return serviceActivator
                .instances(lookup)
                .map(it -> it.stream()
                        .map(instance -> registryInstance(lookup, instance))
                        .toList());
    }

    @SuppressWarnings("unchecked")
    private Optional<List<ServiceInstance<T>>> explicitInstances(Activator<T> serviceActivator, Lookup lookup) {
        if (serviceActivator instanceof BaseActivator<?> baseActivator) {
            return ((BaseActivator<T>) baseActivator)
                    .targetInstances(lookup)
                    .map(it -> it.stream()
                            .map(instance -> registryInstance(lookup, instance))
                            .toList());
        }

        return serviceActivator
                .instances(lookup)
                .map(it -> it.stream()
                        .map(instance -> registryInstance(lookup, instance))
                        .toList());
    }

    private Optional<Activator<T>> existingActivator() {
        try {
            ScopedRegistry scopedRegistry = scopeSupplier.get().registry();
            if (scopedRegistry instanceof ScopedRegistryImpl scopedRegistryImpl) {
                return scopedRegistryImpl.existingActivator(provider.descriptor());
            }

            return Optional.empty();
        } catch (ScopeNotActiveException e) {
            return Optional.empty();
        }
    }

    ServiceInfo descriptor() {
        return provider.descriptor();
    }

    /*
    Get service activator for the scope it is in (always works for singleton, may fail for other)
    this provides an instance of an activator that is bound to a scope instance
    */
    Activator<T> activator() {
        return scopeSupplier
                .get()
                .registry()
                .activator(provider.descriptor(),
                           activatorSupplier);
    }

    private static final class ServiceInstanceImpl<T> implements ServiceInstance<T> {
        private final ServiceDescriptor<T> descriptor;
        private final QualifiedInstance<T> qualifiedInstance;
        private final Set<ResolvedType> contracts;

        private ServiceInstanceImpl(ServiceDescriptor<T> descriptor,
                                    Set<ResolvedType> contracts,
                                    QualifiedInstance<T> qualifiedInstance) {
            this.descriptor = descriptor;
            this.contracts = contracts;
            this.qualifiedInstance = qualifiedInstance;
        }

        @Override
        public T get() {
            return qualifiedInstance.get();
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiedInstance.qualifiers();
        }

        @Override
        public Set<ResolvedType> contracts() {
            return contracts;
        }

        @Override
        public TypeName scope() {
            return descriptor.scope();
        }

        @Override
        public double weight() {
            return descriptor.weight();
        }

        @Override
        public TypeName serviceType() {
            return descriptor.serviceType();
        }

        @Override
        public String toString() {
            return "Instance of " + descriptor.serviceType().fqName() + ": " + qualifiedInstance;
        }
    }
}
