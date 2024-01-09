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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.RegistryInstance;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServicesProvider;

/*
 Developer note: when changing this, also change ManagedServices
 */
@SuppressWarnings("checkstyle:VisibilityModifier") // as long as all are inner classes, this is OK
final class ManagedServicesPerLookup {
    private ManagedServicesPerLookup() {
    }

    abstract static class BaseActivator<T> implements ManagedService<T> {
        final ServiceProvider<T> provider;
        private final ReadWriteLock instanceLock = new ReentrantReadWriteLock();
        protected final Lock readLock = instanceLock.readLock();
        protected final Lock writeLock = instanceLock.writeLock();
        volatile Phase currentPhase = Phase.INIT;

        BaseActivator(ServiceProvider<T> provider) {
            this.provider = provider;
        }

        // three states
        // service provided an empty list (services provider etc.)
        // service provided null, or activation failed
        // service provided 1 or more instances
        @Override
        public Optional<List<QualifiedInstance<T>>> instances(Lookup lookup) {
            try {
                readLock.lock();
                if (currentPhase == Phase.ACTIVE) {
                    return targetInstances();
                }
            } finally {
                readLock.unlock();
            }

            try {
                writeLock.lock();
                if (currentPhase != Phase.ACTIVE) {
                    ActivationResult res = activate(provider.activationRequest());
                    if (res.failure()) {
                        return Optional.empty();
                    }
                }
                return targetInstances();
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public ServiceDescriptor<T> descriptor() {
            return provider.descriptor();
        }

        @Override
        public String description() {
            return provider.descriptor().serviceType().classNameWithEnclosingNames()
                    + ":" + currentPhase;
        }

        @Override
        public ActivationResult activate(ActivationRequest request) {
            try {
                // probably re-entering the same lock
                writeLock.lock();

                if (currentPhase == request.targetPhase()) {
                    // we are already there, just return success
                    return ActivationResult.builder()
                            .serviceProvider(this)
                            .startingActivationPhase(currentPhase)
                            .finishingActivationPhase(currentPhase)
                            .targetActivationPhase(currentPhase)
                            .finishingStatus(ActivationStatus.SUCCESS)
                            .build();
                }
                if (currentPhase.ordinal() > request.targetPhase().ordinal()) {
                    // we are already ahead, this is a problem
                    return ActivationResult.builder()
                            .serviceProvider(this)
                            .startingActivationPhase(currentPhase)
                            .finishingActivationPhase(currentPhase)
                            .targetActivationPhase(request.targetPhase())
                            .finishingStatus(ActivationStatus.FAILURE)
                            .build();
                }
                return doActivate(request);
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public ActivationResult deactivate(DeActivationRequest request) {
            try {
                // probably re-entering the same lock
                writeLock.lock();
                ActivationResult.Builder response = ActivationResult.builder()
                        .serviceProvider(this)
                        .finishingStatus(ActivationStatus.SUCCESS);

                if (!currentPhase.eligibleForDeactivation()) {
                    stateTransitionStart(response, Phase.DESTROYED);
                    return ActivationResult.builder()
                            .serviceProvider(this)
                            .targetActivationPhase(Phase.DESTROYED)
                            .finishingActivationPhase(currentPhase)
                            .finishingStatus(ActivationStatus.SUCCESS)
                            .build();
                }

                response.startingActivationPhase(this.currentPhase);
                stateTransitionStart(response, Phase.PRE_DESTROYING);
                preDestroy(response);
                stateTransitionStart(response, Phase.DESTROYED);

                return response.build();
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public Phase phase() {
            return currentPhase;
        }

        protected void construct(ActivationResult.Builder response) {
        }

        protected void preDestroy(ActivationResult.Builder response) {
        }

        protected abstract Optional<List<QualifiedInstance<T>>> targetInstances();

        private void stateTransitionStart(ActivationResult.Builder res, Phase phase) {
            res.finishingActivationPhase(phase);
            this.currentPhase = phase;
        }

        private ActivationResult doActivate(ActivationRequest request) {
            // we just move to the correct phase, as we can do nothing until an instance is requested
            Phase initialPhase = this.currentPhase;
            Phase startingPhase = request.startingPhase().orElse(initialPhase);
            Phase targetPhase = request.targetPhase();
            this.currentPhase = startingPhase;
            Phase finishingPhase = startingPhase;

            ActivationResult.Builder response = ActivationResult.builder()
                    .serviceProvider(this)
                    .startingActivationPhase(initialPhase)
                    .finishingActivationPhase(startingPhase)
                    .targetActivationPhase(targetPhase)
                    .finishingStatus(ActivationStatus.SUCCESS);

            if (targetPhase.ordinal() > Phase.ACTIVATION_STARTING.ordinal()
                    && initialPhase == Phase.INIT) {
                if (Phase.INIT == startingPhase
                        || Phase.PENDING == startingPhase
                        || Phase.ACTIVATION_STARTING == startingPhase
                        || Phase.DESTROYED == startingPhase) {
                    stateTransitionStart(response, Phase.ACTIVATION_STARTING);
                }
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.CONSTRUCTING.ordinal()) {
                stateTransitionStart(response, Phase.CONSTRUCTING);
                construct(response);
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.INJECTING.ordinal()
                    && (Phase.CONSTRUCTING == finishingPhase)) {
                stateTransitionStart(response, Phase.INJECTING);
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.POST_CONSTRUCTING.ordinal()
                    && (Phase.INJECTING == finishingPhase)) {
                stateTransitionStart(response, Phase.POST_CONSTRUCTING);
            }
            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.ACTIVATION_FINISHING.ordinal()
                    && (Phase.POST_CONSTRUCTING == finishingPhase)) {
                stateTransitionStart(response, Phase.ACTIVATION_FINISHING);
            }
            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.ACTIVE.ordinal()
                    && (Phase.ACTIVATION_FINISHING == finishingPhase)) {
                stateTransitionStart(response, Phase.ACTIVE);
            }

            return response.build();
        }
    }

    /**
     * {@code MyService implements Contract}.
     * Created for a service within each scope.
     */
    static class SingleServiceActivator<T> extends BaseActivator<T> {
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

        @SuppressWarnings("unchecked")
        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            Supplier<T> instanceSupplier = (Supplier<T>) serviceInstance.get(currentPhase);
            return Optional.of(List.of(QualifiedInstance.create(instanceSupplier.get(),
                                                                provider.descriptor().qualifiers())));
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
        public Optional<List<QualifiedInstance<T>>> instances(Lookup lookup) {
            if (serviceInstance == null) {
                return Optional.empty();
            }
            var ipProvider = (InjectionPointProvider<T>) serviceInstance.get(currentPhase);

            return Optional.of(ipProvider.list(lookup)
                                       .stream()
                                       .map(it -> QualifiedInstance.create(it, provider.descriptor().qualifiers()))
                                       .toList());
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
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            ServicesProvider<T> instanceSupplier = (ServicesProvider<T>) serviceInstance.get(currentPhase);
            return Optional.of(instanceSupplier.services());
        }
    }

    /**
     * Service annotated {@link io.helidon.inject.service.Injection.DrivenBy}.
     */
    static class DrivenByActivator<T> extends BaseActivator<T> {
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
            List<RegistryInstance<Object>> drivingInstances = services.lookupInstances(Lookup.builder().addContract(drivenBy)
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
                                                       RegistryInstance<?> driver) {
            Set<Qualifier> qualifiers = driver.qualifiers();
            Qualifier name = qualifiers.stream()
                    .filter(it -> Injection.Named.TYPE_NAME.equals(it.typeName()))
                    .findFirst()
                    .orElse(Qualifier.DEFAULT_NAMED);
            Set<Qualifier> newQualifiers = new HashSet<>(provider.descriptor().qualifiers());
            newQualifiers.add(name);

            Map<Ip, IpPlan<?>> injectionPlan = ManagedServices.DrivenByActivator.updatePlan(provider.injectionPlan(),
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
