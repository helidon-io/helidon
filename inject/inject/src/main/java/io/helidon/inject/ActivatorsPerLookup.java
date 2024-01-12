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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInstance;
import io.helidon.inject.service.ServicesProvider;

import static java.util.function.Predicate.not;

/*
 Developer note: when changing this, also change Activators
 */

/**
 * Activator types for various types the users can implement, for services without scope
 * (with {@link io.helidon.inject.service.Injection.Service} as its scope in descriptor).
 * <p>
 * Activators in this type create an instance per injection, or per call to {@link java.util.function.Supplier#get()}.
 *
 * @see io.helidon.inject.Activators
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
            this.serviceInstance = new OnDemandInstance<>(InjectionContextImpl.create(provider.injectionPlan()),
                                                          provider.interceptMetadata(),
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
            if (lookup.contracts().contains(TypeNames.SUPPLIER)) {
                // the user requested the provider, not the provided
                T instance = serviceInstance.get(currentPhase);
                return Optional.of(List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers())));
            }
            return targetInstances();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
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

        QualifiedProviderActivator(ServiceProvider<T> provider) {
            super(provider);
            this.supportedQualifier = provider.descriptor().qualifiers().iterator().next().typeName();
            this.supportedContracts = provider.descriptor().contracts()
                    .stream()
                    .filter(not(QualifiedProvider.TYPE_NAME::equals))
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

        private Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup, Qualifier qualifier) {
            if (lookup.contracts().size() == 1) {
                if (anyMatch || this.supportedContracts.containsAll(lookup.contracts())) {
                    return Optional.of(targetInstances(lookup, qualifier, lookup.contracts().iterator().next()));
                }
            }
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        private List<QualifiedInstance<T>> targetInstances(Lookup lookup, Qualifier qualifier, TypeName contract) {
            var qProvider = (QualifiedProvider<?, T>) serviceInstance.get(currentPhase);

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

            if (lookup.contracts().contains(InjectionPointProvider.TYPE_NAME)) {
                // the user requested the provider, not the provided
                T instance = (T) ipProvider;
                return Optional.of(List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers())));
            }

            try {
                return Optional.of(ipProvider.list(lookup));
            } catch (RuntimeException e) {
                throw new InjectionServiceProviderException("Failed to list instances in InjectionPointProvider", e, provider);
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

            if (lookup.contracts().contains(ServicesProvider.TYPE_NAME)) {
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
     * Service annotated {@link io.helidon.inject.service.Injection.DrivenBy}.
     */
    static class DrivenByActivator<T> extends Activators.BaseActivator<T> {
        private final Services services;
        private final TypeName drivenBy;
        private List<QualifiedOnDemandInstance<T>> serviceInstances;

        DrivenByActivator(Services services, ServiceProvider<T> provider) {
            super(provider);

            this.services = services;
            this.drivenBy = provider.descriptor().drivenBy()
                    .orElseThrow(() -> new InjectionException(
                            "Cannot create a driven by activator, as drivenBy is empty in descriptor"));
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            // at this moment, we must resolve services that are driving this instance
            List<ServiceInstance<Object>> drivingInstances = services.lookupInstances(Lookup.builder().addContract(drivenBy)
                                                                                              .build());
            serviceInstances = drivingInstances.stream()
                    .map(it -> QualifiedOnDemandInstance.create(provider, it))
                    .toList();
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
                    .filter(it -> Injection.Named.TYPE_NAME.equals(it.typeName()))
                    .findFirst()
                    .orElse(Qualifier.DEFAULT_NAMED);
            Set<Qualifier> newQualifiers = provider.descriptor().qualifiers()
                    .stream()
                    .filter(not(Qualifier.WILDCARD_NAMED::equals))
                    .collect(Collectors.toSet());
            newQualifiers.add(name);

            Map<Ip, IpPlan<?>> injectionPlan = Activators.DrivenByActivator.updatePlan(provider.injectionPlan(),
                                                                                       driver,
                                                                                       name);

            return new QualifiedOnDemandInstance<>(new OnDemandInstance<>(InjectionContextImpl.create(injectionPlan),
                                                                          provider.interceptMetadata(),
                                                                          provider.descriptor()),
                                                   newQualifiers);
        }
    }

    static class OnDemandInstance<T> {
        private final InjectionContext ctx;
        private final InterceptionMetadata interceptionMetadata;
        private final ServiceDescriptor<T> source;

        OnDemandInstance(InjectionContext ctx,
                         InterceptionMetadata interceptionMetadata,
                         ServiceDescriptor<T> source) {
            this.ctx = ctx;
            this.interceptionMetadata = interceptionMetadata;
            this.source = source;
        }

        @SuppressWarnings("unchecked")
        T get(Phase phase) {
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
            throw new InjectionException("An instance was requested even though lifecycle is limited to phase: " + phase);
        }
    }
}
