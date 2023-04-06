/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.pico.api.Application;
import io.helidon.pico.api.CallingContext;
import io.helidon.pico.api.CallingContextFactory;
import io.helidon.pico.api.DefaultMetrics;
import io.helidon.pico.api.DefaultQualifierAndValue;
import io.helidon.pico.api.DefaultServiceInfoCriteria;
import io.helidon.pico.api.InjectionException;
import io.helidon.pico.api.Intercepted;
import io.helidon.pico.api.Metrics;
import io.helidon.pico.api.Module;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.Resettable;
import io.helidon.pico.api.ServiceBinder;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.ServiceProviderBindable;
import io.helidon.pico.api.ServiceProviderProvider;
import io.helidon.pico.api.Services;

import jakarta.inject.Provider;

import static io.helidon.pico.api.CallingContext.toErrorMessage;

/**
 * The default reference implementation of {@link Services}.
 */
class DefaultServices implements Services, ServiceBinder, Resettable {
    private static final ServiceProviderComparator COMPARATOR = ServiceProviderComparator.create();

    private final ConcurrentHashMap<String, ServiceProvider<?>> servicesByTypeName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<ServiceProvider<?>>> servicesByContract = new ConcurrentHashMap<>();
    private final Map<ServiceInfoCriteria, List<ServiceProvider<?>>> cache = new ConcurrentHashMap<>();
    private final PicoServicesConfig cfg;
    private final AtomicInteger lookupCount = new AtomicInteger();
    private final AtomicInteger cacheLookupCount = new AtomicInteger();
    private final AtomicInteger cacheHitCount = new AtomicInteger();
    private volatile State stateWatchOnly; // we are watching and not mutating this state - owned by DefaultPicoServices

    /**
     * The constructor taking a configuration.
     *
     * @param cfg the config
     */
    DefaultServices(PicoServicesConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> List<T> explodeFilterAndSort(Collection<?> coll,
                                            ServiceInfoCriteria criteria,
                                            boolean expected) {
        List exploded;
        if ((coll.size() > 1)
                || coll.stream().anyMatch(sp -> sp instanceof ServiceProviderProvider)) {
            exploded = new ArrayList<>();

            coll.forEach(s -> {
                if (s instanceof ServiceProviderProvider) {
                    List<? extends ServiceProvider<?>> subList = ((ServiceProviderProvider) s)
                            .serviceProviders(criteria, true, true);
                    if (subList != null && !subList.isEmpty()) {
                        subList.stream().filter(Objects::nonNull).forEach(exploded::add);
                    }
                } else {
                    exploded.add(s);
                }
            });
        } else {
            exploded = (coll instanceof List) ? (List) coll : new ArrayList<>(coll);
        }

        List result;
        if (criteria.includeIntercepted()) {
            result = exploded;
        } else {
            result = (List) exploded.stream()
                    .filter(sp -> !(sp instanceof AbstractServiceProvider) || !((AbstractServiceProvider) sp).isIntercepted())
                    .collect(Collectors.toList());
        }

        if (expected && result.isEmpty()) {
            throw resolutionBasedInjectionError(criteria);
        }

        if (result.size() > 1) {
            result.sort(serviceProviderComparator());
        }

        return result;
    }

    static boolean hasContracts(ServiceInfoCriteria criteria) {
        return !criteria.contractsImplemented().isEmpty();
    }

    static boolean isIntercepted(ServiceProvider<?> sp) {
        return (sp instanceof ServiceProviderBindable && ((ServiceProviderBindable<?>) sp).isIntercepted());
    }

    /**
     * First use weight, then use FQN of the service type name as the secondary comparator if weights are the same.
     *
     * @return the pico comparator
     * @see ServiceProviderComparator
     */
    static Comparator<? super Provider<?>> serviceProviderComparator() {
        return COMPARATOR;
    }

    static void assertPermitsDynamic(PicoServicesConfig cfg) {
        if (!cfg.permitsDynamic()) {
            String desc = "Services are configured to prevent dynamic updates.\n"
                    + "Set config '"
                    + PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC
                    + " = true' to enable";
            Optional<CallingContext> callCtx = CallingContextFactory.create(false);
            String msg = (callCtx.isEmpty()) ? toErrorMessage(desc) : toErrorMessage(callCtx.get(), desc);
            throw new IllegalStateException(msg);
        }
    }

    static ServiceInfo toValidatedServiceInfo(ServiceProvider<?> serviceProvider) {
        ServiceInfo info = serviceProvider.serviceInfo();
        Objects.requireNonNull(info.serviceTypeName(), () -> "service type name is required for " + serviceProvider);
        return info;
    }

    static InjectionException serviceProviderAlreadyBoundInjectionError(ServiceProvider<?> previous,
                                                                        ServiceProvider<?> sp) {
        return new InjectionException("Service provider already bound to " + previous, null, sp);
    }

    static InjectionException resolutionBasedInjectionError(ServiceInfoCriteria ctx) {
        return new InjectionException("Expected to resolve a service matching " + ctx);
    }

    static InjectionException resolutionBasedInjectionError(String serviceTypeName) {
        return resolutionBasedInjectionError(DefaultServiceInfoCriteria.builder().serviceTypeName(serviceTypeName).build());
    }

    /**
     * Total size of the service registry.
     *
     * @return total size of the service registry
     */
    public int size() {
        return servicesByTypeName.size();
    }

    /**
     * Performs a reset. When deep is false this will only clear the cache and metrics count. When deep is true will also
     * deeply reset each service in the registry as well as clear out the registry. Dynamic must be permitted in config for
     * reset to occur.
     *
     * @param deep set to true will iterate through every service in the registry to attempt a reset on each service as well
     * @return true if reset had any affect
     * @throws java.lang.IllegalStateException when dynamic is not permitted
     */
    @Override
    public boolean reset(boolean deep) {
        if (Phase.ACTIVATION_STARTING != currentPhase()) {
            assertPermitsDynamic(cfg);
        }

        boolean changed = (deep || !servicesByTypeName.isEmpty() || lookupCount.get() > 0 || cacheLookupCount.get() > 0);

        if (deep) {
            servicesByTypeName.values().forEach(sp -> {
                if (sp instanceof Resettable) {
                    ((Resettable) sp).reset(true);
                }
            });
            servicesByTypeName.clear();
            servicesByContract.clear();
        }

        clearCacheAndMetrics();

        return changed;
    }

    @Override
    public <T> Optional<ServiceProvider<T>> lookupFirst(Class<T> type,
                                                        boolean expected) {
        DefaultServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(type.getName())
                .build();
        return lookupFirst(criteria, expected);
    }

    @Override
    public <T> Optional<ServiceProvider<T>> lookupFirst(Class<T> type,
                                                        String name,
                                                        boolean expected) {
        DefaultServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(type.getName())
                .addQualifier(DefaultQualifierAndValue.createNamed(name))
                .build();
        return lookupFirst(criteria, expected);
    }

    @Override
    public <T> Optional<ServiceProvider<T>> lookupFirst(ServiceInfoCriteria criteria,
                                                        boolean expected) {
        List<ServiceProvider<T>> result = lookup(criteria, expected, 1);
        assert (!expected || !result.isEmpty());
        return (result.isEmpty()) ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public <T> List<ServiceProvider<T>> lookupAll(Class<T> type) {
        DefaultServiceInfoCriteria serviceInfo = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(type.getName())
                .build();
        return lookup(serviceInfo, false, Integer.MAX_VALUE);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<ServiceProvider<?>> lookupAll(ServiceInfoCriteria criteria,
                                              boolean expected) {
        List<ServiceProvider<?>> result = (List) lookup(criteria, expected, Integer.MAX_VALUE);
        assert (!expected || !result.isEmpty());
        return result;
    }

    @Override
    public void bind(ServiceProvider<?> serviceProvider) {
        if (currentPhase().ordinal() > Phase.GATHERING_DEPENDENCIES.ordinal()) {
            assertPermitsDynamic(cfg);
        }

        ServiceInfo serviceInfo = toValidatedServiceInfo(serviceProvider);
        String serviceTypeName = serviceInfo.serviceTypeName();

        ServiceProvider<?> previous = servicesByTypeName.putIfAbsent(serviceTypeName, serviceProvider);
        if (previous != null && previous != serviceProvider) {
            if (cfg.permitsDynamic()) {
                DefaultPicoServices.LOGGER.log(System.Logger.Level.WARNING,
                                               "overwriting " + previous + " with " + serviceProvider);
                servicesByTypeName.put(serviceTypeName, serviceProvider);
            } else {
                throw serviceProviderAlreadyBoundInjectionError(previous, serviceProvider);
            }
        }

        // special handling in case we are an interceptor...
        Set<QualifierAndValue> qualifiers = serviceInfo.qualifiers();
        Optional<QualifierAndValue> interceptedQualifier = qualifiers.stream()
                .filter(q -> q.typeName().name().equals(Intercepted.class.getName()))
                .findFirst();
        if (interceptedQualifier.isPresent()) {
            // assumption: expected that the root service provider is registered prior to any interceptors
            String interceptedServiceTypeName = Objects.requireNonNull(interceptedQualifier.get().value().orElseThrow());
            ServiceProvider<?> interceptedSp = lookupFirst(DefaultServiceInfoCriteria.builder()
                                                                   .serviceTypeName(interceptedServiceTypeName)
                                                                   .build(), true).orElse(null);
            if (interceptedSp instanceof ServiceProviderBindable) {
                ((ServiceProviderBindable<?>) interceptedSp).interceptor(serviceProvider);
            }
        }

        servicesByContract.compute(serviceTypeName, (contract, servicesSharingThisContract) -> {
            if (servicesSharingThisContract == null) {
                servicesSharingThisContract = new TreeSet<>(serviceProviderComparator());
            }
            boolean added = servicesSharingThisContract.add(serviceProvider);
            assert (added) : "expected to have added: " + serviceProvider;
            return servicesSharingThisContract;
        });
        for (String cn : serviceInfo.contractsImplemented()) {
            servicesByContract.compute(cn, (contract, servicesSharingThisContract) -> {
                if (servicesSharingThisContract == null) {
                    servicesSharingThisContract = new TreeSet<>(serviceProviderComparator());
                }
                boolean ignored = servicesSharingThisContract.add(serviceProvider);
                return servicesSharingThisContract;
            });
        }
    }

    void state(State state) {
        this.stateWatchOnly = Objects.requireNonNull(state);
    }

    Phase currentPhase() {
        return (stateWatchOnly == null) ? Phase.INIT : stateWatchOnly.currentPhase();
    }

    Map<ServiceInfoCriteria, List<ServiceProvider<?>>> cache() {
        return Map.copyOf(cache);
    }

    /**
     * Clear the cache and metrics.
     */
    void clearCacheAndMetrics() {
        cache.clear();
        lookupCount.set(0);
        cacheLookupCount.set(0);
        cacheHitCount.set(0);
    }

    Metrics metrics() {
        return DefaultMetrics.builder()
                .serviceCount(size())
                .lookupCount(lookupCount.get())
                .cacheLookupCount(cacheLookupCount.get())
                .cacheHitCount(cacheHitCount.get())
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <T> List<ServiceProvider<T>> lookup(ServiceInfoCriteria criteria,
                                        boolean expected,
                                        int limit) {
        List<ServiceProvider<?>> result;

        lookupCount.incrementAndGet();

        if (hasContracts(criteria)) {
            String serviceTypeName = criteria.serviceTypeName().orElse(null);
            boolean hasOneContractInCriteria = (1 == criteria.contractsImplemented().size());
            String theOnlyContractRequested = (hasOneContractInCriteria)
                    ? criteria.contractsImplemented().iterator().next() : null;
            if (serviceTypeName == null
                    && hasOneContractInCriteria
                    && criteria.qualifiers().isEmpty()) {
                serviceTypeName = theOnlyContractRequested;
            }
            if (serviceTypeName != null) {
                ServiceProvider exact = servicesByTypeName.get(serviceTypeName);
                if (exact != null && !isIntercepted(exact)) {
                    return explodeFilterAndSort(List.of(exact), criteria, expected);
                }
            }
            if (hasOneContractInCriteria) {
                Set<ServiceProvider<?>> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
                if (subsetOfMatches != null) {
                    result = subsetOfMatches.stream().parallel()
                            .filter(sp -> sp.serviceInfo().matches(criteria))
                            .limit(limit)
                            .collect(Collectors.toList());
                    if (!result.isEmpty()) {
                        return explodeFilterAndSort(result, criteria, expected);
                    }
                }
            }
        }

        if (cfg.serviceLookupCaching()) {
            result = cache.get(criteria);
            cacheLookupCount.incrementAndGet();
            if (result != null) {
                cacheHitCount.incrementAndGet();
                return (List) result;
            }
        }

        // table scan :-(
        result = servicesByTypeName.values()
                .stream().parallel()
                .filter(sp -> sp.serviceInfo().matches(criteria))
                .limit(limit)
                .collect(Collectors.toList());
        if (expected && result.isEmpty()) {
            throw resolutionBasedInjectionError(criteria);
        }

        if (!result.isEmpty()) {
            result = explodeFilterAndSort(result, criteria, expected);
        }

        if (cfg.serviceLookupCaching()) {
            cache.put(criteria, List.copyOf(result));
        }

        return (List) result;
    }

    ServiceProvider<?> serviceProviderFor(String serviceTypeName) {
        ServiceProvider<?> serviceProvider = servicesByTypeName.get(serviceTypeName);
        if (serviceProvider == null) {
            throw resolutionBasedInjectionError(serviceTypeName);
        }
        return serviceProvider;
    }

    List<ServiceProvider<?>> allServiceProviders(boolean explode) {
        if (explode) {
            return explodeFilterAndSort(servicesByTypeName.values(), PicoServices.EMPTY_CRITERIA, false);
        }

        return new ArrayList<>(servicesByTypeName.values());
    }

    ServiceBinder createServiceBinder(PicoServices picoServices,
                                      DefaultServices services,
                                      String moduleName,
                                      boolean trusted) {
        assert (picoServices.services() == services);
        return DefaultServiceBinder.create(picoServices, moduleName, trusted);
    }

    void bind(PicoServices picoServices,
              DefaultInjectionPlanBinder binder,
              Application app) {
        String appName = app.named().orElse(app.getClass().getName());
        boolean isLoggable = DefaultPicoServices.LOGGER.isLoggable(System.Logger.Level.INFO);
        if (isLoggable) {
            DefaultPicoServices.LOGGER.log(System.Logger.Level.INFO, "starting binding application: " + appName);
        }
        try {
            app.configure(binder);
            bind(createServiceProvider(app, picoServices));
            if (isLoggable) {
                DefaultPicoServices.LOGGER.log(System.Logger.Level.INFO, "finished binding application: " + appName);
            }
        } catch (Exception e) {
            throw new PicoException("Failed to process: " + app, e);
        }
    }

    void bind(PicoServices picoServices,
              Module module,
              boolean initializing) {
        String moduleName = module.named().orElse(module.getClass().getName());
        boolean isLoggable = DefaultPicoServices.LOGGER.isLoggable(System.Logger.Level.TRACE);
        if (isLoggable) {
            DefaultPicoServices.LOGGER.log(System.Logger.Level.TRACE, "starting binding module: " + moduleName);
        }
        ServiceBinder moduleServiceBinder = createServiceBinder(picoServices, this, moduleName, initializing);
        module.configure(moduleServiceBinder);
        bind(createServiceProvider(module, moduleName, picoServices));
        if (isLoggable) {
            DefaultPicoServices.LOGGER.log(System.Logger.Level.TRACE, "finished binding module: " + moduleName);
        }
    }

    private ServiceProvider<?> createServiceProvider(Module module,
                                                     String moduleName,
                                                     PicoServices picoServices) {
        return new PicoModuleServiceProvider(module, moduleName, picoServices);
    }

    private ServiceProvider<?> createServiceProvider(Application app,
                                                     PicoServices picoServices) {
        return new PicoApplicationServiceProvider(app, picoServices);
    }

}
