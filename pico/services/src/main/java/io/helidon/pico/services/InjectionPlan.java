/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.pico.ContextualServiceQuery;
import io.helidon.pico.DefaultContextualServiceQuery;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.InjectionException;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.Interceptor;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.BasicInjectionPlan;
import io.helidon.pico.spi.InjectionResolver;

/**
 * The injection plan for a given service provider and element belonging to that service provider. This plan can be created during
 * compile-time, and then just loaded from the {@link io.helidon.pico.Application} during Pico bootstrap initialization, or it can
 * be produced during the same startup processing sequence if the Application was not found, or if it was not permitted to be
 * loaded.
 */
@Builder
public interface InjectionPlan extends BasicInjectionPlan {

    /**
     * The list of services/providers that are unqualified to satisfy the given injection point but were considered.
     *
     * @return the unqualified services/providers for this injection point
     */
    @Singular
    List<?> unqualifiedProviders();

    /**
     * Converts the inputs to an injection plans for the given service provider.
     *
     * @param picoServices          pico services
     * @param self                  the reference to the service provider associated with this plan
     * @param dependencies          the dependencies
     * @param resolveIps            flag indicating whether injection points should be resolved
     * @param logger                the logger to use for any logging
     * @return the injection plan per element identity belonging to the service provider
     */
    static Map<String, InjectionPlan> createInjectionPlans(
            PicoServices picoServices,
            ServiceProvider<?> self,
            DependenciesInfo dependencies,
            boolean resolveIps,
            System.Logger logger) {
        Map<String, InjectionPlan> result = new LinkedHashMap<>();
        if (dependencies.allDependencies().isEmpty()) {
            return result;
        }

        Services services = picoServices.services();
        PicoServicesConfig cfg = picoServices.config();
        boolean isPrivateSupported = cfg.supportsJsr330Privates();
        boolean isStaticSupported = cfg.supportsJsr330Statics();

        dependencies.allDependencies()
                .forEach((dep) -> {
                    ServiceInfoCriteria depTo = dep.dependencyTo();
                    final ServiceInfo selfInfo = self.serviceInfo();
                    if (selfInfo.declaredWeight().isPresent()
                            && selfInfo.contractsImplemented().containsAll(depTo.contractsImplemented())) {
                        // if we have a weight on ourselves, and we inject an interface that we actually offer, then
                        // be sure to use it to get lower weighted injection points ...
                        depTo = DefaultServiceInfoCriteria.toBuilder(depTo).weight(selfInfo.declaredWeight().get()).build();
                    }

                    if (self instanceof InjectionResolver) {
                        dep.injectionPointDependencies()
                                .stream()
                                .filter((ipInfo) ->
                                        (isPrivateSupported || ipInfo.access() != InjectionPointInfo.Access.PRIVATE)
                                                && (isStaticSupported || !ipInfo.staticDeclaration()))
                                .forEach((ipInfo) -> {
                                    String id = ipInfo.id();
                                    if (!result.containsKey(id)) {
                                        Object resolved = ((InjectionResolver) self)
                                                .resolve(ipInfo, picoServices, self, resolveIps)
                                                .orElse(null);
                                        if (resolved != null) {
                                            Object target = (resolved instanceof Optional)
                                                    ? ((Optional<?>) resolved).orElse(null) : resolved;
                                            InjectionPlan plan = DefaultInjectionPlan.builder()
                                                    .injectionPointInfo(ipInfo)
                                                    .injectionPointQualifiedServiceProviders(toIpQualified(target))
                                                    .unqualifiedProviders(toIpUnqualified(target))
                                                    .wasResolved(true)
                                                    .resolved(target)
                                                    .build();
                                            Object prev = result.put(id, plan);
                                            assert (Objects.isNull(prev)) : ipInfo;
                                        }
                                    }
                                });
                    }

                    List<ServiceProvider<?>> tmpServiceProviders = services.lookupAll(depTo, false);
                    if (tmpServiceProviders == null || tmpServiceProviders.isEmpty()) {
                        if (VoidServiceProvider.INSTANCE.serviceInfo().matches(depTo)) {
                            tmpServiceProviders = VoidServiceProvider.LIST_INSTANCE;
                        }
                    }

                    // filter down the selections to not include self...
                    final List<ServiceProvider<?>> serviceProviders =
                            (tmpServiceProviders != null && !tmpServiceProviders.isEmpty())
                                    ? tmpServiceProviders.stream()
                                        .filter((sp) -> !isSelf(self, sp))
                                        .collect(Collectors.toList())
                                    : tmpServiceProviders;

                    dep.injectionPointDependencies()
                        .stream()
                        .filter((ipInfo) ->
                                (isPrivateSupported || ipInfo.access() != InjectionPointInfo.Access.PRIVATE)
                                        && (isStaticSupported || !ipInfo.staticDeclaration()))
                        .forEach((ipInfo) -> {
                            String id = ipInfo.id();
                            if (!result.containsKey(id)) {
                                Object resolved = (resolveIps)
                                        ? resolve(self, ipInfo, serviceProviders, logger) : null;
                                if (!resolveIps && !ipInfo.optionalWrapped()
                                        && (serviceProviders == null || serviceProviders.isEmpty())
                                        && !allowNullableInjectionPoint(ipInfo)) {
                                    throw DefaultServices.resolutionBasedInjectionError(
                                                     ipInfo.dependencyToServiceInfo());
                                }
                                InjectionPlan plan = DefaultInjectionPlan.builder()
                                        .injectionPointInfo(ipInfo)
                                        .injectionPointQualifiedServiceProviders(serviceProviders)
                                        .serviceProvider(self)
                                        .wasResolved(resolveIps)
                                        .resolved((resolved instanceof Optional<?>
                                                && ((Optional<?>) resolved).isEmpty())
                                                          ? Optional.empty() : Optional.ofNullable(resolved))
                                        .build();
                                Object prev = result.put(id, plan);
                                assert (Objects.isNull(prev)) : ipInfo;
                            }
                    });
                }
        );

        return result;
    }

    private static List<ServiceProvider<?>> toIpQualified(
            Object target) {
        if (target instanceof Collection) {
            List<ServiceProvider<?>> result = new LinkedList<>();
            ((Collection<?>) target).stream()
                    .map(InjectionPlan::toIpQualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target instanceof AbstractServiceProvider)
                ? Collections.singletonList((ServiceProvider<?>) target)
                : Collections.emptyList();
    }

    private static List<?> toIpUnqualified(
            Object target) {
        if (target instanceof Collection) {
            List<Object> result = new LinkedList<>();
            ((Collection<?>) target).stream()
                    .map(InjectionPlan::toIpUnqualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target instanceof AbstractServiceProvider)
                ? Collections.emptyList()
                : Collections.singletonList(target);
    }

    static boolean isSelf(
            ServiceProvider<?> self,
            Object other) {
        assert (self != null);

        if (self == other) {
            return true;
        }

        if (self instanceof ServiceProviderBindable) {
            Object selfInterceptor = ((ServiceProviderBindable<?>) self).interceptor().orElse(null);

            if (other == selfInterceptor) {
                return true;
            }
        }

        return false;
    }

    static boolean allowNullableInjectionPoint(
            InjectionPointInfo ipInfo) {
        ServiceInfoCriteria missingServiceInfo = ipInfo.dependencyToServiceInfo();
        Set<String> contractsNeeded = missingServiceInfo.contractsImplemented();
        return (1 == contractsNeeded.size() && contractsNeeded.contains(Interceptor.class.getName()));
    }

    @SuppressWarnings("unchecked")
    static Object resolve(
            ServiceProvider<?> self,
            InjectionPointInfo ipInfo,
            List<ServiceProvider<?>> serviceProviders,
            System.Logger logger) {
        if (ipInfo.staticDeclaration()) {
            throw new InjectionException(ipInfo + ": static is not supported", null, self);
        }
        if (ipInfo.access() == InjectionPointInfo.Access.PRIVATE) {
            throw new InjectionException(ipInfo + ": private is not supported", null, self);
        }

        try {
            if (Void.class.getName().equals(ipInfo.serviceTypeName())) {
                return null;
            }

            if (ipInfo.listWrapped()) {
                if (ipInfo.optionalWrapped()) {
                    throw new InjectionException("Optional + List injection is not supported for "
                            + ipInfo.serviceTypeName() + "." + ipInfo.elementName());
                }

                if (serviceProviders.isEmpty()) {
                    if (!allowNullableInjectionPoint(ipInfo)) {
                        throw new InjectionException("expected to resolve a service appropriate for "
                                                             + ipInfo.serviceTypeName() + "." + ipInfo.elementName(),
                                                     DefaultServices
                                                             .resolutionBasedInjectionError(
                                                                     ipInfo.dependencyToServiceInfo()),
                                                     self);
                    } else {
                        return serviceProviders;
                    }
                }

                if (ipInfo.providerWrapped() && !ipInfo.optionalWrapped()) {
                    return serviceProviders;
                }

                if (ipInfo.listWrapped() && !ipInfo.optionalWrapped()) {
                    return toEligibleInjectionRefs(ipInfo, self, serviceProviders, true);
                }
            } else if (serviceProviders.isEmpty()) {
                if (ipInfo.optionalWrapped()) {
                    return Optional.empty();
                } else {
                    throw new InjectionException("expected to resolve a service appropriate for "
                            + ipInfo.serviceTypeName() + "." + ipInfo.elementName(),
                                 DefaultServices.resolutionBasedInjectionError(ipInfo.dependencyToServiceInfo()), self);
                }
            } else {
                ServiceProvider<?> serviceProvider = serviceProviders.get(0);
                Optional<ServiceProviderBindable<?>> serviceProviderBindable =
                        DefaultServiceBinder.toBindableProvider(DefaultServiceBinder.toRootProvider(serviceProvider));
                if (serviceProviderBindable.isPresent()
                        && serviceProviderBindable.get() != serviceProvider
                        && serviceProviderBindable.get() instanceof ServiceProviderProvider) {
                    serviceProvider = serviceProviderBindable.get();
                    serviceProviders = (List<ServiceProvider<?>>) ((ServiceProviderProvider) serviceProvider)
                            .serviceProviders(ipInfo.dependencyToServiceInfo(), true, false);
                    if (!serviceProviders.isEmpty()) {
                        serviceProvider = serviceProviders.get(0);
                    }
                }

                if (ipInfo.providerWrapped()) {
                    return ipInfo.optionalWrapped() ? Optional.of(serviceProvider) : serviceProvider;
                }

                if (ipInfo.optionalWrapped()) {
                    Optional<?> optVal;

                    try {
                        Optional<?> val = serviceProvider.first(ContextualServiceQuery.create(ipInfo, false));
                        optVal = Optional.ofNullable(val);
                    } catch (InjectionException e) {
                        logger.log(System.Logger.Level.WARNING, e.getMessage(), e);
                        optVal = Optional.empty();
                    }

                    return optVal;
                }

                ContextualServiceQuery query = ContextualServiceQuery.create(ipInfo, true);
                return serviceProvider.first(query);
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw expectedToResolveCriteria(ipInfo, t, self);
        }

        throw expectedToResolveCriteria(ipInfo, null, self);
    }

    @SuppressWarnings({"unchecked", "rawTypes"})
    private static List<?> toEligibleInjectionRefs(
            InjectionPointInfo ipInfo,
            ServiceProvider<?> self,
            List<ServiceProvider<?>> list,
            boolean expected) {
        List<?> result = new ArrayList<>();

        ContextualServiceQuery query = DefaultContextualServiceQuery.builder()
                .injectionPointInfo(ipInfo)
                .serviceInfoCriteria(ipInfo.dependencyToServiceInfo())
                .expected(expected);
        for (ServiceProvider<?> sp : list) {
            Collection instances = sp.list(query);
            result.addAll(instances);
        }

        if (expected && result.isEmpty()) {
            throw expectedToResolveCriteria(ipInfo, null, self);
        }

        return result;
    }

    private static InjectionException expectedToResolveCriteria(
            InjectionPointInfo ipInfo,
            Throwable cause,
            ServiceProvider<?> self) {
        String msg = (cause == null) ? "expected" : "failed";
        return new InjectionException(msg + " to resolve a service instance appropriate for '"
                                              + ipInfo.serviceTypeName() + "." + ipInfo.elementName()
                                              + "' with criteria = '" + ipInfo.dependencyToServiceInfo(),
                                      cause, self);
    }

}
