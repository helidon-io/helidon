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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;
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

class ServicesImpl implements Services, ServiceBinder {
    private static final System.Logger LOGGER = System.getLogger(Services.class.getName());
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

    private final Map<Lookup, List<RegistryServiceProvider<?>>> cache = new ConcurrentHashMap<>();
    // a map of service provider instances to their activators, so we can correctly handle activation requests
    private final Map<RegistryServiceProvider<?>, Activator<?>> providersToActivators = new IdentityHashMap<>();
    private final Counter lookupCounter;
    private final InjectionConfig cfg;
    private final Map<TypeName, RegistryServiceProvider<?>> servicesByTypeName;
    private final Map<TypeName, Set<RegistryServiceProvider<?>>> servicesByContract;
    private final Counter cacheLookupCounter;
    private final Counter cacheHitCounter;
    private final InjectionServicesImpl injectionServices;
    private final State state;
    private final ServiceProviderRegistry spRegistry;
    private final Map<TypeName, Map<ServiceDescriptor<?>, Supplier<Activator<?>>>> activatorsByScope;

    private volatile List<RegistryServiceProvider<Interception.Interceptor>> interceptors;

    ServicesImpl(InjectionServicesImpl injectionServices, State state) {
        this.injectionServices = injectionServices;
        this.state = state;
        this.cfg = injectionServices.config();

        this.lookupCounter = Metrics.globalRegistry()
                .getOrCreate(Counter.builder("io.helidon.inject.lookups")
                                     .description("Number of lookups in the service registry")
                                     .scope(Meter.Scope.VENDOR));
        if (cfg.serviceLookupCaching()) {
            this.cacheLookupCounter = Metrics.globalRegistry()
                    .getOrCreate(Counter.builder("io.helidon.inject.cacheLookups")
                                         .description("Number of lookups in cache in the service registry")
                                         .scope(Meter.Scope.VENDOR));
            this.cacheHitCounter = Metrics.globalRegistry()
                    .getOrCreate(Counter.builder("io.helidon.inject.cacheHits")
                                         .description("Number of cache hits in the service registry")
                                         .scope(Meter.Scope.VENDOR));
        } else {
            this.cacheLookupCounter = null;
            this.cacheHitCounter = null;
        }

        /*
        note for future:
        we can optimize this if needed - we can protect parallel operations on binding, and once binding
        is done (and dynamic updates are not permitted), these maps are immutable
         */
        this.servicesByTypeName = new ConcurrentHashMap<>();
        this.servicesByContract = new ConcurrentHashMap<>();
        this.activatorsByScope = new ConcurrentHashMap<>();

        this.spRegistry = new ServiceProviderRegistryImpl(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Supplier<T>> first(Lookup criteria) {
        return this.<T>lookup(criteria, 1)
                .stream()
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<Supplier<T>> all(Lookup criteria) {
        return this.lookup(criteria, Integer.MAX_VALUE);
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
        Set<TypeName> scopes = serviceDescriptor.scopes();
        if (scopes.isEmpty() || scopes.contains(InjectTypes.SINGLETON)) {
            bind(activatorProvider.activator(this, serviceDescriptor));
        }

        for (TypeName scope : scopes) {
            if (!InjectTypes.SINGLETON.equals(scope)) {
                // singleton is never handled by custom scopes
                activatorsByScope.computeIfAbsent(scope, it -> new HashMap<>())
                        .put(serviceDescriptor, () -> activatorProvider.activator(this, serviceDescriptor));
            }
        }
    }

    @Override
    public InjectionServicesImpl injectionServices() {
        return injectionServices;
    }

    @Override
    public ServiceBinder binder() {
        return this;
    }

    @Override
    public ServiceProviderRegistry serviceProviders() {
        return spRegistry;
    }

    ScopeServices createForScope(TypeName scopeType) {
        return new ScopeServicesImpl(this, scopeType, Optional.ofNullable(activatorsByScope.get(scopeType)).orElseGet(Map::of));
    }

    @SuppressWarnings("unchecked")
    <T> List<RegistryServiceProvider<T>> allProviders(Lookup criteria) {
        return this.lookup(criteria, Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    <T> RegistryServiceProvider<T> serviceProvider(ServiceInfo serviceInfo) {
        RegistryServiceProvider<?> serviceProvider = servicesByTypeName.get(serviceInfo.serviceType());
        if (serviceProvider == null) {
            throw new NoSuchElementException("Requested service is not managed by this registry: "
                                                     + serviceInfo.serviceType().fqName());
        }
        return (RegistryServiceProvider<T>) serviceProvider;
    }

    void bindSelf() {
        bind(ServicesActivator.create(this));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void interceptors(ServiceInfo... serviceInfos) {
        if (this.interceptors == null) {
            List list = Stream.of(serviceInfos)
                    .map(this::serviceProvider)
                    .toList();
            this.interceptors = List.copyOf(list);
        }
    }

    List<RegistryServiceProvider<Interception.Interceptor>> interceptors() {
        if (interceptors == null) {
            interceptors = allProviders(Lookup.builder()
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
        bind(InjectionModuleActivator.create(this, module, moduleName));

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
        bind(InjectionApplicationActivator.create(this, application, appName));

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Finished application binding: " + appName);

        }
    }

    Optional<Activator<?>> activator(RegistryServiceProvider<?> instance) {
        return Optional.ofNullable(providersToActivators.get(instance));
    }

    List<RegistryServiceProvider<?>> allProviders() {
        Set<RegistryServiceProvider<?>> result = new HashSet<>(servicesByTypeName.values());
        servicesByContract.values()
                .forEach(result::addAll);

        return List.copyOf(result);
    }

    private static boolean hasNamed(Set<Qualifier> qualifiers) {
        return qualifiers.stream()
                .anyMatch(it -> it.typeName().equals(Injection.Named.TYPE_NAME));
    }

    void bind(Activator<?> activator) {
        if (state.currentPhase().ordinal() > Phase.GATHERING_DEPENDENCIES.ordinal()) {
            if (!cfg.permitsDynamic()) {
                throw new IllegalStateException(
                        "Attempting to bind to Services that do not support dynamic updates. Set option permitsDynamic, "
                                + "or configuration option 'inject.permits-dynamic=true' to enable");
            }
        }

        /*
        We cannot start activation for providers that are not singleton (or no-scope)
         */
        Set<TypeName> scopes = activator.descriptor().scopes();
        if (scopes.isEmpty() || scopes.contains(InjectTypes.SINGLETON)) {
            // make sure the activator has a chance to do something, such as create the initial service provider instance
            activator.activate(ActivationRequest.builder()
                                       .targetPhase(Phase.INIT)
                                       .throwIfError(false)
                                       .build());
            RegistryServiceProvider<?> serviceProvider = activator.serviceProvider();
            this.providersToActivators.put(serviceProvider, activator);

            TypeName serviceType = serviceProvider.serviceType();

            // only put if absent, as this may be a lower weight provider for the same type
            RegistryServiceProvider<?> previousValue = servicesByTypeName.putIfAbsent(serviceType, serviceProvider);
            if (previousValue != null) {
                // a value was already registered for this service type, ignore this registration
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Attempt to register another service provider for the same service type."
                            + " Service type: " + serviceType.fqName()
                            + ", existing provider: " + previousValue
                            + ", new provider: " + serviceProvider);
                }
                return;
            }
            servicesByContract.computeIfAbsent(serviceType, it -> new TreeSet<>(ServiceProviderComparator.instance()))
                    .add(serviceProvider);

            for (TypeName contract : serviceProvider.contracts()) {
                servicesByContract.computeIfAbsent(contract, it -> new TreeSet<>(ServiceProviderComparator.instance()))
                        .add(serviceProvider);
            }
        } else {
            for (TypeName scope : scopes) {
                activatorsByScope.computeIfAbsent(scope, it -> new ArrayList<>())
                        .add(activator);
            }
        }
    }

    private static boolean hasNamed(Set<Qualifier> qualifiers) {
        return qualifiers.stream()
                .anyMatch(it -> it.typeName().equals(InjectTypes.NAMED));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> List lookup(Lookup criteria, int limit) {
        lookupCounter.increment();

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Lookup: " + criteria + ", limit: " + limit);
        }

        if (criteria.serviceType().isPresent()) {
            // when a specific service type is requested, we go for it
            RegistryServiceProvider<?> exact = servicesByTypeName.get(criteria.serviceType().get());
            if (exact != null) {
                return explodeFilterAndSort(List.of(exact), criteria);
            }
        }

        if (1 == criteria.contracts().size()) {
            TypeName theOnlyContractRequested = criteria.contracts().iterator().next();
            Set<RegistryServiceProvider<?>> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
            if (subsetOfMatches != null) {
                // the subset is ordered, cannot use parallel
                List<RegistryServiceProvider<?>> result = subsetOfMatches.stream()
                        .filter(criteria::matches)
                        .limit(limit)
                        .toList();
                if (!result.isEmpty()) {
                    return explodeFilterAndSort(result, criteria);
                }
            }
            if (criteria.serviceType().isEmpty()) {
                // we may have a request for service type and not a contract
                RegistryServiceProvider<?> exact = servicesByTypeName.get(theOnlyContractRequested);
                if (exact != null) {
                    return explodeFilterAndSort(List.of(exact), criteria);
                }
            }
        }

        if (cfg.serviceLookupCaching()) {
            List result = cache.get(criteria);
            cacheLookupCounter.increment();
            if (result != null) {
                cacheHitCounter.increment();
                return result;
            }
        }

        // table scan :-(
        List result = servicesByTypeName.values()
                .stream()
                .filter(criteria::matches)
                .sorted(ServiceProviderComparator.instance())
                .limit(limit)
                .toList();

        if (!result.isEmpty()) {
            result = explodeFilterAndSort(result, criteria);
        }

        if (cfg.serviceLookupCaching()) {
            cache.put(criteria, result);
        }

        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> List<Supplier<T>> explodeFilterAndSort(List<RegistryServiceProvider<?>> coll, Lookup criteria) {
        List<RegistryServiceProvider<?>> exploded;
        if ((coll.size() > 1)
                || coll.stream().anyMatch(sp -> sp instanceof ServiceProviderProvider)) {
            exploded = new ArrayList<>();

            coll.forEach(s -> {
                if (s instanceof ServiceProviderProvider spp) {
                    List<? extends RegistryServiceProvider<?>> subList = spp.serviceProviders(criteria, true, true);
                    if (subList != null && !subList.isEmpty()) {
                        subList.stream().filter(Objects::nonNull).forEach(exploded::add);
                    }
                } else {
                    exploded.add(s);
                }
            });
        } else {
            exploded = new ArrayList<>(coll);
        }

        if (exploded.size() > 1) {
            exploded.sort(ServiceProviderComparator.instance());
        }

        // the providers are sorted by weight and other properties
        // we need to have unnamed providers before named ones (if criteria does not contain a Named qualifier)
        // in similar fashion, if criteria does not contain any qualifier, put unqualified instances first
        if (criteria.qualifiers().isEmpty()) {
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
        } else if (!hasNamed(criteria.qualifiers())) {
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
            RegistryServiceProvider<?> serviceProvider = ServicesImpl.this.serviceProvider(serviceInfo);

            Optional<Binder> binder = serviceProvider.serviceProviderBindable()
                    .flatMap(ServiceProviderBindable::injectionPlanBinder);

            if (binder.isEmpty()) {
                // basically this means this service will not support compile-time injection
                LOGGER.log(Level.WARNING,
                           "service provider is not capable of being bound to injection points: " + serviceProvider);
                return new NoOpBinder(serviceProvider);
            }
            Binder result = binder.get();

            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "binding injection plan to " + result);
            }

            return result;
        }

        @Override
        public void interceptors(ServiceInfo... serviceInfos) {
            ServicesImpl.this.interceptors(serviceInfos);
        }

        @Override
        public String toString() {
            return "Service binder for application: " + appName;
        }
    }
}
