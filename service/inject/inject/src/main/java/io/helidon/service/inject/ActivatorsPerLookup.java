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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.inject.api.ActivationResult;
import io.helidon.service.inject.api.Activator.Phase;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.InjectionPointProvider;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Injection.QualifiedProvider;
import io.helidon.service.inject.api.Injection.ServicesProvider;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.ProviderType;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.inject.api.ServiceInstance;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

import static io.helidon.service.inject.Activators.QualifiedProviderActivator.OBJECT_GENERIC_TYPE;
import static java.util.function.Predicate.not;

/*
 Developer note: when changing this, also change Activators
 */

/**
 * Activator types for various types the users can implement, for services without scope
 * (with {@link io.helidon.service.inject.api.Injection.PerLookup} as its scope in descriptor).
 * <p>
 * Activators in this type create an instance per injection, or per call to {@link java.util.function.Supplier#get()}.
 *
 * @see io.helidon.service.inject.Activators
 */
@SuppressWarnings("checkstyle:VisibilityModifier") // as long as all are inner classes, this is OK
final class ActivatorsPerLookup {
    private ActivatorsPerLookup() {
    }

    /**
     * {@code MyService implements Contract}.
     * Created for a service within each scope.
     */
    static class SingleServiceActivator<T> extends Activators.BaseActivator<T> {
        protected OnDemandInstance<T> serviceInstance;

        SingleServiceActivator(ServiceProvider<T> provider) {
            super(provider);
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            if (serviceInstance == null) {
                return Optional.empty();
            }
            return Optional.of(List.of(QualifiedInstance.create(serviceInstance.get(currentPhase),
                                                                provider.descriptor().qualifiers())));
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            this.serviceInstance = new OnDemandInstance<>(InjectionContext.create(provider.injectionPlan()),
                                                          provider.interceptionMetadata(),
                                                          provider.descriptor());
        }

        @Override
        protected void preDestroy(ActivationResult.Builder response) {
            this.serviceInstance = null;
        }
    }

    /**
     * {@code MyService implements Supplier<Contract>}.
     */
    static class SupplierActivator<T> extends SingleServiceActivator<T> {
        SupplierActivator(ServiceProvider<T> provider) {
            super(provider);
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (requestedProvider(lookup, ProviderType.SUPPLIER)) {
                if (serviceInstance == null) {
                    return Optional.empty();
                }
                // the user requested the provider, not the provided
                T instance = serviceInstance.get(currentPhase);
                return Optional.of(List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers())));
            }
            return targetInstances();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            if (serviceInstance == null) {
                return Optional.empty();
            }
            Supplier<T> instanceSupplier = (Supplier<T>) serviceInstance.get(currentPhase);
            return Optional.of(List.of(QualifiedInstance.create(instanceSupplier.get(),
                                                                provider.descriptor().qualifiers())));
        }
    }

    /**
     * {@code MyService implements QualifiedProvider}.
     */
    static class QualifiedProviderActivator<T> extends SingleServiceActivator<T> {
        private final TypeName supportedQualifier;
        private final Set<TypeName> supportedContracts;
        private final boolean anyMatch;

        QualifiedProviderActivator(ServiceProvider<T> provider, GeneratedInjectService.QualifiedProviderDescriptor qpd) {
            super(provider);
            this.supportedQualifier = qpd.qualifierType();
            this.supportedContracts = provider.descriptor().contracts()
                    .stream()
                    .filter(not(QualifiedProvider.TYPE::equals))
                    .collect(Collectors.toSet());
            this.anyMatch = this.supportedContracts.contains(TypeNames.OBJECT);
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (serviceInstance == null) {
                return Optional.empty();
            }

            return lookup.qualifiers()
                    .stream()
                    .filter(it -> this.supportedQualifier.equals(it.typeName()))
                    .findFirst()
                    .flatMap(qualifier -> targetInstances(lookup, qualifier));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup, Qualifier qualifier) {
            if (lookup.contracts().size() == 1) {
                if (anyMatch || this.supportedContracts.containsAll(lookup.contracts())) {
                    Optional<GenericType<?>> genericType = lookup.injectionPoint()
                            .map(Ip::contractType);
                    GenericType contract = genericType
                            .or(lookup::contractType)
                            .orElse(OBJECT_GENERIC_TYPE);

                    return Optional.of(targetInstances(lookup, qualifier, contract));
                }
            }
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        private List<QualifiedInstance<T>> targetInstances(Lookup lookup, Qualifier qualifier, GenericType<T> contract) {
            var qProvider = (QualifiedProvider<T, ?>) serviceInstance.get(currentPhase);

            return qProvider.list(qualifier, lookup, contract);
        }
    }

    /**
     * {@code MyService implements InjectionPointProvider}.
     */
    static class IpProviderActivator<T> extends SingleServiceActivator<T> {
        IpProviderActivator(ServiceProvider<T> provider) {
            super(provider);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (serviceInstance == null) {
                return Optional.empty();
            }
            var ipProvider = (InjectionPointProvider<T>) serviceInstance.get(currentPhase);

            if (requestedProvider(lookup, ProviderType.IP_PROVIDER)) {
                // the user requested the provider, not the provided
                T instance = (T) ipProvider;
                return Optional.of(List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers())));
            }

            try {
                return Optional.of(ipProvider.list(lookup));
            } catch (RuntimeException e) {
                throw new ServiceRegistryException("Failed to list instances in InjectionPointProvider: "
                                                           + ipProvider.getClass().getName(), e);
            }
        }
    }

    /**
     * {@code MyService implements ServicesProvider}.
     */
    static class ServicesProviderActivator<T> extends SingleServiceActivator<T> {
        ServicesProviderActivator(ServiceProvider<T> provider) {
            super(provider);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            ServicesProvider<T> instanceSupplier = (ServicesProvider<T>) serviceInstance.get(currentPhase);

            if (requestedProvider(lookup, ProviderType.SERVICES_PROVIDER)) {
                return Optional.of(List.of(QualifiedInstance.create((T) instanceSupplier, descriptor().qualifiers())));
            }

            List<QualifiedInstance<T>> response = new ArrayList<>();
            for (QualifiedInstance<T> instance : instanceSupplier.services()) {
                if (lookup.matchesQualifiers(instance.qualifiers())) {
                    response.add(instance);
                }
            }

            return Optional.of(List.copyOf(response));
        }
    }

    /**
     * Service annotated {@link io.helidon.service.inject.api.Injection.CreateFor}.
     */
    static class CreateForActivator<T> extends Activators.BaseActivator<T> {
        private final InjectServiceRegistryImpl registry;
        private final TypeName createFor;

        private List<QualifiedOnDemandInstance<T>> serviceInstances;

        CreateForActivator(InjectServiceRegistryImpl registry,
                           ServiceProvider<T> provider,
                           GeneratedInjectService.CreateForDescriptor dbd) {
            super(provider);

            this.registry = registry;
            this.createFor = dbd.createFor();
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            // at this moment, we must resolve services that are driving this instance
            List<ServiceInfo> services = registry.servicesByContract(createFor);

            serviceInstances = services.stream()
                    .map(registry::serviceManager)
                    .flatMap(it -> it.activator()
                            .instances(Lookup.EMPTY)
                            .stream()
                            .flatMap(List::stream)
                            .map(qi -> it.registryInstance(Lookup.EMPTY, qi)))
                    .map(it -> QualifiedOnDemandInstance.create(provider, it))
                    .collect(Collectors.toList());
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return Optional.of(serviceInstances.stream()
                                       .map(it -> QualifiedInstance.create(it.serviceInstance()
                                                                                   .get(currentPhase),
                                                                           it.qualifiers()))
                                       .toList());
        }

        @Override
        protected void preDestroy(ActivationResult.Builder response) {
            this.serviceInstances = null;
        }
    }

    private record QualifiedOnDemandInstance<T>(OnDemandInstance<T> serviceInstance,
                                                Set<Qualifier> qualifiers) {
        static <T> QualifiedOnDemandInstance<T> create(ServiceProvider<T> provider,
                                                       ServiceInstance<?> driver) {
            Set<Qualifier> qualifiers = driver.qualifiers();
            Qualifier name = qualifiers.stream()
                    .filter(not(Qualifier.WILDCARD_NAMED::equals))
                    .filter(it -> Injection.Named.TYPE.equals(it.typeName()))
                    .findFirst()
                    .orElse(Qualifier.DEFAULT_NAMED);
            Set<Qualifier> newQualifiers = provider.descriptor().qualifiers()
                    .stream()
                    .filter(not(Qualifier.WILDCARD_NAMED::equals))
                    .collect(Collectors.toSet());
            newQualifiers.add(name);

            Map<Dependency, IpPlan<?>> injectionPlan = Activators.CreateForActivator.updatePlan(provider.injectionPlan(),
                                                                                                driver,
                                                                                                name);

            return new QualifiedOnDemandInstance<>(new OnDemandInstance<>(InjectionContext.create(injectionPlan),
                                                                          provider.interceptionMetadata(),
                                                                          provider.descriptor()),
                                                   newQualifiers);
        }
    }

    static class OnDemandInstance<T> {
        private final ReentrantLock lock = new ReentrantLock();
        private final DependencyContext ctx;
        private final InterceptionMetadata interceptionMetadata;
        private final InjectServiceDescriptor<T> source;

        OnDemandInstance(DependencyContext ctx,
                         InterceptionMetadata interceptionMetadata,
                         InjectServiceDescriptor<T> source) {
            this.ctx = ctx;
            this.interceptionMetadata = interceptionMetadata;
            this.source = source;
        }

        @SuppressWarnings("unchecked")
        T get(Phase phase) {
            if (lock.isHeldByCurrentThread()) {
                throw new ServiceRegistryException("Cyclic dependency, attempting to obtain an instance of "
                                                           + source.serviceType().fqName()
                                                           + " while instantiating it");
            }
            try {
                lock.lock();
                if (phase.ordinal() >= Phase.CONSTRUCTING.ordinal()) {
                    T instance = (T) source.instantiate(ctx, interceptionMetadata);
                    if (phase.ordinal() >= Phase.INJECTING.ordinal()) {
                        Set<String> injected = new LinkedHashSet<>();
                        source.inject(ctx, interceptionMetadata, injected, instance);
                    }
                    if (phase.ordinal() >= Phase.POST_CONSTRUCTING.ordinal()) {
                        source.postConstruct(instance);
                    }
                    return instance;
                }
            } finally {
                lock.unlock();
            }
            throw new ServiceRegistryException("An instance was requested even though lifecycle is limited to phase: " + phase);
        }
    }
}
