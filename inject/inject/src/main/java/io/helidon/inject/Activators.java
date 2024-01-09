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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.RegistryInstance;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.service.ServicesProvider;

import static java.util.function.Predicate.not;

/*
 Developer note: when changing this, change ActivatorsPerLookup as well
 */

/**
 * Activator types for various types the users can implement, for real scopes (singleton, request scope).
 *
 * @see io.helidon.inject.ActivatorsPerLookup
 */
@SuppressWarnings("checkstyle:VisibilityModifier") // as long as all are inner classes, this is OK
final class Activators {
    private Activators() {
    }

    abstract static class BaseActivator<T> implements Activator<T> {
        final ServiceProvider<T> provider;
        private final ReadWriteLock instanceLock = new ReentrantReadWriteLock();
        protected final Lock readLock = instanceLock.readLock();
        protected final Lock writeLock = instanceLock.writeLock();
        volatile Phase currentPhase = Phase.INIT;

        BaseActivator(ServiceProvider<T> provider) {
            this.provider = provider;
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

        // three states
        // service provided an empty list (services provider etc.)
        // service provided null, or activation failed
        // service provided 1 or more instances
        @Override
        public Optional<List<QualifiedInstance<T>>> instances(Lookup lookup) {
            /*
            At the time we are looking up instances, we also resolve them to appropriate type
            As this type represents a value within a scope, and not "instance per call", we can safely
            store the result, unless it is lookup bound
             */
            try {
                readLock.lock();
                if (currentPhase == Phase.ACTIVE) {
                    return targetInstances(lookup);
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
                return targetInstances(lookup);
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
        public String toString() {
            return getClass().getSimpleName() + " for " + provider;
        }

        protected void construct(ActivationResult.Builder response) {
        }

        protected void inject(ActivationResult.Builder response) {
        }

        protected void postConstruct(ActivationResult.Builder response) {
        }

        protected void finishActivation(ActivationResult.Builder response) {
        }

        protected void preDestroy(ActivationResult.Builder response) {
        }

        protected abstract void setTargetInstances();

        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            return targetInstances();
        }

        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return Optional.empty();
        }

        private void stateTransitionStart(ActivationResult.Builder res, Phase phase) {
            res.finishingActivationPhase(phase);
            this.currentPhase = phase;
        }

        private ActivationResult doActivate(ActivationRequest request) {
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
                inject(response);
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.POST_CONSTRUCTING.ordinal()
                    && (Phase.INJECTING == finishingPhase)) {
                stateTransitionStart(response, Phase.POST_CONSTRUCTING);
                postConstruct(response);
            }
            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.ACTIVATION_FINISHING.ordinal()
                    && (Phase.POST_CONSTRUCTING == finishingPhase)) {
                stateTransitionStart(response, Phase.ACTIVATION_FINISHING);
                finishActivation(response);
            }
            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= Phase.ACTIVE.ordinal()
                    && (Phase.ACTIVATION_FINISHING == finishingPhase)) {
                stateTransitionStart(response, Phase.ACTIVE);
            }

            if (startingPhase.ordinal() < Phase.CONSTRUCTING.ordinal()
                    && currentPhase.ordinal() >= Phase.CONSTRUCTING.ordinal()) {
                setTargetInstances();
            }

            return response.build();
        }
    }

    static class FixedActivator<T> extends BaseActivator<T> {
        private final Optional<List<QualifiedInstance<T>>> instances;

        FixedActivator(ServiceProvider<T> provider, T instance) {
            super(provider);

            List<QualifiedInstance<T>> values = List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers()));
            this.instances = Optional.of(values);
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return instances;
        }

        @Override
        protected void setTargetInstances() {
        }
    }

    /**
     * {@code MyService implements Contract}.
     * Created for a service within each scope.
     */
    static class SingleServiceActivator<T> extends BaseActivator<T> {
        protected ServiceInstance<T> serviceInstance;
        protected List<QualifiedInstance<T>> targetInstances;

        SingleServiceActivator(ServiceProvider<T> provider) {
            super(provider);
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return Optional.ofNullable(targetInstances);
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            this.serviceInstance = ServiceInstance.create(provider, provider.injectionPlan());
            this.serviceInstance.construct();
        }

        @Override
        protected void setTargetInstances() {
            if (serviceInstance != null) {
                // lifecycle of the target instances is the same as of the service instance
                // when service is created, we have the target...
                this.targetInstances = List.of(QualifiedInstance.create(serviceInstance.get(),
                                                                        provider.descriptor().qualifiers()));
            }
        }

        protected void inject(ActivationResult.Builder response) {
            if (serviceInstance != null) {
                serviceInstance.inject();
            }
        }

        protected void postConstruct(ActivationResult.Builder response) {
            if (serviceInstance != null) {
                serviceInstance.postConstruct();
            }
        }

        @Override
        protected void preDestroy(ActivationResult.Builder response) {
            if (serviceInstance != null) {
                serviceInstance.preDestroy();
            }
            serviceInstance = null;
            targetInstances = null;
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
        protected void setTargetInstances() {
            if (serviceInstance == null) {
                return;
            }
            // the instance list is created just once, hardcoded to the instance we have just created
            Supplier<T> instanceSupplier = (Supplier<T>) serviceInstance.get();
            this.targetInstances = List.of(QualifiedInstance.create(instanceSupplier.get(),
                                                                    provider.descriptor().qualifiers()));
        }
    }

    /**
     * {@code MyService implements InjectionPointProvider}.
     */
    static class IpProviderActivator<T> extends SingleServiceActivator<T> {
        IpProviderActivator(ServiceProvider<T> provider) {
            super(provider);
        }

        @Override
        protected void setTargetInstances() {
            // target instances cannot be created, they are resolved on each lookup
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (serviceInstance == null) {
                return Optional.empty();
            }
            var ipProvider = (InjectionPointProvider<T>) serviceInstance.get();

            if (lookup.contracts().contains(InjectionPointProvider.TYPE_NAME)) {
                // the user requested the provider, not the provided
                T instance = (T) ipProvider;
                return Optional.of(List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers())));
            }

            try {
                return Optional.of(ipProvider.list(lookup)
                                           .stream()
                                           .map(it -> QualifiedInstance.create(it, provider.descriptor().qualifiers()))
                                           .toList());
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

        @Override
        protected void construct(ActivationResult.Builder response) {
            super.construct(response);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void setTargetInstances() {
            if (serviceInstance == null) {
                return;
            }
            // the instance list is created just once, hardcoded to the instance we have just created
            ServicesProvider<T> instanceSupplier = (ServicesProvider<T>) serviceInstance.get();
            targetInstances = instanceSupplier.services();
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (targetInstances == null) {
                return Optional.empty();
            }
            List<QualifiedInstance<T>> response = new ArrayList<>();
            for (QualifiedInstance<T> instance : targetInstances) {
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
    static class DrivenByActivator<T> extends BaseActivator<T> {
        private final Services services;
        private final TypeName drivenBy;
        private List<QualifiedServiceInstance<T>> serviceInstances;
        private List<QualifiedInstance<T>> targetInstances;

        DrivenByActivator(Services services, ServiceProvider<T> provider) {
            super(provider);

            this.services = services;
            this.drivenBy = provider.descriptor().drivenBy()
                    .orElseThrow(() -> new InjectionException(
                            "Cannot create a driven by activator, as drivenBy is empty in descriptor"));
        }

        static Map<Ip, IpPlan<?>> updatePlan(Map<Ip, IpPlan<?>> injectionPlan,
                                             RegistryInstance<?> driver,
                                             Qualifier name) {

            Set<Ip> ips = Set.copyOf(injectionPlan.keySet());

            Set<TypeName> contracts = driver.contracts();

            Map<Ip, IpPlan<?>> updatedPlan = new HashMap<>(injectionPlan);

            for (Ip ip : ips) {
                // injection point for the driving instance - we add the wildcard named ourself
                if (contracts.contains(ip.contract())
                        && ip.qualifiers().size() == 1
                        && ip.qualifiers().contains(Qualifier.WILDCARD_NAMED)) {
                    if (RegistryInstance.TYPE_NAME.equals(ip.typeName())) {
                        // if the injection point has the same contract, no qualifiers, then it is the driving instance
                        updatedPlan.put(ip, new IpPlan<>(() -> driver, injectionPlan.get(ip).descriptors()));
                    } else {
                        // if the injection point has the same contract, no qualifiers, then it is the driving instance
                        updatedPlan.put(ip, new IpPlan<>(driver, injectionPlan.get(ip).descriptors()));
                    }
                }
                // injection point for the service name
                if (TypeNames.STRING.equals(ip.contract())) {
                    // @DrivenByName String name
                    if (ip.qualifiers()
                            .stream()
                            .anyMatch(it -> Injection.DrivenByName.TYPE_NAME.equals(it.typeName()))) {
                        updatedPlan.put(ip, new IpPlan<>(() -> name, injectionPlan.get(ip).descriptors()));
                    }
                }
            }

            return updatedPlan;
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            // at this moment, we must resolve services that are driving this instance

            // we do not want to use lookup, as that is doing too much for us
            List<RegistryInstance<Object>> drivingInstances = driversFromPlan(provider.injectionPlan(), drivenBy)
                    .stream()
                    .map(services::serviceManager)
                    .flatMap(it -> services.managerInstances(Lookup.EMPTY, it).stream())
                    .toList();

            serviceInstances = drivingInstances.stream()
                    .map(it -> QualifiedServiceInstance.create(provider, it))
                    .toList();
            for (QualifiedServiceInstance<T> serviceInstance : serviceInstances) {
                serviceInstance.serviceInstance().construct();
            }
        }

        @Override
        protected void inject(ActivationResult.Builder response) {
            for (QualifiedServiceInstance<T> instance : serviceInstances) {
                instance.serviceInstance().inject();
            }
        }

        @Override
        protected void postConstruct(ActivationResult.Builder response) {
            for (QualifiedServiceInstance<T> instance : serviceInstances) {
                instance.serviceInstance().postConstruct();
            }
        }

        @Override
        protected void setTargetInstances() {
            if (serviceInstances != null) {
                targetInstances = serviceInstances.stream()
                        .map(it -> QualifiedInstance.create(it.serviceInstance().get(), it.qualifiers()))
                        .toList();
            }
        }

        @Override
        protected void preDestroy(ActivationResult.Builder response) {
            if (serviceInstances != null) {
                serviceInstances.stream()
                        .map(QualifiedServiceInstance::serviceInstance)
                        .forEach(ServiceInstance::preDestroy);
            }
            serviceInstances = null;
            targetInstances = null;
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (targetInstances == null) {
                return Optional.empty();
            }
            List<QualifiedInstance<T>> response = new ArrayList<>();
            for (QualifiedInstance<T> instance : targetInstances) {
                if (lookup.matchesQualifiers(instance.qualifiers())) {
                    response.add(instance);
                }
            }

            return Optional.of(List.copyOf(response));
        }

        private List<ServiceInfo> driversFromPlan(Map<Ip, IpPlan<?>> ipSupplierMap, TypeName drivenBy) {
            // I need the list of descriptors from the injection plan
            for (Map.Entry<Ip, IpPlan<?>> entry : ipSupplierMap.entrySet()) {
                Ip ip = entry.getKey();
                if (drivenBy.equals(ip.contract())
                        && ip.qualifiers().size() == 1
                        && ip.qualifiers().contains(Qualifier.WILDCARD_NAMED)) {
                    return List.of(entry.getValue().descriptors());
                }
            }
            // there is not
            return services.servicesByContract(drivenBy);
        }

        private record QualifiedServiceInstance<T>(ServiceInstance<T> serviceInstance,
                                                   Set<Qualifier> qualifiers) {
            static <T> QualifiedServiceInstance<T> create(ServiceProvider<T> provider, RegistryInstance<?> driver) {
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

                Map<Ip, IpPlan<?>> injectionPlan = updatePlan(provider.injectionPlan(), driver, name);

                return new QualifiedServiceInstance<>(ServiceInstance.create(provider, injectionPlan), newQualifiers);
            }
        }
    }

    static class ServiceInstance<T> {
        private final InjectionContext ctx;
        private final InterceptionMetadata interceptionMetadata;
        private final ServiceDescriptor<T> source;

        private volatile T instance;

        private ServiceInstance(InjectionContext ctx, InterceptionMetadata interceptionMetadata, ServiceDescriptor<T> source) {
            this.ctx = ctx;
            this.interceptionMetadata = interceptionMetadata;
            this.source = source;
        }

        static <T> ServiceInstance<T> create(ServiceProvider<T> serviceProvider, Map<Ip, IpPlan<?>> injectionPlan) {
            // the same instance is returned for the lifetime of the service provider
            return new ServiceInstance<>(InjectionContextImpl.create(injectionPlan),
                                         serviceProvider.interceptMetadata(),
                                         serviceProvider.descriptor());
        }

        T get() {
            return instance;
        }

        @SuppressWarnings("unchecked")
        void construct() {
            instance = (T) source.instantiate(ctx, interceptionMetadata);
        }

        void inject() {
            // using linked set, so we can see in debugging what was injected first
            Set<String> injected = new LinkedHashSet<>();
            source.inject(ctx, interceptionMetadata, injected, instance);
        }

        void postConstruct() {
            source.postConstruct(instance);
        }

        void preDestroy() {
            source.preDestroy(instance);
        }
    }
}
