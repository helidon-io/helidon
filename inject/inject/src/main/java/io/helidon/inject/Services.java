/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.RegistryInstance;
import io.helidon.inject.service.ServiceBinder;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;

/**
 * The service registry. The service registry generally has knowledge about all the services that are available within your
 * application, along with the contracts (i.e., interfaces) they advertise, the qualifiers that optionally describe them, and oll
 * of each services' dependencies on other service contracts, etc.
 * <p>
 * Collectively these service instances are considered "the managed service instances" under Injection.
 * <p>
 * Services are described through a (code generated) {@link io.helidon.inject.service.ServiceDescriptor}, and this registry
 * will manage their lifecycle as required by their annotations (such as {@link io.helidon.inject.service.Injection.Singleton}).
 * <p>
 * This Services interface exposes a read-only set of methods providing access to these "managed service" providers, and
 * available
 * via one of the lookup methods provided. Once you resolve the service provider(s), the service provider can be activated by
 * calling one of its get() methods. This is equivalent to the declarative form just using
 * {@link io.helidon.inject.service.Injection.Inject} instead.
 * Note that activation of a service might result in activation chaining. For example, service A injects service B, etc. When
 * service A is activated then service A's dependencies (i.e., injection points) need to be activated as well. To avoid long
 * activation chaining, it is recommended to that users strive to use {@link java.util.function.Supplier} injection whenever
 * possible.
 * Supplier injection (a) breaks long activation chains from occurring by deferring activation until when those services are
 * really needed, and (b) breaks circular references that lead to {@link io.helidon.inject.InjectionException} during activation
 * (i.e., service A injects B, and service B injects A).
 * <p>
 * The services are ranked according to the provider's comparator. The Injection framework will rank according to a strategy that
 * first looks for
 * {@link io.helidon.common.Weighted}, and finally by the alphabetic ordering according
 * to the type name (package and class canonical name).
 */
public final class Services {
    /**
     * public weight used by Helidon Injection components.
     * It is lower than the default, so it is easy to override service with custom providers.
     */
    public static final double INJECT_WEIGHT = Weighted.DEFAULT_WEIGHT - 1;
    static final TypeName TYPE_NAME = TypeName.create(Services.class);
    private static final System.Logger LOGGER = System.getLogger(Services.class.getName());
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Comparator<ServiceInfo> SERVICE_INFO_COMPARATOR = Comparator
            .comparingDouble(ServiceInfo::weight)
            .reversed()
            .thenComparing((f, s) -> {
                if (f.qualifiers().isEmpty() && s.qualifiers().isEmpty()) {
                    return 0;
                }
                if (f.qualifiers().isEmpty()) {
                    return -1;
                }
                if (s.qualifiers().isEmpty()) {
                    return 1;
                }
                return 0;
            })
            .thenComparing(ServiceInfo::serviceType);

    private final String id = String.valueOf(COUNTER.incrementAndGet());
    private final InjectionServicesImpl injectionServices;
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
    // service managers of discovered scope handlers
    private final List<ServiceManager<?>> scopeHandlerManagers = new ArrayList<>();
    // scope handle can give us a scope instance
    private final Map<TypeName, ScopeHandler> scopeHandlers = new HashMap<>();
    // scope to ScopeServices factory - to bind services that are valid in that scope
    private final Map<TypeName, ScopeServicesFactory> scopeServicesFactories = new HashMap<>();
    private final Map<Lookup, List<ServiceInfo>> cache = new HashMap<>();
    private final SingletonScopeHandler singletonScopeHandler;
    private final ServiceScopeHandler serviceScopeHandler;

    private List<ServiceManager<Interception.Interceptor>> interceptors;

    Services(InjectionServicesImpl injectionServices, State state) {
        this.injectionServices = injectionServices;
        this.state = state;
        InjectionConfig cfg = injectionServices.config();

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

        this.singletonScopeHandler = new SingletonScopeHandler(this);
        this.serviceScopeHandler = new ServiceScopeHandler(this);
        this.scopeHandlers.put(Injection.Singleton.TYPE_NAME, singletonScopeHandler);
        this.scopeHandlers.put(Injection.Service.TYPE_NAME, serviceScopeHandler);

        if (!injectionServices.config().interceptionEnabled()) {
            this.interceptors = List.of();
        }
    }

    /**
     * Get the first service instance matching the lookup with the expectation that there is a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service, use {@code Object} if not known
     * @return the best service instance matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.InjectionException if there is no service that could satisfy the lookup, or the resolution to
     *                                              instance failed
     */
    public <T> T get(Lookup lookup) {
        return this.<T>supply(lookup).get();
    }

    public <T> T get(Class<T> type) {
        return this.get(Lookup.create(type));
    }

    public <T> Optional<T> first(Lookup lookup) {
        return this.<T>supplyFirst(lookup).get();
    }

    public <T> Optional<T> first(Class<T> type) {
        return this.first(Lookup.create(type));
    }

    public <T> Optional<Supplier<T>> firstSupplier(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ServiceSupply<>(lookup, managers));
    }

    public <T> Optional<Supplier<T>> firstSupplier(Class<T> type) {
        return firstSupplier(Lookup.create(type));
    }

    public <T> List<T> all(Lookup lookup) {
        return this.<T>supplyAll(lookup).get();
    }

    public <T> List<T> all(Class<T> type) {
        return this.all(Lookup.create(type));
    }

    public <T> List<Supplier<T>> allSuppliers(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        return managers.stream()
                .map(it -> (Supplier<T>) new ServiceSupply<>(lookup, List.of(it)))
                .toList();
    }

    public <T> List<Supplier<T>> allSuppliers(Class<T> type) {
        return allSuppliers(Lookup.create(type));
    }

    /**
     * Get the first service provider matching the lookup with the expectation that there is a match available.
     * The provided {@link java.util.function.Supplier#get()} may throw an
     * {@link io.helidon.inject.InjectionException} in case the matching service cannot provide a value (either because
     * of scope mismatch, or because there is no available instance, and we use a runtime resolution through
     * {@link io.helidon.inject.service.ServicesProvider}, {@link io.helidon.inject.service.InjectionPointProvider}, or similar).
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     * @throws io.helidon.inject.InjectionException if there is no service that could satisfy the lookup
     */
    public <T> Supplier<T> supply(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            throw new InjectionException("No services match: " + lookup);
        }
        return new ServiceSupply<>(lookup, managers);
    }

    public <T> Supplier<T> supply(Class<T> type) {
        return this.supply(Lookup.create(type));
    }

    public <T> Supplier<T> supply(ServiceInfo descriptor) {
        return new ServiceSupply<>(Lookup.builder()
                                           .serviceType(descriptor.serviceType())
                                           .build(),
                                   List.of(serviceManager(descriptor)));
    }

    /**
     * Find the first service provider matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service providers, use {@code Object} if not known
     * @return the best service provider matching the lookup, cast to the expected type; please use a {@code Object} as the type
     *         if the result may contain an unknown provider type
     */
    public <T> Supplier<Optional<T>> supplyFirst(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return Optional::empty;
        }
        return new ServiceSupplyOptional<>(lookup, managers);
    }

    public <T> Supplier<Optional<T>> supplyFirst(Class<T> type) {
        return this.supplyFirst(Lookup.create(type));
    }

    /**
     * Supply all services matching the lookup with the expectation that there may not be a match available.
     *
     * @param lookup lookup to use
     * @param <T>    type of the expected service suppliers
     * @return supplier of list of services ordered, may be empty if there is no match
     */
    public <T> Supplier<List<T>> supplyAll(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        if (managers.isEmpty()) {
            return List::of;
        }
        return new ServiceSupplyList<>(lookup, managers);
    }

    public <T> Supplier<List<T>> supplyAll(Class<T> type) {
        return this.supplyAll(Lookup.create(type));
    }

    /**
     * Injection services this instance is managed by.
     *
     * @return injection services
     */
    public InjectionServices injectionServices() {
        return injectionServices;
    }

    /**
     * Provides a binder for this service registry.
     * Note that by public you can only bind services from {@link io.helidon.inject.service.ModuleComponent} instances that
     * are code generated at build time.
     * <p>
     * This binder is only allowed if you enable dynamic binding. Although this may be tempting, you are breaking the
     * deterministic behavior of the service registry, and may encounter runtime errors that are otherwise impossible.
     *
     * @return service binder that allows binding into this service registry
     */
    public ServiceBinder binder() {
        return this::bind;
    }

    /**
     * Limit runtime phase.
     *
     * @return phase to activate to
     * @see io.helidon.inject.InjectionConfig#limitRuntimePhase()
     */
    public Phase limitRuntimePhase() {
        return injectionServices().config().limitRuntimePhase();
    }

    public <T> List<RegistryInstance<T>> lookupInstances(Lookup lookup) {
        List<ServiceManager<T>> managers = lookupManagers(lookup);

        return managers.stream()
                .flatMap(it -> managerInstances(lookup, it).stream())
                .toList();

    }

    /**
     * A lookup method operating on the service descriptors, rather than service instances.
     * This is a useful tool for tools that need to analyze the structure of the registry,
     * for testing etc.
     * The returned instances are always the same instances registered with this registry, and these
     * are expected to be the singleton instances from code generated {@link io.helidon.inject.service.ServiceDescriptor}.
     * <p>
     * The registry is optimized for look-ups based on service type and service contracts, all other
     * lookups trigger a full registry scan.
     *
     * @param lookup lookup criteria to find matching services
     * @return a list of service descriptors that match the lookup criteria
     */
    @SuppressWarnings("deprecation")
    public List<ServiceInfo> lookupServices(Lookup lookup) {
        // a very special lookup
        if (lookup.qualifiers().contains(Qualifier.DRIVEN_BY_NAME)) {
            if (lookup.qualifiers().size() != 1) {
                throw new InjectionException("Invalid injection lookup. @DrivenByName must be the only qualifier used.");
            }
            if (!lookup.contracts().contains(TypeNames.STRING)) {
                throw new InjectionException("Invalid injection lookup. @DrivenByName must use String contract.");
            }
            if (lookup.contracts().size() != 1) {
                throw new InjectionException("Invalid injection lookup. @DrivenByName must use String as the only contract.");
            }
            return List.of(DrivenByName__ServiceDescriptor.INSTANCE);
        }

        Lock currentLock = stateReadLock;
        try {
            // most of our operations are within a read lock
            currentLock.lock();

            lookupCounter.increment();

            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Lookup: " + lookup);
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
                Set<ServiceInfo> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
                if (subsetOfMatches != null) {
                    // the subset is ordered, cannot use parallel, also no need to re-order
                    subsetOfMatches.stream()
                            .filter(lookup::matches)
                            .forEach(result::add);
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }

            if (cacheEnabled) {
                List<ServiceInfo> cacheResult = cache.get(lookup);
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
                    .filter(lookup::matches)
                    .sorted(SERVICE_INFO_COMPARATOR)
                    .forEach(result::add);

            if (cacheEnabled) {
                // upgrade to write lock
                currentLock.unlock();
                currentLock = stateWriteLock;
                currentLock.lock();
                cache.put(lookup, result);
            }

            return result;
        } finally {
            currentLock.unlock();
        }
    }

    <T> List<RegistryInstance<T>> managerInstances(Lookup lookup, ServiceManager<T> manager) {
        return manager.managedServiceInScope()
                .instances(lookup)
                .stream()
                .flatMap(List::stream)
                .map(qi -> manager.registryInstance(lookup, qi))
                .toList();
    }

    List<ServiceInfo> servicesByContract(TypeName drivenBy) {
        Set<ServiceInfo> serviceInfos = servicesByContract.get(drivenBy);
        if (serviceInfos == null) {
            return List.of();
        }
        return List.copyOf(serviceInfos);
    }

    void postBindAllModules() {
        singletonScopeHandler.activate();
    }

    ScopeServices createForScope(TypeName scope, String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        try {
            stateReadLock.lock();
            ScopeServicesFactory factory = scopeServicesFactories.get(scope);
            if (factory != null) {
                return factory.createForScope(id, initialBindings);
            }
        } finally {
            stateReadLock.unlock();
        }

        // upgrade to write lock
        try {
            stateWriteLock.lock();
            ScopeServicesFactory factory = scopeServicesFactories.computeIfAbsent(scope,
                                                                                  it -> new ScopeServicesFactory(this, it));
            return factory.createForScope(id, initialBindings);

        } finally {
            stateWriteLock.unlock();
        }
    }

    Map<TypeName, ActivationResult> close() {
        return singletonScopeHandler.currentScope()
                .map(Scope::services)
                .map(ScopeServices::close)
                .orElseGet(Map::of);
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

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting module binding: " + moduleName);
        }

        ServiceBinder moduleBinder = new ServiceBinderImpl(moduleName);
        module.configure(moduleBinder);

        ServiceDescriptor<ModuleComponent> descriptor = ModuleServiceDescriptor.create(module, moduleName);
        bindInstance(descriptor, module);

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Finished module binding: " + moduleName);
        }
    }

    void bind(Application application) {
        String appName = application.name();

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting application binding: " + appName);
        }

        ServiceInjectionPlanBinder appBinder = new AppBinderImpl(appName);
        application.configure(appBinder);

        ServiceDescriptor<Application> descriptor = ApplicationServiceDescriptor.create(application, appName);
        bindInstance(descriptor, application);

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Finished application binding: " + appName);

        }
    }

    ScopeHandler scopeHandler(TypeName scope) {
        return scopeHandlers.computeIfAbsent(scope, it -> {
            for (ServiceManager<?> scopeHandlerManager : scopeHandlerManagers) {
                ScopeHandler scopeHandler = (ScopeHandler) scopeHandlerManager.managedServiceInScope()
                        .instances(Lookup.EMPTY) // lookup instances
                        .orElseThrow(() -> new InjectionException("There is not scope handler service for scope " + scope.fqName()))
                        .getFirst() // List.getFirst() - Qualified instance
                        .instance();
                if (scopeHandler.supportedScope().equals(scope)) {
                    return scopeHandler;
                }
            }
            throw new InjectionException("A request for service in scope " + scope.fqName()
                                                 + " has been received, yet there is no ScopeHandler available for this scope "
                                                 + "in this registry");
        });
    }

    @SuppressWarnings("unchecked")
    <T> ServiceManager<T> serviceManager(ServiceInfo descriptor) {
        ServiceManager<?> serviceManager = servicesByDescriptor.get(descriptor);
        if (serviceManager == null) {
            throw new InjectionException("A service descriptor was used that is not bound to this service registry: " + descriptor.serviceType());
        }
        return (ServiceManager<T>) serviceManager;
    }

    <T> List<ServiceManager<T>> lookupManagers(Lookup lookup) {
        return lookupServices(lookup)
                .stream()
                .map(this::<T>serviceManager)
                .toList();
    }

    <T> void bindInstance(ServiceDescriptor<T> descriptor, T instance) {
        ServiceProvider<T> provider = new ServiceProvider<>(this, descriptor);
        ManagedService<T> managedService = ManagedService.create(provider, instance);

        bind(new ServiceManager<>(scopeSupplier(descriptor), provider, () -> managedService));
    }

    void bind(ServiceManager<?> provider) {
        try {
            stateWriteLock.lock();
            if (state.currentPhase().ordinal() > Phase.GATHERING_DEPENDENCIES.ordinal()) {
                throw new IllegalStateException(
                        "Attempting to bind to Services in the wrong lifecycle state: " + state.currentPhase());
            }

            ServiceDescriptor<?> descriptor = provider.descriptor();
            servicesByDescriptor.put(descriptor, provider);

            if (descriptor.contracts().contains(ScopeHandler.TYPE_NAME)) {
                scopeHandlerManagers.add(provider);
            }

            TypeName serviceType = descriptor.serviceType();

            // only put if absent, as this may be a lower weight provider for the same type
            ServiceInfo previousValue = servicesByType.putIfAbsent(serviceType, descriptor);
            if (previousValue != null) {
                // a value was already registered for this service type, ignore this registration
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE,
                               "Attempt to register another service provider for the same service type."
                                       + " Service type: " + serviceType.fqName()
                                       + ", existing provider: " + previousValue
                                       + ", new provider: " + provider);
                }
                return;
            }

            servicesByContract.computeIfAbsent(serviceType, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                    .add(descriptor);

            for (TypeName contract : descriptor.contracts()) {
                servicesByContract.computeIfAbsent(contract, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                        .add(descriptor);
            }

            scopeServicesFactories.computeIfAbsent(descriptor.scope(), it -> new ScopeServicesFactory(this, it))
                    .bindService(provider);
        } finally {
            stateWriteLock.unlock();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void bind(ServiceDescriptor<?> serviceDescriptor) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Binding service descriptor: " + serviceDescriptor.infoType().fqName());
        }

        ServiceProvider provider = new ServiceProvider<>(this,
                                                         serviceDescriptor);
        Supplier managedService = () -> ManagedService.create(this, provider);
        ServiceManager<?> manager = new ServiceManager<>(scopeSupplier(serviceDescriptor), provider, managedService);

        // scope handlers have a very specific meaning
        if (serviceDescriptor.contracts().contains(ScopeHandler.TYPE_NAME)) {
            if (!Injection.Singleton.TYPE_NAME.equals(serviceDescriptor.scope())) {
                throw new InjectionException("Services that provide ScopeHandler contract MUST be in Singleton scope, but "
                                                     + serviceDescriptor.serviceType().fqName() + " is in "
                                                     + serviceDescriptor.scope().fqName() + " scope.");
            }
        }

        bind(manager);
    }

    private static <T> List<RegistryInstance<T>> explodeFilterAndSort(Lookup lookup,
                                                                      List<ServiceManager<T>> serviceManagers) {
        // this method is called when we resolve instances, so we can safely assume any scope is active

        List<RegistryInstance<T>> result = new ArrayList<>();

        for (ServiceManager<T> serviceManager : serviceManagers) {
            serviceManager.managedServiceInScope()
                    .instances(lookup)
                    .stream()
                    .flatMap(List::stream)
                    //.filter(it -> lookup.matchesQualifiers(it.qualifiers()))
                    .map(it -> serviceManager.registryInstance(lookup, it))
                    .forEach(result::add);
        }

        result.sort(RegistryInstanceComparator.instance());

        return List.copyOf(result);
    }

    private <T> Supplier<Scope> scopeSupplier(ServiceDescriptor<T> descriptor) {
        TypeName scope = descriptor.scope();
        if (Injection.Singleton.TYPE_NAME.equals(scope)) {
            return () -> singletonScopeHandler.scope;
        } else if (Injection.Service.TYPE_NAME.equals(scope)) {
            return () -> serviceScopeHandler.scope;
        } else {
            // must be a lazy value, as the scope handler may not be available at the time this method is called
            LazyValue<ScopeHandler> scopeHandler = LazyValue.create(() -> scopeHandler(scope));
            return () -> scopeHandler.get()
                    .currentScope() // must be called each time, as we must use the currently active scope, not a cached one
                    .orElseThrow(() -> new ScopeNotActiveException("Scope not active fore service: "
                                                                           + descriptor.serviceType().fqName(),
                                                                   scope));
        }
    }

    private static class ServiceSupplyBase<T> {
        private final Lookup lookup;
        private final List<ServiceManager<T>> managers;

        private ServiceSupplyBase(Lookup lookup, List<ServiceManager<T>> managers) {
            this.managers = managers;
            this.lookup = lookup;
        }

        @Override
        public String toString() {
            return managers.stream()
                    .map(ServiceManager::descriptor)
                    .map(ServiceDescriptor::serviceType)
                    .map(TypeName::fqName)
                    .collect(Collectors.joining(", "));
        }
    }

    static class ServiceSupply<T> extends ServiceSupplyBase<T> implements Supplier<T> {
        private final Supplier<T> value;

        // supply a single instance at runtime based on the manager
        ServiceSupply(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);

            Supplier<T> supplier;

            supplier = () -> explodeFilterAndSort(lookup, managers)
                    .stream()
                    .findFirst()
                    .map(RegistryInstance::get)
                    .orElseThrow(() -> new InjectionServiceProviderException(
                            "Neither of matching services could provide a value. Descriptors: " + managers + ", "
                                    + "lookup: " + super.lookup));

            this.value = supplier;
        }

        @Override
        public T get() {
            return value.get();
        }

        private boolean singletonOnly(List<ServiceManager<T>> managers) {
            return managers.stream()
                    .map(ServiceManager::descriptor)
                    .map(ServiceInfo::scope)
                    .allMatch(Injection.Singleton.TYPE_NAME::equals);
        }
    }

    static class ServiceSupplyOptional<T> extends ServiceSupplyBase<T> implements Supplier<Optional<T>> {
        // supply a single instance at runtime based on the manager
        ServiceSupplyOptional(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
        }

        @Override
        public Optional<T> get() {
            Optional<RegistryInstance<T>> first = explodeFilterAndSort(super.lookup, super.managers)
                    .stream()
                    .findFirst();
            return first.map(Supplier::get);
        }
    }

    static class ServiceSupplyList<T> extends ServiceSupplyBase<T> implements Supplier<List<T>> {
        // supply a single instance at runtime based on the manager
        ServiceSupplyList(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
        }

        @Override
        public List<T> get() {
            Stream<RegistryInstance<T>> stream = explodeFilterAndSort(super.lookup, super.managers)
                    .stream();

            return stream.map(Supplier::get)
                    .toList();
        }
    }

    private static class ServiceScopeHandler implements ScopeHandler {
        private final Scope scope;

        ServiceScopeHandler(Services serviceRegistry) {
            this.scope = new ServiceScope(serviceRegistry);
        }

        @Override
        public TypeName supportedScope() {
            return Injection.Service.TYPE_NAME;
        }

        @Override
        public Optional<Scope> currentScope() {
            return Optional.of(scope);
        }
    }

    private static class SingletonScopeHandler implements ScopeHandler {
        private final Scope scope;

        SingletonScopeHandler(Services serviceRegistry) {
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

        void activate() {
            scope.services().activate();
        }
    }

    private static class SingletonScope implements Scope {
        private final LazyValue<ScopeServices> services;

        SingletonScope(Services serviceRegistry) {
            this.services = LazyValue.create(() -> serviceRegistry.createForScope(Injection.Singleton.TYPE_NAME,
                                                                                  serviceRegistry.id,
                                                                                  Map.of()));
        }

        @Override
        public void close() {
            // no-op, singleton service registry is closed from InjectionServices
        }

        @Override
        public ScopeServices services() {
            return services.get();
        }
    }

    private static class ServiceScope implements Scope {
        private final ScopeServices services;

        ServiceScope(Services serviceRegistry) {
            this.services = new ServiceScopeServices(serviceRegistry, serviceRegistry.id);
        }

        @Override
        public void close() {
            // no-op
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
            Services.this.bind(serviceDescriptor);
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
            ServiceManager<?> serviceManager = Services.this.serviceManager(serviceInfo);

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "binding injection plan to " + serviceManager);
            }

            return serviceManager.servicePlanBinder();
        }

        @Override
        public void interceptors(ServiceInfo... descriptors) {
            Services.this.interceptors(descriptors);
        }

        @Override
        public String toString() {
            return "Service binder for application: " + appName;
        }
    }
}
