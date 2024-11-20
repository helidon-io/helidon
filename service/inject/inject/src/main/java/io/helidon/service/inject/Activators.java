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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.inject.api.ActivationRequest;
import io.helidon.service.inject.api.ActivationResult;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.FactoryType;
import io.helidon.service.inject.api.GeneratedInjectService.PerInstanceDescriptor;
import io.helidon.service.inject.api.GeneratedInjectService.QualifiedFactoryDescriptor;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.InjectionPointFactory;
import io.helidon.service.inject.api.Injection.QualifiedFactory;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.inject.api.ServiceInstance;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

import static java.util.function.Predicate.not;

/*
 Developer note: when changing this, change ActivatorsPerLookup as well
 */

/**
 * Activator types for various types the users can implement, for real scopes (singleton, request scope).
 *
 * @see io.helidon.service.inject.ActivatorsPerLookup
 */
@SuppressWarnings("checkstyle:VisibilityModifier") // as long as all are inner classes, this is OK
final class Activators {
    private Activators() {
    }

    @SuppressWarnings("unchecked")
    static <T> Activator<T> create(ServiceProvider<T> provider, T instance) {
        return switch (provider.descriptor().factoryType()) {
            case NONE, SERVICE, SUPPLIER -> {
                if (instance instanceof Supplier<?> supplier) {
                    yield new FixedSupplierActivator<>(provider, (Supplier<T>) supplier);
                } else {
                    yield new Activators.FixedActivator<>(provider, instance);
                }
            }
            case SERVICES -> new Activators.FixedServicesFactoryActivator<>(provider, (Injection.ServicesFactory<T>) instance);
            case INJECTION_POINT -> new Activators.FixedIpFactoryActivator<>(provider, (InjectionPointFactory<T>) instance);
            case QUALIFIED -> new Activators.FixedQualifiedFactoryActivator<>(provider, (QualifiedFactory<T, ?>) instance);
        };
    }

    static <T> Supplier<Activator<T>> create(InjectServiceRegistryImpl registry, ServiceProvider<T> provider) {
        InjectServiceDescriptor<T> descriptor = provider.descriptor();

        if (descriptor.scope().equals(Injection.PerLookup.TYPE)) {
            return switch (descriptor.factoryType()) {
                case NONE -> new MissingDescribedActivator<>(provider);
                case SERVICE -> {
                    if (descriptor instanceof PerInstanceDescriptor dbd) {
                        yield () -> new ActivatorsPerLookup.PerInstanceActivator<>(registry, provider, dbd);
                    }
                    yield () -> new ActivatorsPerLookup.SingleServiceActivator<>(provider);
                }
                case SUPPLIER -> () -> new ActivatorsPerLookup.SupplierActivator<>(provider);
                case SERVICES -> () -> new ActivatorsPerLookup.ServicesFactoryActivator<>(provider);
                case INJECTION_POINT -> () -> new ActivatorsPerLookup.IpFactoryActivator<>(provider);
                case QUALIFIED -> () ->
                        new ActivatorsPerLookup.QualifiedFactoryActivator<>(provider,
                                                                            (QualifiedFactoryDescriptor) descriptor);
            };
        } else {
            return switch (descriptor.factoryType()) {
                case NONE -> new MissingDescribedActivator<>(provider);
                case SERVICE -> {
                    if (descriptor instanceof PerInstanceDescriptor dbd) {
                        yield () -> new PerInstanceActivator<>(registry, provider, dbd);
                    }
                    yield () -> new Activators.SingleServiceActivator<>(provider);
                }
                case SUPPLIER -> () -> new Activators.SupplierActivator<>(provider);
                case SERVICES -> () -> new ServicesFactoryActivator<>(provider);
                case INJECTION_POINT -> () -> new IpFactoryActivator<>(provider);
                case QUALIFIED -> () ->
                        new QualifiedFactoryActivator<>(provider,
                                                        (QualifiedFactoryDescriptor) descriptor);
            };
        }
    }

    abstract static class BaseActivator<T> implements Activator<T> {
        final ServiceProvider<T> provider;
        private final ReadWriteLock instanceLock = new ReentrantReadWriteLock();

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
            /*
            At the time we are looking up instances, we also resolve them to appropriate type
            As this type represents a value within a scope, and not "instance per call", we can safely
            store the result, unless it is lookup bound
             */
            instanceLock.readLock().lock();
            try {
                if (currentPhase == Phase.ACTIVE) {
                    return targetInstances(lookup);
                }
            } finally {
                instanceLock.readLock().unlock();
            }

            instanceLock.writeLock().lock();
            try {
                if (currentPhase != Phase.ACTIVE) {
                    ActivationResult res = activate(provider.activationRequest());
                    if (res.failure()) {
                        return Optional.empty();
                    }
                }
                return targetInstances(lookup);
            } finally {
                instanceLock.writeLock().unlock();
            }
        }

        @Override
        public InjectServiceDescriptor<T> descriptor() {
            return provider.descriptor();
        }

        @Override
        public String description() {
            return provider.descriptor().serviceType().classNameWithEnclosingNames()
                    + ":" + currentPhase;
        }

        @Override
        public ActivationResult activate(ActivationRequest request) {
            // probably re-entering the same lock
            instanceLock.writeLock().lock();
            try {
                if (currentPhase == request.targetPhase()) {
                    // we are already there, just return success
                    return ActivationResult.builder()
                            .startingActivationPhase(currentPhase)
                            .finishingActivationPhase(currentPhase)
                            .targetActivationPhase(currentPhase)
                            .success(true)
                            .build();
                }
                if (currentPhase.ordinal() > request.targetPhase().ordinal()) {
                    // we are already ahead, this is a problem
                    return ActivationResult.builder()
                            .startingActivationPhase(currentPhase)
                            .finishingActivationPhase(currentPhase)
                            .targetActivationPhase(request.targetPhase())
                            .success(false)
                            .build();
                }
                return doActivate(request);
            } finally {
                instanceLock.writeLock().unlock();
            }
        }

        @Override
        public ActivationResult deactivate() {
            // probably re-entering the same lock
            instanceLock.writeLock().lock();
            try {
                ActivationResult.Builder response = ActivationResult.builder()
                        .success(true);

                if (!currentPhase.eligibleForDeactivation()) {
                    stateTransitionStart(response, Phase.DESTROYED);
                    return ActivationResult.builder()
                            .targetActivationPhase(Phase.DESTROYED)
                            .finishingActivationPhase(currentPhase)
                            .success(true)
                            .build();
                }

                response.startingActivationPhase(this.currentPhase);
                stateTransitionStart(response, Phase.PRE_DESTROYING);
                preDestroy(response);
                stateTransitionStart(response, Phase.DESTROYED);

                return response.build();
            } finally {
                instanceLock.writeLock().unlock();
            }
        }

        @Override
        public Phase phase() {
            return currentPhase;
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

        protected void setTargetInstances() {
        }

        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            return targetInstances();
        }

        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return Optional.empty();
        }

        /**
         * Check if a provider was requested as part of the lookup.
         * This is the case if:
         * <ul>
         *     <li>The lookup explicitly asks for a provider by its type</li>
         *     <li>The lookup looks for contract that is the same as this service type</li>
         *     <li>The lookup looks for service type that is the same as this service type</li>
         * </ul>
         *
         * @param lookup       requested lookup
         * @param providerType type of provider this type implements
         * @return whether the provider itself should be returned
         */
        protected boolean requestedProvider(Lookup lookup, FactoryType providerType) {
            if (lookup.factoryTypes().contains(providerType)) {
                return true;
            }
            if (lookup.contracts().size() == 1) {
                ResolvedType requestedContract = lookup.contracts().iterator().next();
                if (requestedContract.equals(ResolvedType.create(descriptor().serviceType()))) {
                    // requested actual service
                    return true;
                }
                if (descriptor().factoryContracts().contains(requestedContract)
                        && !descriptor().contracts().contains(requestedContract)) {
                    // requested a contract satisfied by the factory only
                    return true;
                }
            }
            if (lookup.serviceType().isPresent() && lookup.serviceType().get().equals(descriptor().serviceType())) {
                return true;
            }

            return false;
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
                    .startingActivationPhase(initialPhase)
                    .finishingActivationPhase(startingPhase)
                    .targetActivationPhase(targetPhase)
                    .success(true);

            if (targetPhase.ordinal() > Phase.ACTIVATION_STARTING.ordinal()
                    && initialPhase == Phase.INIT) {
                if (Phase.INIT == startingPhase
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

            List<QualifiedInstance<T>> values = List.of(QualifiedInstance.create(instance, provider.serviceInfo().qualifiers()));
            this.instances = Optional.of(values);
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return instances;
        }
    }

    static class FixedSupplierActivator<T> extends BaseActivator<T> {
        private final Supplier<Optional<List<QualifiedInstance<T>>>> instances;

        FixedSupplierActivator(ServiceProvider<T> provider, Supplier<T> instanceSupplier) {
            super(provider);

            instances = LazyValue.create(() -> {
                List<QualifiedInstance<T>> values = List.of(QualifiedInstance.create(instanceSupplier.get(),
                                                                                     provider.descriptor().qualifiers()));
                return Optional.of(values);
            });
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances() {
            return instances.get();
        }

    }

    static class FixedIpFactoryActivator<T> extends IpFactoryActivator<T> {

        public FixedIpFactoryActivator(ServiceProvider<T> provider,
                                       InjectionPointFactory<T> instance) {
            super(provider);
            serviceInstance = InstanceHolder.create(instance);
        }
    }

    static class FixedServicesFactoryActivator<T> extends ServicesFactoryActivator<T> {
        FixedServicesFactoryActivator(ServiceProvider<T> provider,
                                      Injection.ServicesFactory<T> factory) {
            super(provider);
            serviceInstance = InstanceHolder.create(factory);
        }
    }

    static class FixedQualifiedFactoryActivator<T> extends QualifiedFactoryActivator<T> {
        FixedQualifiedFactoryActivator(ServiceProvider<T> provider,
                                       Injection.QualifiedFactory<T, ?> factory) {
            super(provider, (QualifiedFactoryDescriptor) provider.descriptor());
            serviceInstance = InstanceHolder.create(factory);
        }
    }

    /**
     * {@code MyService implements Contract}.
     * Created for a service within each scope.
     */
    static class SingleServiceActivator<T> extends BaseActivator<T> {
        private final ReentrantLock lock = new ReentrantLock();
        protected InstanceHolder<T> serviceInstance;
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
            if (lock.isHeldByCurrentThread()) {
                throw new ServiceRegistryException("Cyclic dependency, attempting to obtain an instance of "
                                                           + provider.descriptor().serviceType().fqName()
                                                           + " while instantiating it");
            }
            try {
                lock.lock();
                if (serviceInstance == null) {
                    // it may have been set explicitly when creating registry
                    this.serviceInstance = InstanceHolder.create(provider, provider.injectionPlan());
                }
                this.serviceInstance.construct();
            } finally {
                lock.unlock();
            }
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

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (requestedProvider(lookup, FactoryType.SUPPLIER)) {
                // the user requested the provider, not the provided
                T instance = serviceInstance.get();
                return Optional.of(List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers())));
            }

            return super.targetInstances(lookup);
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
     * {@code MyService implements QualifiedProvider}.
     */
    static class QualifiedFactoryActivator<T> extends SingleServiceActivator<T> {
        static final GenericType<?> OBJECT_GENERIC_TYPE = GenericType.create(Object.class);

        private final TypeName supportedQualifier;
        private final Set<ResolvedType> supportedContracts;
        private final boolean anyMatch;

        QualifiedFactoryActivator(ServiceProvider<T> provider, QualifiedFactoryDescriptor qpd) {
            super(provider);
            this.supportedQualifier = qpd.qualifierType();
            this.supportedContracts = provider.descriptor()
                    .contracts()
                    .stream()
                    .filter(it -> !QualifiedFactory.TYPE.equals(it.type()))
                    .collect(Collectors.toSet());
            this.anyMatch = this.supportedContracts.contains(ResolvedType.create(TypeNames.OBJECT));
        }

        @Override
        protected void setTargetInstances() {
            // target instances cannot be created, they are resolved on each lookup
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
            var qProvider = (QualifiedFactory<T, ?>) serviceInstance.get();

            return qProvider.list(qualifier, lookup, contract);
        }
    }

    /**
     * {@code MyService implements InjectionPointProvider}.
     */
    static class IpFactoryActivator<T> extends SingleServiceActivator<T> {
        IpFactoryActivator(ServiceProvider<T> provider) {
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
            var ipProvider = (InjectionPointFactory<T>) serviceInstance.get();

            if (requestedProvider(lookup, FactoryType.INJECTION_POINT)) {
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
    static class ServicesFactoryActivator<T> extends SingleServiceActivator<T> {
        ServicesFactoryActivator(ServiceProvider<T> provider) {
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
            Injection.ServicesFactory<T> instanceSupplier = (Injection.ServicesFactory<T>) serviceInstance.get();
            targetInstances = instanceSupplier.services();
        }

        @Override
        protected Optional<List<QualifiedInstance<T>>> targetInstances(Lookup lookup) {
            if (requestedProvider(lookup, FactoryType.SERVICES)) {
                return Optional.of(List.of(QualifiedInstance.create(serviceInstance.get(), descriptor().qualifiers())));
            }

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
     * Service annotated {@link io.helidon.service.inject.api.Injection.PerInstance}.
     */
    static class PerInstanceActivator<T> extends BaseActivator<T> {
        private final InjectServiceRegistryImpl registry;
        private final ResolvedType createFor;
        private final Lookup createForLookup;
        private List<QualifiedServiceInstance<T>> serviceInstances;
        private List<QualifiedInstance<T>> targetInstances;

        PerInstanceActivator(InjectServiceRegistryImpl registry, ServiceProvider<T> provider, PerInstanceDescriptor dbd) {
            super(provider);

            this.registry = registry;
            this.createFor = ResolvedType.create(dbd.createFor());
            this.createForLookup = Lookup.builder()
                    .addContract(createFor)
                    .build();
        }

        static Map<Dependency, IpPlan<?>> updatePlan(Map<Dependency, IpPlan<?>> injectionPlan,
                                                     ServiceInstance<?> driver,
                                                     Qualifier name) {

            Set<Dependency> ips = Set.copyOf(injectionPlan.keySet());

            Set<ResolvedType> contracts = driver.contracts();

            Map<Dependency, IpPlan<?>> updatedPlan = new HashMap<>(injectionPlan);

            for (Dependency dependency : ips) {
                Ip ip = Ip.create(dependency);
                // injection point for the driving instance
                if (contracts.contains(ResolvedType.create(ip.contract()))
                        && ip.qualifiers().isEmpty()) {
                    if (ServiceInstance.TYPE.equals(ip.typeName())) {
                        // if the injection point has the same contract, no qualifiers, then it is the driving instance
                        updatedPlan.put(ip, new IpPlan<>(() -> driver, injectionPlan.get(ip).descriptors()));
                    } else {
                        // if the injection point has the same contract, no qualifiers, then it is the driving instance
                        updatedPlan.put(ip, new IpPlan<>(driver, injectionPlan.get(ip).descriptors()));
                    }
                }
                // injection point for the service name
                if (TypeNames.STRING.equals(ip.contract())) {
                    // @InstanceName String name
                    if (ip.qualifiers()
                            .stream()
                            .anyMatch(it -> Injection.InstanceName.TYPE.equals(it.typeName()))) {
                        updatedPlan.put(ip,
                                        new IpPlan<>(() -> name.value().orElse(Injection.Named.DEFAULT_NAME),
                                                     injectionPlan.get(ip).descriptors()));
                    }
                }
            }

            return updatedPlan;
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            // at this moment, we must resolve services that are driving this instance

            // we do not want to use lookup, as that is doing too much for us
            List<ServiceInstance<Object>> drivingInstances = driversFromPlan(provider.injectionPlan(), createFor)
                    .stream()
                    .map(registry::serviceManager)
                    .flatMap(it -> it.activator()
                            .instances(createForLookup)
                            .stream()
                            .flatMap(List::stream)
                            .map(qi -> it.registryInstance(createForLookup, qi)))
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
                        .forEach(InstanceHolder::preDestroy);
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

        private List<ServiceInfo> driversFromPlan(Map<Dependency, IpPlan<?>> ipSupplierMap, ResolvedType createFor) {
            // I need the list of descriptors from the injection plan
            for (Map.Entry<Dependency, IpPlan<?>> entry : ipSupplierMap.entrySet()) {
                Dependency dependency = entry.getKey();
                Ip ip = Ip.create(dependency);
                if (createFor.equals(ip.contract())
                        && ip.qualifiers().size() == 1
                        && ip.qualifiers().contains(Qualifier.WILDCARD_NAMED)) {
                    return List.of(entry.getValue().descriptors());
                }
            }
            // there is not
            return registry.servicesByContract(createFor);
        }

        private record QualifiedServiceInstance<T>(InstanceHolder<T> serviceInstance,
                                                   Set<Qualifier> qualifiers) {
            static <T> QualifiedServiceInstance<T> create(ServiceProvider<T> provider, ServiceInstance<?> driver) {
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

                Map<Dependency, IpPlan<?>> injectionPlan = updatePlan(provider.injectionPlan(), driver, name);

                return new QualifiedServiceInstance<>(InstanceHolder.create(provider, injectionPlan), newQualifiers);
            }
        }
    }

    interface InstanceHolder<T> {
        static <T> InstanceHolder<T> create(ServiceProvider<T> serviceProvider, Map<Dependency, IpPlan<?>> injectionPlan) {
            // the same instance is returned for the lifetime of the service provider
            return new InstanceHolderImpl<>(InjectionContext.create(injectionPlan),
                                            serviceProvider.interceptionMetadata(),
                                            serviceProvider.descriptor());
        }

        // we use the instance holder to hold either the actual instance,
        // or the factory; this is a place for improvement
        @SuppressWarnings("unchecked")
        static <T> InstanceHolder<T> create(Object instance) {
            return new FixedInstanceHolder<>((T) instance);
        }

        T get();

        default void construct() {
        }

        default void inject() {
        }

        default void postConstruct() {
        }

        default void preDestroy() {
        }
    }

    private static class FixedInstanceHolder<T> implements InstanceHolder<T> {
        private final T instance;

        private FixedInstanceHolder(T instance) {
            this.instance = instance;
        }

        @Override
        public T get() {
            return instance;
        }
    }

    private static class InstanceHolderImpl<T> implements InstanceHolder<T> {
        private final DependencyContext ctx;
        private final InterceptionMetadata interceptionMetadata;
        private final InjectServiceDescriptor<T> source;

        private volatile T instance;

        private InstanceHolderImpl(DependencyContext ctx,
                               InterceptionMetadata interceptionMetadata,
                               InjectServiceDescriptor<T> source) {
            this.ctx = ctx;
            this.interceptionMetadata = interceptionMetadata;
            this.source = source;
        }



        @Override
        public T get() {
            return instance;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void construct() {
            instance = (T) source.instantiate(ctx, interceptionMetadata);
        }

        @Override
        public void inject() {
            // using linked set, so we can see in debugging what was injected first
            Set<String> injected = new LinkedHashSet<>();
            source.inject(ctx, interceptionMetadata, injected, instance);
        }

        @Override
        public void postConstruct() {
            source.postConstruct(instance);
        }

        @Override
        public void preDestroy() {
            source.preDestroy(instance);
        }
    }

    private static class MissingDescribedActivator<T> implements Supplier<Activator<T>> {
        private static final System.Logger LOGGER = System.getLogger(Activators.class.getName());
        private final String serviceType;

        private MissingDescribedActivator(ServiceProvider<T> provider) {
            this.serviceType = provider.serviceInfo().serviceType().fqName();

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "The registry knows of a descriptor that was generated on demand "
                                   + "(@" + Injection.Describe.class.getName() + "), which expects an instance configured for "
                                   + "it, either"
                                   + " when creating the registry through configuration, or when creating a scope. "
                                   + "Service that does not have an instance registered: "
                                   + serviceType
                                   + ", if there is an attempt on injecting this type, a runtime exception "
                                   + "will be thrown");
            }

        }

        @Override
        public Activator<T> get() {
            throw new ServiceRegistryException("Instance for " + serviceType + " must be provided explicitly "
                                                       + "either during startup, or when creating a scope."
                                                       + " Cannot provide an instance for a descriptor without a service.");
        }
    }
}
