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

package io.helidon.pico.services;

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

import io.helidon.pico.Application;
import io.helidon.pico.DefaultMetrics;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.InjectionException;
import io.helidon.pico.Intercepted;
import io.helidon.pico.Metrics;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.Resetable;

import jakarta.inject.Provider;

/**
 * The default reference implementation of {@link io.helidon.pico.Services}.
 */
class DefaultServices implements Services, Resetable {
    private static final ServiceProviderComparator COMPARATOR = new ServiceProviderComparator();

    private final ConcurrentHashMap<String, ServiceProvider<?>> servicesByTypeName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<ServiceProvider<?>>> servicesByContract = new ConcurrentHashMap<>();
    private final Map<ServiceInfoCriteria, List<ServiceProvider<?>>> cache = new ConcurrentHashMap<>();
    private final PicoServicesConfig cfg;
    private final AtomicInteger lookupCount = new AtomicInteger();
    private final AtomicInteger cacheLookupCount = new AtomicInteger();
    private final AtomicInteger cacheHitCount = new AtomicInteger();

    /**
     * The constructor taking a configuration.
     *
     * @param cfg the config
     */
    DefaultServices(
            PicoServicesConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg);
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
    public synchronized boolean reset(
            boolean deep) {
        assertPermitsDynamic(cfg);

        boolean changed = (deep || !servicesByTypeName.isEmpty() || lookupCount.get() > 0 || cacheLookupCount.get() > 0);

        if (deep) {
            servicesByTypeName.values().forEach((sp) -> {
                if (sp instanceof Resetable) {
                    ((Resetable) sp).reset(true);
                }
            });
            servicesByTypeName.clear();
            servicesByContract.clear();
        }

        clearCacheAndMetrics();

        return changed;
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
                .services(size())
                .lookupCount(lookupCount.get())
                .cacheLookupCount(cacheLookupCount.get())
                .cacheHitCount(cacheHitCount.get())
                .build();
    }

    @Override
    public <T> Optional<ServiceProvider<T>> lookupFirst(
            Class<T> type,
            boolean expected) {
        DefaultServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(type.getName())
                .build();
        return lookupFirst(criteria, expected);
    }

    @Override
    public <T> Optional<ServiceProvider<T>> lookupFirst(
            Class<T> type,
            String name,
            boolean expected) {
        DefaultServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(type.getName())
                .addQualifier(DefaultQualifierAndValue.createNamed(name))
                .build();
        return lookupFirst(criteria, expected);
    }

    @Override
    public <T> Optional<ServiceProvider<T>> lookupFirst(
            ServiceInfoCriteria criteria,
            boolean expected) {
        List<ServiceProvider<T>> result = lookup(criteria, expected, 1);
        assert (!expected || !result.isEmpty());
        return (result.isEmpty()) ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public <T> List<ServiceProvider<T>> lookupAll(
            Class<T> type) {
        DefaultServiceInfoCriteria serviceInfo = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(type.getName())
                .build();
        return lookup(serviceInfo, false, Integer.MAX_VALUE);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<ServiceProvider<?>> lookupAll(
            ServiceInfoCriteria criteria,
            boolean expected) {
        List<ServiceProvider<?>> result = (List) lookup(criteria, expected, Integer.MAX_VALUE);
        assert (!expected || !result.isEmpty());
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <T> List<ServiceProvider<T>> lookup(
            ServiceInfoCriteria criteria,
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
                    return explodeAndSort(List.of(exact), criteria, expected);
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
                        return explodeAndSort(result, criteria, expected);
                    }
                }
            }
        }

        if (cfg.serviceLookupCaching()) {
            result = cache.get(criteria);
            cacheLookupCount.incrementAndGet();
            if (Objects.nonNull(result)) {
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
            result = explodeAndSort(result, criteria, expected);
        }

        if (cfg.serviceLookupCaching()) {
            cache.put(criteria, List.copyOf(result));
        }

        return (List) result;
    }

    ServiceProvider<?> serviceProviderFor(
            String serviceTypeName) {
        ServiceProvider<?> serviceProvider = servicesByTypeName.get(serviceTypeName);
        if (serviceProvider == null) {
            throw resolutionBasedInjectionError(serviceTypeName);
        }
        return serviceProvider;
    }

    List<ServiceProvider<?>> allServiceProviders(
            boolean explode) {
        if (explode) {
            return explodeAndSort(servicesByTypeName.values(), null, false);
        }

        return new ArrayList<>(servicesByTypeName.values());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> List<T> explodeAndSort(
            Collection<?> coll,
            ServiceInfoCriteria criteria,
            boolean expected) {
        List result;

        if ((coll.size() > 1)
                || coll.stream().anyMatch(sp -> sp instanceof ServiceProviderProvider)) {
            result = new ArrayList<>();

            coll.forEach(s -> {
                if (s instanceof ServiceProviderProvider) {
                    List<? extends ServiceProvider<?>> subList = ((ServiceProviderProvider) s)
                            .serviceProviders(criteria, true, true);
                    if (Objects.nonNull(subList) && !subList.isEmpty()) {
                        subList.stream().filter(Objects::nonNull).forEach(result::add);
                    }
                } else {
                    result.add(s);
                }
            });

            if (result.size() > 1) {
                result.sort(serviceProviderComparator());
            }

            return result;
        } else {
            result = (coll instanceof List) ? (List) coll : new ArrayList<>(coll);
        }

        if (expected && result.isEmpty()) {
            throw resolutionBasedInjectionError(criteria);
        }

        return result;
    }

    static boolean hasContracts(
            ServiceInfoCriteria criteria) {
        return !criteria.contractsImplemented().isEmpty();
    }

    static boolean isIntercepted(
            ServiceProvider<?> sp) {
        return (sp instanceof ServiceProviderBindable && ((ServiceProviderBindable<?>) sp).isIntercepted());
    }

    ServiceBinder createServiceBinder(
            PicoServices picoServices,
            DefaultServices services,
            String moduleName) {
        return new DefaultServiceBinder(picoServices, services, moduleName);
    }

    void bind(
            PicoServices picoServices,
            DefaultInjectionPlanBinder binder,
            Application app) {
        app.configure(binder);
        bind(createServiceProvider(app, picoServices));
    }

    void bind(
            PicoServices picoServices,
            io.helidon.pico.Module module) {
        String moduleName = module.named().orElse(null);
        ServiceBinder moduleServiceBinder = createServiceBinder(picoServices, this, moduleName);
        module.configure(moduleServiceBinder);
        bind(createServiceProvider(module, moduleName, picoServices));
    }

    void bind(
            ServiceProvider<?> serviceProvider) {
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
                .filter((q) -> q.typeName().name().equals(Intercepted.class.getName()))
                .findFirst();
        if (interceptedQualifier.isPresent()) {
            // assumption: expected that the root service provider is registered prior to any interceptors ...
            String interceptedServiceTypeName = Objects.requireNonNull(interceptedQualifier.get().value().orElseThrow());
            ServiceProvider<?> interceptedSp = lookupFirst(DefaultServiceInfoCriteria.builder()
                                                                   .serviceTypeName(interceptedServiceTypeName)
                                                                   .build(), false).orElse(null);
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
            throw new IllegalStateException("services are configured to not permit dynamic");
        }
    }

    private ServiceProvider<?> createServiceProvider(
            io.helidon.pico.Module module,
            String moduleName,
            PicoServices picoServices) {
        return new BasicModuleServiceProvider(module, moduleName, picoServices);
    }

    private ServiceProvider<?> createServiceProvider(
            Application app,
            PicoServices picoServices) {
        return new BasicApplicationServiceProvider(app, picoServices);
    }

    static ServiceInfo toValidatedServiceInfo(
            ServiceProvider<?> serviceProvider) {
        ServiceInfo info = serviceProvider.serviceInfo();
        Objects.requireNonNull(info.serviceTypeName(), () -> "service type name is required for " + serviceProvider);
        return info;
    }

    static InjectionException serviceProviderAlreadyBoundInjectionError(
            ServiceProvider<?> previous,
            ServiceProvider<?> sp) {
        return new InjectionException("service provider already bound to " + previous, null, sp);
    }

    static InjectionException resolutionBasedInjectionError(
            ServiceInfoCriteria ctx) {
        return new InjectionException("expected to resolve a service matching " + ctx);
    }

    static InjectionException resolutionBasedInjectionError(
            String serviceTypeName) {
        return resolutionBasedInjectionError(DefaultServiceInfoCriteria.builder().serviceTypeName(serviceTypeName).build());
    }

    static String toDescription(
            Object provider) {
        if (provider instanceof Optional) {
            provider = ((Optional<?>) provider).orElse(null);
        }

        if (provider instanceof ServiceProvider) {
            return ((ServiceProvider<?>) provider).description();
        }
        return String.valueOf(provider);
    }

    static List<String> toDescriptions(
            Collection<?> coll) {
        return coll.stream().map(DefaultServices::toDescription).collect(Collectors.toList());
    }

}
