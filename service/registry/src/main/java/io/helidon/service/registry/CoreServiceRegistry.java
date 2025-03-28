/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.LruCache;
import io.helidon.common.Weighted;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.ServiceSupplies.ServiceSupplyList;

import static io.helidon.service.registry.LookupTrace.traceLookup;
import static io.helidon.service.registry.ServiceRegistryManager.SERVICE_INFO_COMPARATOR;

/**
 * Basic implementation of the service registry with simple dependency support.
 */
class CoreServiceRegistry implements ServiceRegistry, Scopes {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final String id = String.valueOf(COUNTER.incrementAndGet());

    private final RegistryMetricsImpl metrics = new RegistryMetricsImpl();
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Lock stateReadLock = stateLock.readLock();
    private final Lock stateWriteLock = stateLock.writeLock();

    // map of scope annotation to service info
    private final Map<TypeName, ServiceInfo> scopeHandlerServices;
    // map of service implementation class to service info
    private final Map<TypeName, ServiceInfo> servicesByType;
    // map of provided contracts to service info(s)
    private final Map<ResolvedType, Set<ServiceInfo>> servicesByContract;
    // map of qualifier annotations to service info(s)
    private final Map<TypeName, Set<ServiceInfo>> qualifiedProvidersByQualifier;
    // map of qualifier annotations and resolved type combination to service info(s)
    private final Map<ServiceRegistryManager.TypedQualifiedProviderKey, Set<ServiceInfo>> typedQualifiedProviders;
    private final Map<ResolvedType, AtomicBoolean> accessedContracts;

    private final boolean cacheEnabled;
    private final LruCache<Lookup, List<ServiceInfo>> cache;

    private final LazyValue<Scope> singletonScope = LazyValue
            .create(() -> createScope(Service.Singleton.TYPE, Optional::empty, id, Map.of()));

    private final LazyValue<Scope> perLookupScope = LazyValue
            .create(() -> createScope(Service.PerLookup.TYPE, Optional::empty, id, Map.of()));
    private final Map<TypeName, Service.ScopeHandler> scopeHandlerInstances = new HashMap<>();
    private final Lock scopeHandlerInstancesLock = new ReentrantLock();
    private final boolean interceptionEnabled;
    private final InterceptionMetadata interceptionMetadata;
    private final ServiceManager<String> serviceManagerForInstanceName;

    // runtime fields (to obtain actual service instances)
    // service descriptor to its manager
    private final Map<ServiceInfo, ServiceManager<?>> servicesByDescriptor = new IdentityHashMap<>();
    private final ActivationRequest activationRequest;

    private final Bindings bindings;
    private final boolean allowLateBinding;

    private Map<ServiceInfo, ServiceManager<Interception.Interceptor>> interceptors;

    @SuppressWarnings("unchecked")
    CoreServiceRegistry(ServiceRegistryConfig config,
                        Set<ServiceDescriptor<?>> descriptors,
                        Map<TypeName, ServiceInfo> scopeHandlers,
                        Map<ServiceInfo, Object> explicitInstances,
                        Map<TypeName, ServiceInfo> servicesByType,
                        Map<ResolvedType, Set<ServiceInfo>> servicesByContract,
                        Map<TypeName, Set<ServiceInfo>> qualifiedProvidersByQualifier,
                        Map<ServiceRegistryManager.TypedQualifiedProviderKey, Set<ServiceInfo>> typedQualifiedProviders,
                        Map<ResolvedType, AtomicBoolean> accessedContracts) {
        this.accessedContracts = Map.copyOf(accessedContracts);
        this.interceptionEnabled = config.interceptionEnabled();
        // this is a bit tricky - we are leaking our instance that is not yet finished, so it must
        // not be accessed in the constructor!
        this.interceptionMetadata = interceptionEnabled
                ? InterceptionMetadataImpl.create(this)
                : InterceptionMetadataImpl.noop();
        // again - we leak our instance early, but it is not used until runtime from bindings
        this.bindings = new Bindings(this);
        this.allowLateBinding = config.allowLateBinding();

        // these must be bound here, as the instance exists now
        // (and we do not want to allow post-constructor binding)
        explicitInstances.put(Scopes__ServiceDescriptor.INSTANCE, this);
        explicitInstances.put(ServiceRegistry__ServiceDescriptor.INSTANCE, this);
        explicitInstances.put(InterceptionMetadata__ServiceDescriptor.INSTANCE, interceptionMetadata);
        // nobody can replace these
        accessed(Scopes__ServiceDescriptor.INSTANCE);
        accessed(ServiceRegistry__ServiceDescriptor.INSTANCE);
        accessed(InterceptionMetadata__ServiceDescriptor.INSTANCE);

        this.cacheEnabled = config.lookupCacheEnabled();
        this.cache = cacheEnabled ? LruCache.create(config.lookupCacheSize()) : null;

        this.scopeHandlerServices = scopeHandlers;
        this.servicesByType = new HashMap<>(servicesByType);
        this.servicesByContract = new HashMap<>(servicesByContract);
        this.qualifiedProvidersByQualifier = qualifiedProvidersByQualifier;
        this.typedQualifiedProviders = typedQualifiedProviders;
        this.activationRequest = ActivationRequest.builder()
                .targetPhase(config.limitActivationPhase())
                .build();

        /*
        For each known service descriptor, create an appropriate service manager
         */
        descriptors.forEach(descriptor -> {
            bindings.register(descriptor);

            Object instance = explicitInstances.get(descriptor);
            ServiceProvider<Object> provider = new ServiceProvider<>(
                    this,
                    (ServiceDescriptor<Object>) descriptor);

            if (instance != null) {
                Activator<Object> activator = Activators.create(provider, instance);
                servicesByDescriptor.put(descriptor,
                                         new ServiceManager<>(this,
                                                              scopeSupplier(descriptor),
                                                              provider,
                                                              true,
                                                              () -> activator));
            } else {
                // we must always prefer explicit instances - so if specified, we will never override it
                servicesByDescriptor.putIfAbsent(descriptor,
                                                 new ServiceManager<>(this,
                                                                      scopeSupplier(descriptor),
                                                                      provider,
                                                                      false,
                                                                      Activators.create(this, provider)));
            }
        });

        this.serviceManagerForInstanceName = new InstanceNameServiceManager(this);
    }

    @Override
    public <T> T get(TypeName contract) {
        return get(Lookup.create(contract));
    }

    @Override
    public <T> T get(Lookup lookup) {
        return this.<T>supply(lookup).get();
    }

    @Override
    public <T> Supplier<T> supply(TypeName contract) {
        return supply(Lookup.create(contract));
    }

    @Override
    public <T> Supplier<T> supply(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);
        if (managers.isEmpty()) {
            throw new ServiceRegistryException("There is no service in registry that matches this lookup: " + lookup);
        }
        return new ServiceSupplies.ServiceSupply<>(lookup, managers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> get(ServiceInfo serviceInfo) {
        ServiceManager<?> serviceManager = servicesByDescriptor.get(serviceInfo);
        if (serviceManager == null) {
            return Optional.empty();
        }
        return (Optional<T>) new ServiceSupplies.ServiceSupplyOptional<>(Lookup.EMPTY, List.of(serviceManager(serviceInfo)))
                .get();
    }

    @Override
    public <T> Optional<T> first(TypeName contract) {
        return first(Lookup.create(contract));
    }

    @Override
    public <T> Optional<T> first(Lookup lookup) {
        return this.<T>supplyFirst(lookup).get();
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(TypeName contract) {
        return supplyFirst(Lookup.create(contract));
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return Optional::empty;
        }
        return new ServiceSupplies.ServiceSupplyOptional<>(lookup, managers);
    }

    @Override
    public <T> List<T> all(TypeName contract) {
        return all(Lookup.create(contract));
    }

    @Override
    public <T> List<T> all(Lookup lookup) {
        return this.<T>supplyAll(lookup).get();
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(TypeName contract) {
        return supplyAll(Lookup.create(contract));
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return List::of;
        }
        return new ServiceSupplyList<>(lookup, managers);
    }

    @Override
    public List<ServiceInfo> allServices(TypeName contract) {
        return lookupServices(Lookup.create(contract));
    }

    @Override
    public List<ServiceInfo> lookupServices(Lookup lookup) {
        try {
            stateReadLock.lock();
            // a very special lookup
            if (lookup.qualifiers().contains(Qualifier.CREATE_FOR_NAME)) {
                checkCreateForName(lookup);
                return List.of(InstanceName__ServiceDescriptor.INSTANCE);
            }

            metrics.lookup();
            traceLookup(lookup, "start: {0}", lookup);

            if (cacheEnabled) {
                List<ServiceInfo> cacheResult = cache.get(lookup)
                        .orElse(null);
                metrics.cacheAccess();
                if (cacheResult != null) {
                    traceLookup(lookup, "from cache", cacheResult);
                    metrics.cacheHit();
                    return cacheResult;
                }
            }

            List<ServiceInfo> result = new ArrayList<>();

            if (lookup.serviceType().isPresent()) {
                // when a specific service type is requested, we go for it
                ServiceInfo exact = servicesByType.get(lookup.serviceType().get());
                if (exact != null) {
                    traceLookup(lookup, "by service type", result);
                    result.add(exact);
                    return result;
                }
            }

            if (1 == lookup.contracts().size()) {
                // a single contract is requested, we are ready for this ("indexed by contract")
                ResolvedType theOnlyContractRequested = lookup.contracts().iterator().next();
                Set<ServiceInfo> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
                if (subsetOfMatches != null) {
                    // the subset is ordered, cannot use parallel, also no need to re-order
                    subsetOfMatches.stream()
                            .filter(lookup::matches)
                            .forEach(result::add);
                    if (!result.isEmpty()) {
                        traceLookup(lookup, "by single contract", result);
                        if (cacheEnabled) {
                            cache.put(lookup, result);
                        }

                        return result;
                    }
                }
            }

            // table scan :-(
            metrics.fullScan();
            // we need to go through each service descriptor if it matches
            servicesByDescriptor.keySet()
                    .stream()
                    .filter(lookup::matches)
                    .sorted(SERVICE_INFO_COMPARATOR)
                    .forEach(result::add);
            traceLookup(lookup, "from full table scan", result);

            if (result.isEmpty() && !lookup.qualifiers().isEmpty()) {
                // check qualified providers
                if (lookup.contracts().size() == 1) {
                    ResolvedType contract = lookup.contracts().iterator().next();
                    for (Qualifier qualifier : lookup.qualifiers()) {
                        TypeName qualifierType = qualifier.typeName();
                        Set<ServiceInfo> found = typedQualifiedProviders.get(new ServiceRegistryManager.TypedQualifiedProviderKey(
                                qualifierType,
                                contract));
                        if (found != null) {
                            traceLookup(lookup, "from typed qualified providers", found);
                            result.addAll(found);
                        }
                        found = qualifiedProvidersByQualifier.get(qualifierType);
                        if (found != null) {
                            traceLookup(lookup, "from typed qualified providers", found);
                            result.addAll(found);
                        }
                    }
                }
            }

            if (cacheEnabled) {
                cache.put(lookup, result);
            }

            traceLookup(lookup, "full result", result);
            return result;
        } finally {
            stateReadLock.unlock();
        }
    }

    @Override
    public <T> List<ServiceInstance<T>> lookupInstances(Lookup lookup) {
        Lookup instanceLookup;
        if (lookup.factoryTypes().isEmpty() && lookup.serviceType().isPresent()) {
            // in case the factories are not requested, but a specific service type is, we only use
            // service manager for that service, but we want instances, not the factory itself
            instanceLookup = Lookup.builder(lookup)
                    .clearServiceType()
                    .build();
        } else {
            instanceLookup = lookup;
        }
        return new ServiceSupplies.ServiceInstanceSupplyList<T>(instanceLookup, lookupManagers(lookup))
                .get();
    }

    @Override
    public Scope createScope(TypeName scopeType, String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        return createScope(scopeType,
                           scopeHandler(scopeType),
                           id,
                           initialBindings);
    }

    @Override
    public RegistryMetrics metrics() {
        return metrics;
    }

    Bindings bindings() {
        return bindings;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void add(ServiceDescriptor descriptor) {
        if (!allowLateBinding) {
            throw new ServiceRegistryException("This service registry instance does not support late binding, as it was "
                                                       + "explicitly disabled through registry configuration: " + id);
        }
        stateWriteLock.lock();
        try {
            Set<ResolvedType> contracts = descriptor.contracts();
            contracts.forEach(this::checkValidContract);

            ServiceProvider<Object> provider = new ServiceProvider<>(this,
                                                                     descriptor);
            Supplier<Activator<Object>> activator = Activators.create(this, provider);
            servicesByDescriptor.put(descriptor, new ServiceManager<>(this,
                                                                      scopeSupplier(descriptor),
                                                                      provider,
                                                                      true,
                                                                      activator));

            for (ResolvedType contract : contracts) {
                ServiceInfo serviceInfo = servicesByType.get(contract.type());
                if (serviceInfo != null) {
                    throw new ServiceRegistryException("Cannot add a custom service descriptor for a service implementation: "
                                                               + contract.type());
                }

                Set<ServiceInfo> serviceInfos = new TreeSet<>(SERVICE_INFO_COMPARATOR);
                var existing = servicesByContract.get(contract);
                if (existing != null) {
                    // we may add new contracts in case somebody injects Object; this should only be done
                    // for contracts already known by the registry
                    serviceInfos.addAll(existing);
                }

                serviceInfos.add(descriptor);

                // replace the instances
                servicesByContract.put(contract, serviceInfos);
                // reset bindings, as build-time binding would ignore instances explicitly set
                bindings.forgetContract(contract);
            }
        } finally {
            stateWriteLock.unlock();
        }
    }

    <T> void add(Class<T> contract, double weight, T instance) {
        if (!allowLateBinding) {
            throw new ServiceRegistryException("This service registry instance does not support late binding, as it was "
                                                       + "explicitly disabled through registry configuration: " + id);
        }
        stateWriteLock.lock();
        try {
            ResolvedType contractType = ResolvedType.create(contract);
            checkValidContract(contractType);
            ServiceInfo serviceInfo = servicesByType.get(contractType.type());
            if (serviceInfo == null) {
                Set<ServiceInfo> serviceInfos = new TreeSet<>(SERVICE_INFO_COMPARATOR);
                Set<ServiceInfo> currentInfos = servicesByContract.get(contractType);
                if (currentInfos != null) {
                    serviceInfos.addAll(currentInfos);
                }

                // each instance will have its own descriptor
                VirtualDescriptor vt = new VirtualDescriptor(contractType.type(), weight, instance);
                ServiceProvider<Object> provider = new ServiceProvider<>(this,
                                                                         vt);
                Activator<Object> activator = Activators.create(provider, instance);

                servicesByDescriptor.put(vt, new ServiceManager<>(this,
                                                                  scopeSupplier(vt),
                                                                  provider,
                                                                  true,
                                                                  () -> activator));
                serviceInfos.add(vt);

                // replace the instances
                servicesByContract.put(contractType, serviceInfos);
            } else {
                throw new ServiceRegistryException("Cannot add a service instance for service implementation: "
                                                           + contract.getName());
            }
            // reset bindings, as build-time binding would ignore instances explicitly set
            bindings.forgetContract(contractType);
        } finally {
            stateWriteLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    <T> void set(Class<T> contract, T[] instances) {
        if (!allowLateBinding) {
            throw new ServiceRegistryException("This service registry instance does not support late binding, as it was "
                                                       + "explicitly disabled through registry configuration: " + id);
        }

        stateWriteLock.lock();
        try {
            ResolvedType contractType = ResolvedType.create(contract);
            checkValidContract(contractType);
            ServiceInfo serviceInfo = servicesByType.get(contractType.type());
            if (serviceInfo == null) {
                Set<ServiceInfo> serviceInfos = new TreeSet<>(SERVICE_INFO_COMPARATOR);

                // we need to keep order of the instances; if somebody calls set, and then add, it may be tricky
                double currentWeight = Weighted.DEFAULT_WEIGHT;
                for (T instance : instances) {
                    // each instance will have its own descriptor
                    VirtualDescriptor vt = new VirtualDescriptor(contractType.type(), currentWeight, instance);
                    ServiceProvider<Object> provider = new ServiceProvider<>(this,
                                                                             vt);
                    Activator<Object> activator = Activators.create(provider, instance);

                    servicesByDescriptor.put(vt, new ServiceManager<>(this,
                                                                      scopeSupplier(vt),
                                                                      provider,
                                                                      true,
                                                                      () -> activator));
                    serviceInfos.add(vt);
                    // reduce by a small number, so other things behave as expected
                    currentWeight -= 0.001;
                }
                // replace the instances
                servicesByContract.put(contractType, serviceInfos);
            } else {
                // this is a service instance, not contract implementation (i.e. the contract is actual service class)
                ServiceProvider<Object> provider = new ServiceProvider<>(this,
                                                                         (ServiceDescriptor<Object>) serviceInfo);
                if (instances.length != 1) {
                    throw new ServiceRegistryException("Attempting to set a service provider with wrong number of instances. "
                                                               + "A service provider must have exactly one instance.");
                }
                Activator<Object> activator = Activators.create(provider, instances[0]);
                servicesByDescriptor.put(serviceInfo, new ServiceManager<>(this,
                                                                           scopeSupplier(serviceInfo),
                                                                           provider,
                                                                           true,
                                                                           () -> activator));
            }
            // reset bindings, as build-time binding would ignore instances explicitly set
            bindings.forgetContract(contractType);
        } finally {
            stateWriteLock.unlock();
        }
    }

    InterceptionMetadata interceptionMetadata() {
        return interceptionMetadata;
    }

    ActivationRequest activationRequest() {
        return activationRequest;
    }

    List<ServiceInfo> servicesByContract(ResolvedType contract) {
        Set<ServiceInfo> serviceInfos = servicesByContract.get(contract);
        if (serviceInfos == null) {
            return List.of();
        }
        return serviceInfos.stream()
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    <T> ServiceManager<T> serviceManager(ServiceInfo info) {
        if (info == InstanceName__ServiceDescriptor.INSTANCE) {
            return (ServiceManager<T>) serviceManagerForInstanceName;
        }
        ServiceManager<T> result = (ServiceManager<T>) servicesByDescriptor.get(info);
        if (result == null) {
            throw new ServiceRegistryException("Attempt to use service info not managed by this registry: " + info);
        }
        return result;
    }

    void interceptors(ServiceInfo... serviceInfos) {
        if (!interceptionEnabled) {
            return;
        }
        try {
            stateWriteLock.lock();
            if (this.interceptors == null) {
                this.interceptors = new LinkedHashMap<>();
            }
            Set<ServiceInfo> ordered = new TreeSet<>(SERVICE_INFO_COMPARATOR);
            for (ServiceInfo serviceInfo : serviceInfos) {
                ServiceManager<Object> serviceManager = this.serviceManager(serviceInfo);
                ordered.add(serviceManager.descriptor());
            }

            // there may be more than one application, we need to add to existing
            for (ServiceInfo serviceInfo : ordered) {
                this.interceptors.computeIfAbsent(serviceInfo,
                                                  this::serviceManager);
            }
        } finally {
            stateWriteLock.unlock();
        }
    }

    void shutdown() {
        singletonScope.get()
                .close();
    }

    <T> List<ServiceManager<T>> lookupManagers(Lookup lookup) {
        List<ServiceManager<T>> result = new ArrayList<>();

        for (ServiceInfo service : lookupServices(lookup)) {
            result.add(serviceManager(service));
            accessed(service);
        }

        return result;
    }

    List<ServiceManager<Interception.Interceptor>> interceptors() {
        try {
            stateReadLock.lock();
            if (interceptors != null) {
                return List.copyOf(interceptors.values());
            }
        } finally {
            stateReadLock.unlock();
        }
        try {
            stateWriteLock.lock();
            if (interceptors == null) {
                // we must preserve the order of services, as they are weight ordered!
                this.interceptors = new LinkedHashMap<>();
                List<ServiceManager<Interception.Interceptor>> serviceManagers =
                        lookupManagers(Lookup.builder()
                                               .addContract(Interception.Interceptor.class)
                                               .addQualifier(Qualifier.WILDCARD_NAMED)
                                               .build());
                for (ServiceManager<Interception.Interceptor> serviceManager : serviceManagers) {
                    this.interceptors.put(serviceManager.descriptor(), serviceManager);
                }
            }
            return List.copyOf(interceptors.values());
        } finally {
            stateWriteLock.unlock();
        }
    }

    void ensureInjectionPlans() {
        servicesByDescriptor.values()
                .forEach(ServiceManager::ensureBindingPlan);
    }

    private void accessed(ServiceInfo service) {
        stateReadLock.lock();
        try {
            accessed(ResolvedType.create(service.serviceType()));
            service.contracts()
                    .forEach(this::accessed);
        } finally {
            stateReadLock.unlock();
        }
    }

    private void accessed(ResolvedType type) {
        AtomicBoolean atomicBoolean = accessedContracts.get(type);
        if (atomicBoolean == null) {
            return;
        }
        atomicBoolean.set(true);
    }

    private void checkValidContract(ResolvedType contract) {
        AtomicBoolean accessed = accessedContracts.get(contract);

        if (bindings.isValidContract(contract) && accessed == null) {
            return;
        }

        if (accessed == null) {
            throw new ServiceRegistryException("Contract " + contract.resolvedName()
                                                       + " is not provided by any service in the registry, so it cannot have "
                                                       + "instances configured, as no services can use it.");
        }
        if (accessed.get()) {
            throw new ServiceRegistryException("Contract " + contract.resolvedName()
                                                       + " has already been set, or accessed by a service, its instances "
                                                       + "cannot be re-configured, as that would end up in inconsistent "
                                                       + "state of the service registry.");
        }
    }

    private void checkCreateForName(Lookup lookup) {
        if (lookup.qualifiers().size() != 1) {
            throw new ServiceRegistryException("Invalid injection lookup. @"
                                                       + Service.InstanceName.class.getName()
                                                       + " must be the only qualifier used.");
        }
        if (!lookup.contracts().contains(ResolvedType.create(TypeNames.STRING))) {
            throw new ServiceRegistryException("Invalid injection lookup. @"
                                                       + Service.InstanceName.class.getName()
                                                       + " must use String contract.");
        }
        if (lookup.contracts().size() != 1) {
            throw new ServiceRegistryException("Invalid injection lookup. @"
                                                       + Service.InstanceName.class.getName()
                                                       + " must use String as the only contract.");
        }
    }

    private Supplier<Scope> scopeSupplier(ServiceInfo descriptor) {
        TypeName scope = descriptor.scope();
        if (Service.Singleton.TYPE.equals(scope)) {
            return singletonScope;
        } else if (Service.PerLookup.TYPE.equals(scope)) {
            return perLookupScope;
        } else {
            // must be a lazy value, as the scope handler may not be available at the time this method is called
            LazyValue<Service.ScopeHandler> scopeHandler = LazyValue.create(() -> scopeHandler(scope));
            return () -> scopeHandler.get()
                    .currentScope() // must be called each time, as we must use the currently active scope, not a cached one
                    .orElseThrow(() -> new ScopeNotActiveException("Scope not active for service: "
                                                                           + descriptor.serviceType().fqName(),
                                                                   scope));
        }
    }

    private Scope createScope(TypeName scopeType,
                              Service.ScopeHandler scopeHandler,
                              String id,
                              Map<ServiceDescriptor<?>, Object> initialBindings) {
        var registry = new ScopedRegistryImpl(this, scopeType, id, initialBindings);
        var scope = new ScopeImpl(scopeType, scopeHandler, registry);
        scopeHandler.activate(scope);
        return scope;
    }

    private Service.ScopeHandler scopeHandler(TypeName scope) {
        scopeHandlerInstancesLock.lock();
        try {
            return scopeHandlerInstances.computeIfAbsent(scope, it -> {
                ServiceInfo serviceInfo = scopeHandlerServices.get(scope);
                if (serviceInfo == null) {
                    throw new ServiceRegistryException("There is no scope handler service registered for scope: "
                                                               + scope.fqName());
                }
                ServiceManager<?> serviceManager = servicesByDescriptor.get(serviceInfo);
                return (Service.ScopeHandler) serviceManager.activator()
                        .instances(Lookup.EMPTY)
                        .orElseThrow(() -> new ServiceRegistryException("Scope handler service did not return any instance for: "
                                                                                + scope.fqName()))
                        .getFirst() // List.getFirst() - Qualified instance
                        .get();
            });
        } finally {
            scopeHandlerInstancesLock.unlock();
        }
    }

    private record ScopeImpl(TypeName scopeType,
                             Service.ScopeHandler handler,
                             ScopedRegistry registry) implements Scope {

        @Override
        public void close() {
            handler.deactivate(this);
        }

        @Override
        public String toString() {
            return "Scope for " + scopeType.fqName();
        }
    }

}
