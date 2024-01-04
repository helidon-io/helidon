/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ContextualLookup;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceBinder;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.spi.ActivatorProvider;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;

/**
 * This is the root service registry that knows of all services.
 */
class ServicesImpl implements Services, ServiceBinder {
    private static final System.Logger LOGGER = System.getLogger(Services.class.getName());
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Comparator<ServiceManager<?>> SERVICE_MANAGER_COMPARATOR = Comparator
            .<ServiceManager<?>>comparingDouble(ServiceManager::weight)
            .thenComparing(ServiceManager::serviceType)
            .reversed();
    private static final Map<String, ActivatorProvider> ACTIVATOR_PROVIDERS;

    static {
        Map<String, ActivatorProvider> activators = new HashMap<>();

        HelidonServiceLoader.builder(ServiceLoader.load(ActivatorProvider.class, Thread.currentThread().getContextClassLoader()))
                .addService(new InjectActivatorProvider())
                .build()
                .asList()
                .forEach(it -> activators.putIfAbsent(it.id(), it));
        ACTIVATOR_PROVIDERS = Map.copyOf(activators);
    }

    private final String id = String.valueOf(COUNTER.incrementAndGet());
    private final InjectionServicesImpl injectionServices;
    private final InjectionConfig cfg;
    private final Counter lookupCounter;
    private final Counter lookupScanCounter;
    private final Counter cacheLookupCounter;
    private final Counter cacheHitCounter;
    private final boolean cacheEnabled;
    private final State state;

    /*
    The following section may be modified at runtime
     */
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Lock stateReadLock = stateLock.readLock();
    private final Lock stateWriteLock = stateLock.writeLock();

    // service descriptor to its manager
    private final Map<ServiceInfo, ServiceManager<?>> servicesByDescriptor = new IdentityHashMap<>();
    // implementation type to its manager
    private final Map<TypeName, ServiceInfo> servicesByType = new HashMap<>();
    // implemented contracts to their manager(s)
    private final Map<TypeName, Set<ServiceInfo>> servicesByContract = new HashMap<>();
    private final List<ServiceManager<?>> scopeHandlerManagers = new ArrayList<>();
    private final Map<TypeName, ScopeHandler> scopeHandlers = new HashMap<>();
    private final Map<Lookup, List<ServiceManager<?>>> cache = new HashMap<>();

    private List<ServiceManager<Interception.Interceptor>> interceptors;

    ServicesImpl(InjectionServicesImpl injectionServices, State state) {
        this.injectionServices = injectionServices;
        this.state = state;
        this.cfg = injectionServices.config();

        this.lookupCounter = Metrics.globalRegistry()
                .getOrCreate(Counter.builder("io.helidon.inject.lookups")
                                     .description("Number of lookups in the service registry")
                                     .scope(Meter.Scope.VENDOR));
        this.lookupScanCounter = Metrics.globalRegistry()
                .getOrCreate(Counter.builder("io.helidon.inject.scanLookups")
                                     .description("Number of lookups that require registry scan")
                                     .scope(Meter.Scope.VENDOR));

        this.cacheEnabled = cfg.serviceLookupCaching();
        this.cacheLookupCounter = Metrics.globalRegistry()
                .getOrCreate(Counter.builder("io.helidon.inject.cacheLookups")
                                     .description("Number of lookups in cache in the service registry")
                                     .scope(Meter.Scope.VENDOR));
        this.cacheHitCounter = Metrics.globalRegistry()
                .getOrCreate(Counter.builder("io.helidon.inject.cacheHits")
                                     .description("Number of cache hits in the service registry")
                                     .scope(Meter.Scope.VENDOR));

        this.scopeHandlers.put(Injection.Singleton.TYPE_NAME, new SingletonScopeHandler(this));

        if (!injectionServices.config().interceptionEnabled()) {
            this.interceptors = List.of();
        }
    }

    @Override
    public InjectionServices injectionServices() {
        return injectionServices;
    }

    @Override
    public void bind(ServiceDescriptor<?> serviceDescriptor) {
        ActivatorProvider activatorProvider = ACTIVATOR_PROVIDERS.get(serviceDescriptor.runtimeId());
        if (activatorProvider == null) {
            throw new IllegalStateException("Expected an activator provider for runtime id: " + serviceDescriptor.runtimeId()
                                                    + ", available activator providers: " + ACTIVATOR_PROVIDERS.keySet());
        }
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Binding service descriptor: " + serviceDescriptor.infoType().fqName());
        }

        // scope handlers have a very specific meaning
        if (serviceDescriptor.contracts().contains(ScopeHandler.TYPE_NAME)) {
            if (!Injection.Singleton.TYPE_NAME.equals(serviceDescriptor.scope())) {
                throw new InjectionException("Services that provide ScopeHandler contract MUST be in Singleton scope, but "
                                                     + serviceDescriptor.serviceType().fqName() + " is in "
                                                     + serviceDescriptor.scope().fqName() + " scope.");
            }
        }

        bind(serviceDescriptor, () -> activatorProvider.activator(this, serviceDescriptor));
    }

    @Override
    public <T> Supplier<T> supply(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            throw new InjectionException("No services match: " + lookup);
        }
        return new ServiceSupply<>(ContextualLookup.builder().from(lookup).build(), managers);
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return Optional::empty;
        }
        return new ServiceSupplyOptional<>(ContextualLookup.builder().from(lookup).build(), managers);
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return List::of;
        }
        return new ServiceSupplyList<>(ContextualLookup.builder().from(lookup).build(), managers);
    }

    @Override
    public ServiceBinder binder() {
        return this;
    }

    void bindSelf() {
        bind(Services__ServiceDescriptor.INSTANCE, () -> ServicesActivator.create(this));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void interceptors(ServiceInfo... serviceInfos) {
        if (this.interceptors == null) {
            List list = Stream.of(serviceInfos)
                    .map(this::serviceManager)
                    .toList();
            this.interceptors = List.copyOf(list);
        }
    }

    List<ServiceManager<Interception.Interceptor>> interceptors() {
        if (interceptors == null) {
            interceptors = lookupManagers(Lookup.builder()
                                                  .addContract(Interception.Interceptor.class)
                                                  .addQualifier(Qualifier.WILDCARD_NAMED)
                                                  .build());
        }
        return interceptors;
    }

    void bind(ModuleComponent module) {
        String moduleName = module.name();

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Starting module binding: " + moduleName);
        }

        ServiceBinder moduleBinder = new ServiceBinderImpl(moduleName);
        module.configure(moduleBinder);
        InjectionModuleActivator activator = InjectionModuleActivator.create(this, module, moduleName);
        bind(activator.descriptor(), () -> activator);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Finished module binding: " + moduleName);
        }
    }

    void bind(Application application) {
        String appName = application.name();

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Starting application binding: " + appName);
        }

        ServiceInjectionPlanBinder appBinder = new AppBinderImpl(appName);
        application.configure(appBinder);
        InjectionApplicationActivator activator = InjectionApplicationActivator.create(this,
                                                                                       application,
                                                                                       appName);
        bind(activator.descriptor(), () -> activator);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Finished application binding: " + appName);

        }
    }

    ScopeHandler scopeHandler(TypeName scope) {
        return scopeHandlers.computeIfAbsent(scope, it -> {
            for (ServiceManager<?> scopeHandlerManager : scopeHandlerManagers) {
                ScopeHandler scopeHandler = (ScopeHandler) scopeHandlerManager.serviceProvider().get();
                if (scopeHandler.supportedScope().equals(scope)) {
                    return scopeHandler;
                }
            }
            throw new InjectionException("A request for service in scope " + scope.fqName()
                                                 + " has been received, yet there is no ScopeHandler available for this scope "
                                                 + "in this registry");
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> List<Supplier<T>> explodeFilterAndSort(Lookup lookup,
                                                              List<ServiceManager<T>> serviceManagers) {
        // this method is called when we resolve instances, so we can safely assume any scope is active

        List<RegistryServiceProvider<T>> providers = serviceManagers.stream()
                .map(ServiceManager::serviceProvider)
                .toList();

        List<RegistryServiceProvider<?>> exploded = providers.stream()
                .flatMap(it -> {
                    if (it instanceof ServiceProviderProvider spp) {
                        return spp.serviceProviders(lookup, true, true)
                                .stream();
                    } else {
                        return Stream.of(it);
                    }
                })
                .sorted(ServiceProviderComparator.instance())
                .toList();

        // the providers are sorted by weight and other properties
        // we need to have unnamed providers before named ones (if criteria does not contain a Named qualifier)
        // in similar fashion, if criteria does not contain any qualifier, put unqualified instances first
        if (lookup.qualifiers().isEmpty()) {
            // unqualified first, unnamed before named, but keep the existing order otherwise
            List unqualified = new ArrayList<>();
            List<RegistryServiceProvider<?>> qualified = new ArrayList<>();
            for (RegistryServiceProvider<?> serviceProvider : exploded) {
                if (serviceProvider.qualifiers().isEmpty()) {
                    unqualified.add(serviceProvider);
                } else {
                    qualified.add(serviceProvider);
                }
            }
            unqualified.addAll(qualified);
            return unqualified;
        } else if (!hasNamed(lookup.qualifiers())) {
            // unnamed first
            List unnamed = new ArrayList<>();
            List<RegistryServiceProvider<?>> named = new ArrayList<>();
            for (RegistryServiceProvider serviceProvider : exploded) {
                if (hasNamed(serviceProvider.qualifiers())) {
                    named.add(serviceProvider);
                } else {
                    unnamed.add(serviceProvider);
                }
            }
            unnamed.addAll(named);
            return unnamed;
        }

        // need to coerce the compiler into the correct type here...
        return (List) exploded;
    }

    private static boolean hasNamed(Set<Qualifier> qualifiers) {
        return qualifiers.stream()
                .anyMatch(it -> it.typeName().equals(Injection.Named.TYPE_NAME));
    }

    @SuppressWarnings("unchecked")
    <T> ServiceManager<T> serviceManager(ServiceInfo descriptor) {
        ServiceManager<?> serviceManager = servicesByDescriptor.get(descriptor);
        if (serviceManager == null) {
            throw new InjectionException("A service descriptor was used that is not bound to this service registry: " + descriptor.serviceType());
        }
        return (ServiceManager<T>) serviceManager;
    }

    @SuppressWarnings("unchecked,rawtypes")
    private void bind(ServiceDescriptor<?> descriptor, Supplier<Activator<?>> activatorSupplier) {
        try {
            stateWriteLock.lock();
            if (state.currentPhase().ordinal() > Phase.GATHERING_DEPENDENCIES.ordinal()) {
                if (!cfg.permitsDynamic()) {
                    throw new IllegalStateException(
                            "Attempting to bind to Services that do not support dynamic updates. Set option permitsDynamic, "
                                    + "or configuration option 'inject.permits-dynamic=true' to enable");
                }
            }

            ServiceManager serviceManager = new ServiceManager(this, descriptor, activatorSupplier);
            servicesByDescriptor.put(descriptor, serviceManager);

            if (descriptor.contracts().contains(ScopeHandler.TYPE_NAME)) {
                scopeHandlerManagers.add(serviceManager);
            }

            TypeName serviceType = descriptor.serviceType();

            // only put if absent, as this may be a lower weight provider for the same type
            ServiceManager<?> previousValue = servicesByType.putIfAbsent(serviceType, serviceManager);
            if (previousValue != null) {
                // a value was already registered for this service type, ignore this registration
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Attempt to register another service provider for the same service type."
                            + " Service type: " + serviceType.fqName()
                            + ", existing provider: " + previousValue
                            + ", new provider: " + serviceManager);
                }
                return;
            }

            servicesByContract.computeIfAbsent(serviceType, it -> new TreeSet<>(SERVICE_MANAGER_COMPARATOR))
                    .add(serviceManager);

            for (TypeName contract : descriptor.contracts()) {
                servicesByContract.computeIfAbsent(contract, it -> new TreeSet<>(SERVICE_MANAGER_COMPARATOR))
                        .add(serviceManager);
            }
        } finally {
            stateWriteLock.unlock();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <T> List<ServiceInfo> lookupServices(Lookup lookup) {
        Lock currentLock = stateReadLock;
        try {
            // most of our operations are within a read lock
            currentLock.lock();

            lookupCounter.increment();

            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Lookup: " + lookup);
            }

            List<ServiceInfo> result = new ArrayList<>();

            if (lookup.serviceType().isPresent()) {
                // when a specific service type is requested, we go for it
                ServiceInfo exact = servicesByType.get(lookup.serviceType().get());
                if (exact != null) {
                    result.add(exact);
                    return result;
                }
            }

            if (1 == lookup.contracts().size()) {
                // a single contract is requested, we are ready for this ("indexed by contract")
                TypeName theOnlyContractRequested = lookup.contracts().iterator().next();
                Set<ServiceManager<?>> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
                if (subsetOfMatches != null) {
                    // the subset is ordered, cannot use parallel, also no need to re-order
                    subsetOfMatches.stream()
                            .filter(it -> lookup.matches(it.descriptor()))
                            .forEach(it -> result.add((ServiceManager<T>) it));
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }

            if (cacheEnabled) {
                List cacheResult = cache.get(lookup);
                cacheLookupCounter.increment();
                if (cacheResult != null) {
                    cacheHitCounter.increment();
                    return cacheResult;
                }
            }

            // table scan :-(
            lookupScanCounter.increment();
            servicesByType.values()
                    .stream()
                    .filter(it -> lookup.matches(it.descriptor()))
                    .sorted(SERVICE_MANAGER_COMPARATOR)
                    .forEach(it -> result.add((ServiceManager<T>) it));

            if (cacheEnabled) {
                // upgrade to write lock
                currentLock.unlock();
                currentLock = stateWriteLock;
                currentLock.lock();
                List cached = result;
                cache.put(lookup, cached);
            }

            return result;
        } finally {
            currentLock.unlock();
        }
    }

    static class ServiceSupply<T> implements Supplier<T> {
        private final ContextualLookup lookup;
        private final List<ServiceManager<T>> managers;

        // supply a single instance at runtime based on the manager
        ServiceSupply(ContextualLookup lookup, List<ServiceManager<T>> managers) {
            this.lookup = lookup;
            this.managers = managers;
        }

        @Override
        public T get() {
            return explodeFilterAndSort(lookup, managers)
                    .stream()
                    .findFirst()
                    .map(Supplier::get)
                    .orElseThrow(() -> new InjectionException(
                            "Neither of matching services could provide a value. Descriptors: " + managers + ", "
                                    + "lookup: " + lookup));
        }
    }

    static class ServiceSupplyOptional<T> implements Supplier<Optional<T>> {
        private final ContextualLookup lookup;
        private final List<ServiceManager<T>> managers;

        // supply a single instance at runtime based on the manager
        ServiceSupplyOptional(ContextualLookup lookup, List<ServiceManager<T>> managers) {
            this.lookup = lookup;
            this.managers = managers;
        }

        @Override
        public Optional<T> get() {
            return explodeFilterAndSort(lookup, managers)
                    .stream()
                    .findFirst()
                    .map(Supplier::get);
        }
    }

    static class ServiceSupplyList<T> implements Supplier<List<T>> {
        private final ContextualLookup lookup;
        private final List<ServiceManager<T>> managers;

        // supply a single instance at runtime based on the manager
        ServiceSupplyList(ContextualLookup lookup, List<ServiceManager<T>> managers) {
            this.lookup = lookup;
            this.managers = managers;
        }

        @Override
        public List<T> get() {
            return explodeFilterAndSort(lookup, managers)
                    .stream()
                    .map(Supplier::get)
                    .toList();
        }
    }

    private static class SingletonScopeHandler implements ScopeHandler {
        private final Scope scope;

        SingletonScopeHandler(ServicesImpl serviceRegistry) {
            this.scope = new SingletonScope(serviceRegistry);
        }

        @Override
        public TypeName supportedScope() {
            return Injection.Singleton.TYPE_NAME;
        }

        @Override
        public Optional<Scope> currentScope() {
            return Optional.of(scope);
        }
    }

    private static class SingletonScope implements Scope {
        private final ScopeServices services;

        SingletonScope(ServicesImpl serviceRegistry) {
            this.services = new ScopeServices(serviceRegistry, Injection.Singleton.TYPE_NAME, serviceRegistry.id, Map.of());
        }

        @Override
        public void close() {
            // no-op, singleton service registry is closed from InjectionServices
        }

        @Override
        public ScopeServices services() {
            return services;
        }
    }

    private class ServiceBinderImpl implements ServiceBinder {
        private final String moduleName;

        ServiceBinderImpl(String moduleName) {
            this.moduleName = moduleName;
        }

        @Override
        public void bind(ServiceDescriptor<?> serviceDescriptor) {
            ServicesImpl.this.bind(serviceDescriptor);
        }

        @Override
        public String toString() {
            return "Service binder for module: " + moduleName;
        }
    }

    private class AppBinderImpl implements ServiceInjectionPlanBinder {
        private final String appName;

        AppBinderImpl(String appName) {
            this.appName = appName;
        }

        @Override
        public Binder bindTo(ServiceInfo serviceInfo) {
            ServiceManager<?> serviceManager = ServicesImpl.this.serviceManager(serviceInfo);

            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "binding injection plan to " + serviceManager);
            }

            return serviceManager.servicePlanBinder();
        }

        @Override
        public void interceptors(ServiceInfo... descriptors) {
            ServicesImpl.this.interceptors(descriptors);
        }

        @Override
        public String toString() {
            return "Service binder for application: " + appName;
        }
    }
}
