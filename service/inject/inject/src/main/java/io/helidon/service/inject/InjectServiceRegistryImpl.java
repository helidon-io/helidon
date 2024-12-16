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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.configurable.LruCache;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.inject.InjectRegistryManager.TypedQualifiedProviderKey;
import io.helidon.service.inject.ServiceSupplies.ServiceSupplyList;
import io.helidon.service.inject.api.ActivationRequest;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectRegistry__ServiceDescriptor;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.InstanceName__ServiceDescriptor;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.InterceptionMetadata__ServiceDescriptor;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ScopeNotActiveException;
import io.helidon.service.inject.api.ScopedRegistry;
import io.helidon.service.inject.api.Scopes;
import io.helidon.service.inject.api.Scopes__ServiceDescriptor;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

import static io.helidon.service.inject.InjectRegistryManager.SERVICE_INFO_COMPARATOR;
import static io.helidon.service.inject.LookupTrace.traceLookup;

/**
 * Full-blown service registry with injection and interception support.
 * <p>
 * This implementation re-implements even the core registry, as we want the services to be capable of interoperating
 * (i.e. core services can receive inject services and vice-versa).
 */
class InjectServiceRegistryImpl implements InjectRegistry, Scopes {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final String id = String.valueOf(COUNTER.incrementAndGet());

    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Lock stateReadLock = stateLock.readLock();
    private final Lock stateWriteLock = stateLock.writeLock();

    // map of scope annotation to service info
    private final Map<TypeName, InjectServiceInfo> scopeHandlerServices;
    // map of service implementation class to service info
    private final Map<TypeName, InjectServiceInfo> servicesByType;
    // map of provided contracts to service info(s)
    private final Map<ResolvedType, Set<InjectServiceInfo>> servicesByContract;
    // map of qualifier annotations to service info(s)
    private final Map<TypeName, Set<InjectServiceInfo>> qualifiedProvidersByQualifier;
    // map of qualifier annotations and resolved type combination to service info(s)
    private final Map<TypedQualifiedProviderKey, Set<InjectServiceInfo>> typedQualifiedProviders;

    private final RegistryCounter lookupCounter;
    private final RegistryCounter lookupScanCounter;
    private final RegistryCounter cacheLookupCounter;
    private final RegistryCounter cacheHitCounter;
    private final boolean cacheEnabled;
    private final LruCache<Lookup, List<InjectServiceInfo>> cache;

    private final LazyValue<Scope> singletonScope = LazyValue
            .create(() -> createScope(Injection.Singleton.TYPE, Optional::empty, id, Map.of()));

    private final LazyValue<Scope> perLookupScope = LazyValue
            .create(() -> createScope(Injection.PerLookup.TYPE, Optional::empty, id, Map.of()));
    private final Map<TypeName, Injection.ScopeHandler> scopeHandlerInstances = new HashMap<>();
    private final Lock scopeHandlerInstancesLock = new ReentrantLock();
    private final boolean interceptionEnabled;
    private final InterceptionMetadata interceptionMetadata;

    // runtime fields (to obtain actual service instances)
    // service descriptor to its manager
    private final Map<ServiceInfo, ServiceManager<?>> servicesByDescriptor = new IdentityHashMap<>();
    private final ActivationRequest activationRequest;
    private Map<ServiceInfo, ServiceManager<Interception.Interceptor>> interceptors;

    @SuppressWarnings("unchecked")
    InjectServiceRegistryImpl(InjectConfig config,
                              Map<ServiceInfo, InjectRegistryManager.Described> descriptorToDescribed,
                              Map<TypeName, InjectServiceInfo> scopeHandlers,
                              Map<ServiceInfo, Object> explicitInstances,
                              Map<TypeName, InjectServiceInfo> servicesByType,
                              Map<ResolvedType, Set<InjectServiceInfo>> servicesByContract,
                              Map<TypeName, Set<InjectServiceInfo>> qualifiedProvidersByQualifier,
                              Map<TypedQualifiedProviderKey, Set<InjectServiceInfo>> typedQualifiedProviders) {

        this.interceptionEnabled = config.interceptionEnabled();
        // this is a bit tricky - we are leaking our instance that is not yet finished, so it must
        // not be accessed in the constructor!
        this.interceptionMetadata = interceptionEnabled
                ? InterceptionMetadataImpl.create(this)
                : InterceptionMetadataImpl.noop();

        // these must be bound here, as the instance exists now
        // (and we do not want to allow post-constructor binding)
        explicitInstances.put(Scopes__ServiceDescriptor.INSTANCE, this);
        explicitInstances.put(InjectRegistry__ServiceDescriptor.INSTANCE, this);
        explicitInstances.put(InterceptionMetadata__ServiceDescriptor.INSTANCE, interceptionMetadata);

        this.cacheEnabled = config.lookupCacheEnabled();
        this.cache = cacheEnabled ? config.lookupCache().orElseGet(LruCache::create) : null;

        // no-op counters (registry needs config, too early
        this.lookupCounter = new RegistryCounter();
        this.lookupScanCounter = new RegistryCounter();
        if (cacheEnabled) {
            this.cacheLookupCounter = new RegistryCounter();
            this.cacheHitCounter = new RegistryCounter();
        } else {
            this.cacheLookupCounter = null;
            this.cacheHitCounter = null;
        }

        this.scopeHandlerServices = scopeHandlers;
        this.servicesByType = servicesByType;
        this.servicesByContract = servicesByContract;
        this.qualifiedProvidersByQualifier = qualifiedProvidersByQualifier;
        this.typedQualifiedProviders = typedQualifiedProviders;
        this.activationRequest = ActivationRequest.builder()
                .targetPhase(config.limitRuntimePhase())
                .build();

        /*
        For each known service descriptor, create an appropriate service manager
         */
        descriptorToDescribed.forEach((descriptor, described) -> {
            InjectServiceDescriptor<?> injectDescriptor = described.injectDescriptor();

            Object instance = explicitInstances.get(descriptor);
            ServiceProvider<Object> provider = new ServiceProvider<>(
                    this,
                    (InjectServiceDescriptor<Object>) described.injectDescriptor());

            if (instance != null) {
                Activator<Object> activator = Activators.create(provider, instance);
                servicesByDescriptor.put(descriptor,
                                         new ServiceManager<>(scopeSupplier(injectDescriptor),
                                                              provider,
                                                              true,
                                                              () -> activator));
            } else {
                // we must always prefer explicit instances - so if specified, we will never override it
                servicesByDescriptor.putIfAbsent(descriptor,
                                                 new ServiceManager<>(scopeSupplier(injectDescriptor),
                                                                      provider,
                                                                      false,
                                                                      Activators.create(this, provider)));
            }
        });

        // make sure config is initialized as it should be
        if (config.limitRuntimePhase().ordinal() >= Activator.Phase.ACTIVE.ordinal()) {
            Config registryConfig = first(Config.class).orElseGet(GlobalConfig::config);
            GlobalConfig.config(() -> registryConfig, true);

            // Set-up metrics using metric registry
            MeterRegistry meterRegistry = Metrics.globalRegistry();
            this.lookupCounter.consumer = meterRegistry
                    .getOrCreate(Counter.builder("io.helidon.inject.lookups")
                                         .description("Number of lookups in the service registry")
                                         .scope(Meter.Scope.VENDOR))::increment;
            this.lookupScanCounter.consumer = meterRegistry
                    .getOrCreate(Counter.builder("io.helidon.inject.scanLookups")
                                         .description("Number of lookups that require registry scan")
                                         .scope(Meter.Scope.VENDOR))::increment;
            if (cacheEnabled) {
                this.cacheLookupCounter.consumer = meterRegistry
                        .getOrCreate(Counter.builder("io.helidon.inject.cacheLookups")
                                             .description("Number of lookups in cache in the service registry")
                                             .scope(Meter.Scope.VENDOR))::increment;
                this.cacheHitCounter.consumer = meterRegistry
                        .getOrCreate(Counter.builder("io.helidon.inject.cacheHits")
                                             .description("Number of cache hits in the service registry")
                                             .scope(Meter.Scope.VENDOR))::increment;
            }
        }
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
    public List<ServiceInfo> allServices(TypeName contract) {
        return lookupServices(Lookup.create(contract))
                .stream()
                .map(InjectServiceInfo::coreInfo)
                .collect(Collectors.toList());
    }

    @Override
    public <T> T get(Lookup lookup) {
        return this.<T>supply(lookup).get();
    }

    @Override
    public <T> Optional<T> first(Lookup lookup) {
        return this.<T>supplyFirst(lookup).get();
    }

    @Override
    public <T> List<T> all(Lookup lookup) {
        return this.<T>supplyAll(lookup).get();
    }

    @Override
    public <T> Supplier<T> supply(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            throw new ServiceRegistryException("There is no service in registry that matches this lookup: " + lookup);
        }
        return new ServiceSupplies.ServiceSupply<>(lookup, managers);
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
    public <T> Supplier<List<T>> supplyAll(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return List::of;
        }
        return new ServiceSupplyList<>(lookup, managers);
    }

    @Override
    public <T> T get(TypeName contract) {
        return get(Lookup.create(contract));
    }

    @Override
    public <T> Optional<T> first(TypeName contract) {
        return first(Lookup.create(contract));
    }

    @Override
    public <T> List<T> all(TypeName contract) {
        return all(Lookup.create(contract));
    }

    @Override
    public <T> Supplier<T> supply(TypeName contract) {
        return supply(Lookup.create(contract));
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(TypeName contract) {
        return supplyFirst(Lookup.create(contract));
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(TypeName contract) {
        return supplyAll(Lookup.create(contract));
    }

    @Override
    public Scope createScope(TypeName scopeType, String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        return createScope(scopeType,
                           scopeHandler(scopeType),
                           id,
                           initialBindings);
    }

    @Override
    public List<InjectServiceInfo> lookupServices(Lookup lookup) {
        try {
            stateReadLock.lock();
            // a very special lookup
            if (lookup.qualifiers().contains(Qualifier.CREATE_FOR_NAME)) {
                if (lookup.qualifiers().size() != 1) {
                    throw new ServiceRegistryException("Invalid injection lookup. @"
                                                               + Injection.InstanceName.class.getName()
                                                               + " must be the only qualifier used.");
                }
                if (!lookup.contracts().contains(ResolvedType.create(TypeNames.STRING))) {
                    throw new ServiceRegistryException("Invalid injection lookup. @"
                                                               + Injection.InstanceName.class.getName()
                                                               + " must use String contract.");
                }
                if (lookup.contracts().size() != 1) {
                    throw new ServiceRegistryException("Invalid injection lookup. @"
                                                               + Injection.InstanceName.class.getName()
                                                               + " must use String as the only contract.");
                }
                return List.of(InstanceName__ServiceDescriptor.INSTANCE);
            }

            lookupCounter.increment();

            traceLookup(lookup, "start: {0}", lookup);

            if (cacheEnabled) {
                List<InjectServiceInfo> cacheResult = cache.get(lookup)
                        .orElse(null);
                cacheLookupCounter.increment();
                if (cacheResult != null) {
                    traceLookup(lookup, "from cache", cacheResult);
                    cacheHitCounter.increment();
                    return cacheResult;
                }
            }

            List<InjectServiceInfo> result = new ArrayList<>();

            if (lookup.serviceType().isPresent()) {
                // when a specific service type is requested, we go for it
                InjectServiceInfo exact = servicesByType.get(lookup.serviceType().get());
                if (exact != null) {
                    traceLookup(lookup, "by service type", result);
                    result.add(exact);
                    return result;
                }
            }

            if (1 == lookup.contracts().size()) {
                // a single contract is requested, we are ready for this ("indexed by contract")
                ResolvedType theOnlyContractRequested = lookup.contracts().iterator().next();
                Set<InjectServiceInfo> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
                if (subsetOfMatches != null) {
                    // the subset is ordered, cannot use parallel, also no need to re-order
                    subsetOfMatches.stream()
                            .filter(lookup::matches)
                            .forEach(result::add);
                    if (!result.isEmpty()) {
                        traceLookup(lookup, "by single contract", result);
                        return result;
                    }
                }
            }

            // table scan :-(
            lookupScanCounter.increment();
            servicesByType.values()
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
                        Set<InjectServiceInfo> found = typedQualifiedProviders.get(new TypedQualifiedProviderKey(qualifierType,
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

    void ensureInjectionPlans() {
        servicesByDescriptor.values()
                .forEach(ServiceManager::ensureInjectionPlan);
    }

    void close() {
        singletonScope.get()
                .close();
    }

    List<ServiceInfo> servicesByContract(ResolvedType contract) {
        Set<InjectServiceInfo> serviceInfos = servicesByContract.get(contract);
        if (serviceInfos == null) {
            return List.of();
        }
        return serviceInfos.stream()
                .map(InjectServiceInfo::coreInfo)
                .collect(Collectors.toList());
    }

    <T> List<ServiceManager<T>> lookupManagers(Lookup lookup) {
        List<ServiceManager<T>> result = new ArrayList<>();

        for (InjectServiceInfo service : lookupServices(lookup)) {
            result.add(serviceManager(service.coreInfo()));
        }

        return result;
    }

    InterceptionMetadata interceptionMetadata() {
        return interceptionMetadata;
    }

    ActivationRequest activationRequest() {
        return activationRequest;
    }

    @SuppressWarnings("unchecked")
    <T> ServiceManager<T> serviceManager(ServiceInfo info) {
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
            Set<InjectServiceInfo> ordered = new TreeSet<>(SERVICE_INFO_COMPARATOR);
            for (ServiceInfo serviceInfo : serviceInfos) {
                ServiceManager<Object> serviceManager = this.serviceManager(serviceInfo);
                ordered.add(serviceManager.injectDescriptor());
            }

            // there may be more than one application, we need to add to existing
            for (InjectServiceInfo injectServiceInfo : ordered) {
                this.interceptors.computeIfAbsent(injectServiceInfo.coreInfo(),
                                                  this::serviceManager);
            }
        } finally {
            stateWriteLock.unlock();
        }
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

    private Scope createScope(TypeName scopeType,
                              Injection.ScopeHandler scopeHandler,
                              String id,
                              Map<ServiceDescriptor<?>, Object> initialBindings) {
        var registry = new ScopedRegistryImpl(this, scopeType, id, initialBindings);
        var scope = new ScopeImpl(scopeType, scopeHandler, registry);
        scopeHandler.activate(scope);
        return scope;
    }

    private Supplier<Scope> scopeSupplier(InjectServiceInfo descriptor) {
        TypeName scope = descriptor.scope();
        if (Injection.Singleton.TYPE.equals(scope)) {
            return singletonScope;
        } else if (Injection.PerLookup.TYPE.equals(scope)) {
            return perLookupScope;
        } else {
            // must be a lazy value, as the scope handler may not be available at the time this method is called
            LazyValue<Injection.ScopeHandler> scopeHandler = LazyValue.create(() -> scopeHandler(scope));
            return () -> scopeHandler.get()
                    .currentScope() // must be called each time, as we must use the currently active scope, not a cached one
                    .orElseThrow(() -> new ScopeNotActiveException("Scope not active for service: "
                                                                           + descriptor.serviceType().fqName(),
                                                                   scope));
        }
    }

    private Injection.ScopeHandler scopeHandler(TypeName scope) {
        scopeHandlerInstancesLock.lock();
        try {
            return scopeHandlerInstances.computeIfAbsent(scope, it -> {
                InjectServiceInfo serviceInfo = scopeHandlerServices.get(scope);
                if (serviceInfo == null) {
                    throw new ServiceRegistryException("There is no scope handler service registered for scope: "
                                                               + scope.fqName());
                }
                ServiceManager<?> serviceManager = servicesByDescriptor.get(serviceInfo.coreInfo());
                return (Injection.ScopeHandler) serviceManager.activator()
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

    private static class RegistryCounter implements Counter {
        private volatile Consumer<Long> consumer;

        RegistryCounter() {
            consumer = it -> {
            };
        }

        @Override
        public void increment() {
            increment(1);
        }

        @Override
        public void increment(long amount) {
            consumer.accept(amount);
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException("This is not a full counter");
        }

        @Override
        public Id id() {
            throw new UnsupportedOperationException("This is not a full counter");
        }

        @Override
        public Optional<String> baseUnit() {
            throw new UnsupportedOperationException("This is not a full counter");
        }

        @Override
        public Optional<String> description() {
            throw new UnsupportedOperationException("This is not a full counter");
        }

        @Override
        public Type type() {
            throw new UnsupportedOperationException("This is not a full counter");
        }

        @Override
        public Optional<String> scope() {
            throw new UnsupportedOperationException("This is not a full counter");
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            throw new UnsupportedOperationException("This is not a full counter");
        }
    }

    private record ScopeImpl(TypeName scopeType,
                             Injection.ScopeHandler handler,
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
