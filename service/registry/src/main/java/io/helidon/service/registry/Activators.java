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

package io.helidon.service.registry;

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
import io.helidon.service.registry.GeneratedService.PerInstanceDescriptor;
import io.helidon.service.registry.GeneratedService.QualifiedFactoryDescriptor;
import io.helidon.service.registry.Service.InjectionPointFactory;
import io.helidon.service.registry.Service.QualifiedFactory;
import io.helidon.service.registry.Service.QualifiedInstance;

import static java.util.function.Predicate.not;

/*
 Developer note: when changing this, change ActivatorsPerLookup as well
 */

/**
 * Activator types for various types the users can implement, for real scopes (singleton, request scope).
 *
 * @see io.helidon.service.registry.ActivatorsPerLookup
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
            case SERVICES -> new Activators.FixedServicesFactoryActivator<>(provider, (Service.ServicesFactory<T>) instance);
            case INJECTION_POINT -> new Activators.FixedIpFactoryActivator<>(provider, (InjectionPointFactory<T>) instance);
            case QUALIFIED -> new Activators.FixedQualifiedFactoryActivator<>(provider, (QualifiedFactory<T, ?>) instance);
        };
    }

    static <T> Supplier<Activator<T>> create(CoreServiceRegistry registry, ServiceProvider<T> provider) {
        ServiceDescriptor<T> descriptor = provider.descriptor();
        // as this is going to create an instance of the service, we need the dependency context
        var bindingPlan = registry.bindings().bindingPlan(descriptor);
        DependencyContext dependencyContext = new BindingDependencyContext(bindingPlan);

        if (descriptor.scope().equals(Service.PerLookup.TYPE)) {
            return switch (descriptor.factoryType()) {
                case NONE -> new MissingDescribedActivator<>(provider);
                case SERVICE -> {
                    if (descriptor instanceof PerInstanceDescriptor dbd) {
                        yield () -> new ActivatorsPerLookup.PerInstanceActivator<>(registry,
                                                                                   dependencyContext,
                                                                                   bindingPlan,
                                                                                   provider,
                                                                                   dbd);
                    }
                    yield () -> new ActivatorsPerLookup.SingleServiceActivator<>(provider, dependencyContext);
                }
                case SUPPLIER -> () -> new ActivatorsPerLookup.SupplierActivator<>(provider, dependencyContext);
                case SERVICES -> () -> new ActivatorsPerLookup.ServicesFactoryActivator<>(provider, dependencyContext);
                case INJECTION_POINT -> () -> new ActivatorsPerLookup.IpFactoryActivator<>(provider, dependencyContext);
                case QUALIFIED -> () ->
                        new ActivatorsPerLookup.QualifiedFactoryActivator<>(provider,
                                                                            dependencyContext,
                                                                            (QualifiedFactoryDescriptor) descriptor);
            };
        } else {
            return switch (descriptor.factoryType()) {
                case NONE -> new MissingDescribedActivator<>(provider);
                case SERVICE -> {
                    if (descriptor instanceof PerInstanceDescriptor dbd) {
                        yield () -> new PerInstanceActivator<>(registry, provider, dependencyContext, bindingPlan, dbd);
                    }
                    yield () -> new Activators.SingleServiceActivator<>(provider, dependencyContext);
                }
                case SUPPLIER -> () -> new Activators.SupplierActivator<>(provider, dependencyContext);
                case SERVICES -> () -> new ServicesFactoryActivator<>(provider, dependencyContext);
                case INJECTION_POINT -> () -> new IpFactoryActivator<>(provider, dependencyContext);
                case QUALIFIED -> () ->
                        new QualifiedFactoryActivator<>(provider,
                                                        dependencyContext,
                                                        (QualifiedFactoryDescriptor) descriptor);
            };
        }
    }

    interface InstanceHolder<T> {
        static <T> InstanceHolder<T> create(ServiceProvider<T> serviceProvider, DependencyContext dependencyContext) {
            // the same instance is returned for the lifetime of the service provider
            return new InstanceHolderImpl<>(dependencyContext,
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

    abstract static class BaseActivator<T> implements Activator<T> {
        final ServiceProvider<T> provider;
        final DependencyContext dependencyContext;

        private final ReadWriteLock instanceLock = new ReentrantReadWriteLock();

        volatile ActivationPhase currentPhase = ActivationPhase.INIT;

        BaseActivator(ServiceProvider<T> provider, DependencyContext dependencyContext) {
            this.provider = provider;
            this.dependencyContext = dependencyContext;
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
                if (currentPhase == ActivationPhase.ACTIVE) {
                    return targetInstances(lookup);
                }
            } finally {
                instanceLock.readLock().unlock();
            }

            instanceLock.writeLock().lock();
            try {
                if (currentPhase != ActivationPhase.ACTIVE) {
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
                    stateTransitionStart(response, ActivationPhase.DESTROYED);
                    return ActivationResult.builder()
                            .targetActivationPhase(ActivationPhase.DESTROYED)
                            .finishingActivationPhase(currentPhase)
                            .success(true)
                            .build();
                }

                response.startingActivationPhase(this.currentPhase);
                stateTransitionStart(response, ActivationPhase.PRE_DESTROYING);
                preDestroy(response);
                stateTransitionStart(response, ActivationPhase.DESTROYED);

                return response.build();
            } finally {
                instanceLock.writeLock().unlock();
            }
        }

        @Override
        public ActivationPhase phase() {
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

        private void stateTransitionStart(ActivationResult.Builder res, ActivationPhase phase) {
            res.finishingActivationPhase(phase);
            this.currentPhase = phase;
        }

        private ActivationResult doActivate(ActivationRequest request) {
            ActivationPhase initialPhase = this.currentPhase;
            ActivationPhase startingPhase = request.startingPhase().orElse(initialPhase);
            ActivationPhase targetPhase = request.targetPhase();
            this.currentPhase = startingPhase;
            ActivationPhase finishingPhase = startingPhase;

            ActivationResult.Builder response = ActivationResult.builder()
                    .startingActivationPhase(initialPhase)
                    .finishingActivationPhase(startingPhase)
                    .targetActivationPhase(targetPhase)
                    .success(true);

            if (targetPhase.ordinal() > ActivationPhase.ACTIVATION_STARTING.ordinal()
                    && initialPhase == ActivationPhase.INIT) {
                if (ActivationPhase.INIT == startingPhase
                        || ActivationPhase.ACTIVATION_STARTING == startingPhase
                        || ActivationPhase.DESTROYED == startingPhase) {
                    stateTransitionStart(response, ActivationPhase.ACTIVATION_STARTING);
                }
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= ActivationPhase.CONSTRUCTING.ordinal()) {
                stateTransitionStart(response, ActivationPhase.CONSTRUCTING);
                construct(response);
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= ActivationPhase.INJECTING.ordinal()
                    && (ActivationPhase.CONSTRUCTING == finishingPhase)) {
                stateTransitionStart(response, ActivationPhase.INJECTING);
                inject(response);
            }

            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= ActivationPhase.POST_CONSTRUCTING.ordinal()
                    && (ActivationPhase.INJECTING == finishingPhase)) {
                stateTransitionStart(response, ActivationPhase.POST_CONSTRUCTING);
                postConstruct(response);
            }
            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= ActivationPhase.ACTIVATION_FINISHING.ordinal()
                    && (ActivationPhase.POST_CONSTRUCTING == finishingPhase)) {
                stateTransitionStart(response, ActivationPhase.ACTIVATION_FINISHING);
                finishActivation(response);
            }
            finishingPhase = response.finishingActivationPhase().orElse(finishingPhase);
            if (response.targetActivationPhase().ordinal() >= ActivationPhase.ACTIVE.ordinal()
                    && (ActivationPhase.ACTIVATION_FINISHING == finishingPhase)) {
                stateTransitionStart(response, ActivationPhase.ACTIVE);
            }

            if (startingPhase.ordinal() < ActivationPhase.CONSTRUCTING.ordinal()
                    && currentPhase.ordinal() >= ActivationPhase.CONSTRUCTING.ordinal()) {
                setTargetInstances();
            }

            return response.build();
        }
    }

    static class FixedActivator<T> extends BaseActivator<T> {
        private final Optional<List<QualifiedInstance<T>>> instances;

        FixedActivator(ServiceProvider<T> provider, T instance) {
            super(provider, null);

            List<QualifiedInstance<T>> values = List.of(QualifiedInstance.create(instance, provider.descriptor().qualifiers()));
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
            super(provider, null);

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

        FixedIpFactoryActivator(ServiceProvider<T> provider,
                                InjectionPointFactory<T> instance) {
            super(provider, null);
            serviceInstance = InstanceHolder.create(instance);
        }
    }

    static class FixedServicesFactoryActivator<T> extends ServicesFactoryActivator<T> {
        FixedServicesFactoryActivator(ServiceProvider<T> provider,
                                      Service.ServicesFactory<T> factory) {
            super(provider, null);
            serviceInstance = InstanceHolder.create(factory);
        }
    }

    static class FixedQualifiedFactoryActivator<T> extends QualifiedFactoryActivator<T> {
        FixedQualifiedFactoryActivator(ServiceProvider<T> provider,
                                       QualifiedFactory<T, ?> factory) {
            super(provider, null, (QualifiedFactoryDescriptor) provider.descriptor());
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

        SingleServiceActivator(ServiceProvider<T> provider, DependencyContext dependencyContext) {
            super(provider, dependencyContext);
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
                    this.serviceInstance = InstanceHolder.create(provider, dependencyContext);
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
        SupplierActivator(ServiceProvider<T> provider, DependencyContext dependencyContext) {
            super(provider, dependencyContext);
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
            T value = instanceSupplier.get();
            if (value instanceof Optional opt) {
                if (opt.isPresent()) {
                    value = (T) opt.get();
                    this.targetInstances = List.of(QualifiedInstance.create(value,
                                                                            provider.descriptor().qualifiers()));
                } else {
                    this.targetInstances = List.of();
                }
            } else {
                this.targetInstances = List.of(QualifiedInstance.create(value,
                                                                        provider.descriptor().qualifiers()));
            }
        }
    }

    /**
     * {@code MyService implements QualifiedProvider}.
     */
    static class QualifiedFactoryActivator<T> extends SingleServiceActivator<T> {
        private final TypeName supportedQualifier;
        private final Set<ResolvedType> supportedContracts;
        private final boolean anyMatch;

        QualifiedFactoryActivator(ServiceProvider<T> provider,
                                  DependencyContext dependencyContext,
                                  QualifiedFactoryDescriptor qpd) {
            super(provider, dependencyContext);
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
                    Optional<GenericType<?>> genericType = lookup.dependency()
                            .map(Dependency::contractType);
                    GenericType contract = genericType
                            .or(lookup::contractType)
                            .orElse(GenericType.OBJECT);

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
        IpFactoryActivator(ServiceProvider<T> provider, DependencyContext dependencyContext) {
            super(provider, dependencyContext);
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
        ServicesFactoryActivator(ServiceProvider<T> provider, DependencyContext dependencyContext) {
            super(provider, dependencyContext);
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
            Service.ServicesFactory<T> instanceSupplier = (Service.ServicesFactory<T>) serviceInstance.get();
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
     * Service annotated {@link Service.PerInstance}.
     */
    static class PerInstanceActivator<T> extends BaseActivator<T> {
        private final CoreServiceRegistry registry;
        private final ResolvedType createFor;
        private final Lookup createForLookup;
        private final Bindings.ServiceBindingPlan bindingPlan;

        private List<QualifiedServiceInstance<T>> serviceInstances;
        private List<QualifiedInstance<T>> targetInstances;

        PerInstanceActivator(CoreServiceRegistry registry,
                             ServiceProvider<T> provider,
                             DependencyContext dependencyContext,
                             Bindings.ServiceBindingPlan bindingPlan,
                             PerInstanceDescriptor dbd) {
            super(provider, dependencyContext);

            this.registry = registry;
            this.bindingPlan = bindingPlan;
            this.createFor = ResolvedType.create(dbd.createFor());
            this.createForLookup = Lookup.builder()
                    .addContract(createFor)
                    .build();
        }

        static DependencyContext updatePlan(Bindings.ServiceBindingPlan injectionPlan,
                                            DependencyContext dependencyContext,
                                            ServiceInstance<?> driver,
                                            Qualifier name) {

            Set<ResolvedType> contracts = driver.contracts();

            Map<Dependency, Supplier<Object>> targetPlan = new HashMap<>();

            for (Bindings.DependencyBinding binding : injectionPlan.allBindings()) {
                Dependency dependency = binding.dependency();

                boolean updated = false;
                // injection point for the driving instance
                if (contracts.contains(ResolvedType.create(dependency.contract()))
                        && dependency.qualifiers().isEmpty()) {
                    updated = true;
                    // if the injection point has the same contract, no qualifiers, then it is the driving instance
                    if (dependency.isServiceInstance()) {
                        // return ServiceInstance
                        targetPlan.put(dependency, () -> driver);
                    } else {
                        // return instance
                        targetPlan.put(dependency, driver::get);
                    }
                }
                // injection point for the service name
                if (TypeNames.STRING.equals(dependency.contract())) {
                    // @InstanceName String name
                    if (dependency.qualifiers()
                            .stream()
                            .anyMatch(it -> Service.InstanceName.TYPE.equals(it.typeName()))) {
                        updated = true;
                        targetPlan.put(dependency, () -> name.value().orElse(Service.Named.DEFAULT_NAME));
                    }
                }

                if (!updated) {
                    // fallback to original instance
                    targetPlan.put(dependency, () -> dependencyContext.dependency(dependency));
                }
            }

            return new SupplierDependencyContext(targetPlan);
        }

        @Override
        protected void construct(ActivationResult.Builder response) {
            // at this moment, we must resolve services that are driving this instance

            // we do not want to use lookup, as that is doing too much for us
            List<ServiceInstance<Object>> drivingInstances = driversFromPlan(bindingPlan, createFor)
                    .stream()
                    .map(registry::serviceManager)
                    .flatMap(it -> it.activator()
                            .instances(createForLookup)
                            .stream()
                            .flatMap(List::stream)
                            .map(qi -> it.registryInstance(createForLookup, qi)))
                    .toList();

            serviceInstances = drivingInstances.stream()
                    .map(it -> QualifiedServiceInstance.create(provider, bindingPlan, dependencyContext, it))
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

        private List<ServiceInfo> driversFromPlan(Bindings.ServiceBindingPlan ipSupplierMap, ResolvedType createFor) {
            // I need the list of descriptors from the injection plan
            for (Bindings.DependencyBinding binding : ipSupplierMap.allBindings()) {
                Dependency dependency = binding.dependency();
                if (createFor.equals(ResolvedType.create(dependency.contract()))
                        && dependency.qualifiers().size() == 1
                        && dependency.qualifiers().contains(Qualifier.WILDCARD_NAMED)) {
                    return binding.descriptors();
                }
            }

            // there is not
            return registry.servicesByContract(createFor);
        }

        private record QualifiedServiceInstance<T>(InstanceHolder<T> serviceInstance,
                                                   Set<Qualifier> qualifiers) {
            static <T> QualifiedServiceInstance<T> create(ServiceProvider<T> provider,
                                                          Bindings.ServiceBindingPlan bindingPlan,
                                                          DependencyContext dependencyContext,
                                                          ServiceInstance<?> driver) {
                Set<Qualifier> qualifiers = driver.qualifiers();
                Qualifier name = qualifiers.stream()
                        .filter(not(Qualifier.WILDCARD_NAMED::equals))
                        .filter(it -> Service.Named.TYPE.equals(it.typeName()))
                        .findFirst()
                        .orElse(Qualifier.DEFAULT_NAMED);
                Set<Qualifier> newQualifiers = provider.descriptor().qualifiers()
                        .stream()
                        .filter(not(Qualifier.WILDCARD_NAMED::equals))
                        .collect(Collectors.toSet());
                newQualifiers.add(name);

                DependencyContext targetDependencyContext = updatePlan(bindingPlan, dependencyContext, driver, name);

                return new QualifiedServiceInstance<>(InstanceHolder.create(provider, targetDependencyContext), newQualifiers);
            }
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
        private final ServiceDescriptor<T> source;

        private volatile T instance;

        private InstanceHolderImpl(DependencyContext ctx,
                                   InterceptionMetadata interceptionMetadata,
                                   ServiceDescriptor<T> source) {
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
            this.serviceType = provider.descriptor().serviceType().fqName();

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "The registry knows of a descriptor that was generated on demand "
                                   + "(@" + Service.Describe.class.getName() + "), which expects an instance configured for "
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
