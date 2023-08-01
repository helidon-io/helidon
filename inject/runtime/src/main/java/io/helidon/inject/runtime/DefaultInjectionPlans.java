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

package io.helidon.inject.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.AccessModifier;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.DependencyInfo;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.InjectionServiceProviderException;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.InjectionServicesConfig;
import io.helidon.inject.api.Interceptor;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.ServiceProviderBindable;
import io.helidon.inject.api.ServiceProviderInjectionException;
import io.helidon.inject.api.ServiceProviderProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.spi.InjectionResolver;

class DefaultInjectionPlans {
    private static final TypeName INTERCEPTOR = TypeName.create(Interceptor.class);
    private static final TypeName IP_PROVIDER = TypeName.create(InjectionPointProvider.class);
    private static final TypeName VOID = TypeName.create(Void.class);

    private DefaultInjectionPlans() {
    }

    /**
     * Converts the inputs to an injection plans for the given service provider.
     *
     * @param injectionServices injection services
     * @param self         the reference to the service provider associated with this plan
     * @param dependencies the dependencies
     * @param resolveIps   flag indicating whether injection points should be resolved
     * @param logger            the logger to use for any logging
     * @return the injection plan per element identity belonging to the service provider
     */
    static Map<String, HelidonInjectionPlan> createInjectionPlans(InjectionServices injectionServices,
                                                                  ServiceProvider<?> self,
                                                                  DependenciesInfo dependencies,
                                                                  boolean resolveIps,
                                                                  System.Logger logger) {
        Map<String, HelidonInjectionPlan> result = new LinkedHashMap<>();
        if (dependencies.allDependencies().isEmpty()) {
            return result;
        }

        dependencies.allDependencies()
                .forEach(dep -> {
                    try {
                        accumulate(dep, result, injectionServices, self, resolveIps, logger);
                    } catch (Exception e) {
                        throw new InjectionServiceProviderException("An error occurred creating the injection plan", e, self);
                    }
                });

        return result;
    }

    /**
     * Special case where we have qualifiers on the criteria, but we should still allow any
     * {@link InjectionPointProvider} match regardless, since it will be the ultimate determining agent
     * to use the dependency info - not the services registry default logic.
     *
     * @param services the services registry
     * @param depTo    the dependency with the qualifiers
     * @return a list of {@link InjectionPointProvider}s that can handle the contracts requested
     */
    static List<ServiceProvider<?>> injectionPointProvidersFor(Services services,
                                                               ServiceInfoCriteria depTo) {
        if (depTo.qualifiers().isEmpty()) {
            return List.of();
        }

        if (depTo.contractsImplemented().isEmpty()) {
            return List.of();
        }

        ServiceInfoCriteria modifiedDepTo = ServiceInfoCriteria.builder(depTo)
                .qualifiers(Set.of())
                .build();

        List<ServiceProvider<?>> providers = services.lookupAll(modifiedDepTo);
        return providers.stream()
                .filter(it -> it.serviceInfo().contractsImplemented().contains(IP_PROVIDER))
                .toList();
    }

    /**
     * Creates and maybe adjusts the criteria to match the context of who is doing the lookup.
     *
     * @param dep       the dependency info to lookup
     * @param self      the service doing the lookup
     * @param selfInfo  the service info for the service doing the lookup
     * @return the criteria
     */
    static ServiceInfoCriteria toCriteria(DependencyInfo dep,
                                          ServiceProvider<?> self,
                                          ServiceInfo selfInfo) {
        ServiceInfoCriteria criteria = dep.dependencyTo();
        ServiceInfoCriteria.Builder builder = null;
        if (selfInfo.declaredWeight().isPresent()
                && selfInfo.contractsImplemented().containsAll(criteria.contractsImplemented())) {
            // if we have a weight on ourselves, and we inject an interface that we actually offer, then
            // be sure to use it to get lower weighted injection points
            builder = ServiceInfoCriteria.builder(criteria)
                    .weight(selfInfo.declaredWeight().get());
        }

        if ((self instanceof ServiceProviderBindable) && ((ServiceProviderBindable<?>) self).isInterceptor()) {
            if (builder == null) {
                builder = ServiceInfoCriteria.builder(criteria);
            }
            builder = builder.includeIntercepted(true);
        }

        return (builder != null) ? builder.build() : criteria;
    }

    /**
     * Resolution comes after the plan was loaded or created.
     *
     * @param self              the reference to the service provider associated with this plan
     * @param ipInfo            the injection point
     * @param serviceProviders  the service providers that qualify in preferred weighted order
     * @param logger            the logger to use for any logging
     * @return the resolution (and activation) of the qualified service provider(s) in the form acceptable to the injection point
     */
    @SuppressWarnings("unchecked")
    static Object resolve(ServiceProvider<?> self,
                          InjectionPointInfo ipInfo,
                          List<ServiceProvider<?>> serviceProviders,
                          System.Logger logger) {
        if (ipInfo.staticDeclaration()) {
            throw new ServiceProviderInjectionException(ipInfo + ": static is not supported", null, self);
        }
        if (ipInfo.access() == AccessModifier.PRIVATE) {
            throw new ServiceProviderInjectionException(ipInfo + ": private is not supported", null, self);
        }

        try {
            if (VOID.equals(ipInfo.serviceTypeName())) {
                return null;
            }

            if (ipInfo.listWrapped()) {
                if (ipInfo.optionalWrapped()) {
                    throw new ServiceProviderInjectionException("Optional + List injection is not supported for "
                                                         + ipInfo.serviceTypeName() + "." + ipInfo.elementName());
                }

                if (serviceProviders.isEmpty()) {
                    if (!allowNullableInjectionPoint(ipInfo)) {
                        throw new ServiceProviderInjectionException("Expected to resolve a service appropriate for "
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
                    throw new ServiceProviderInjectionException("Expected to resolve a service appropriate for "
                                                         + ipInfo.serviceTypeName() + "." + ipInfo.elementName(),
                                                DefaultServices.resolutionBasedInjectionError(ipInfo.dependencyToServiceInfo()),
                                                self);
                }
            } else {
                // "standard" case
                ServiceProvider<?> serviceProvider = serviceProviders.get(0);
                Optional<ServiceProviderBindable<?>> serviceProviderBindable =
                        ServiceBinderDefault.toBindableProvider(ServiceBinderDefault.toRootProvider(serviceProvider));
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
                        optVal = Objects.requireNonNull(
                                serviceProvider.first(ContextualServiceQuery.create(ipInfo, false)));
                    } catch (ServiceProviderInjectionException e) {
                        logger.log(System.Logger.Level.WARNING, e.getMessage(), e);
                        optVal = Optional.empty();
                    }
                    return optVal;
                }

                ContextualServiceQuery query = ContextualServiceQuery.create(ipInfo, true);
                Optional<?> first = serviceProvider.first(query);
                return first.orElse(null);
            }
        } catch (ServiceProviderInjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw expectedToResolveCriteria(ipInfo, t, self);
        }

        throw expectedToResolveCriteria(ipInfo, null, self);
    }

    @SuppressWarnings("unchecked")
    private static void accumulate(DependencyInfo dep,
                                   Map<String, HelidonInjectionPlan> result,
                                   InjectionServices injectionServices,
                                   ServiceProvider<?> self,
                                   boolean resolveIps,
                                   System.Logger logger) {
        ServiceInfo selfInfo = self.serviceInfo();
        ServiceInfoCriteria depTo = toCriteria(dep, self, selfInfo);
        Services services = injectionServices.services();
        InjectionServicesConfig cfg = injectionServices.config();
        boolean isPrivateSupported = cfg.supportsJsr330Privates();
        boolean isStaticSupported = cfg.supportsJsr330Statics();

        if (self instanceof InjectionResolver) {
            dep.injectionPointDependencies()
                    .stream()
                    .filter(ipInfo -> (isPrivateSupported || ipInfo.access() != AccessModifier.PRIVATE)
                            && (isStaticSupported || !ipInfo.staticDeclaration()))
                    .forEach(ipInfo -> {
                        String id = ipInfo.id();
                        if (!result.containsKey(id)) {
                            Object resolved = ((InjectionResolver) self)
                                    .resolve(ipInfo, injectionServices, self, resolveIps)
                                    .orElse(null);
                            Object target = (resolved instanceof Optional)
                                    ? ((Optional<?>) resolved).orElse(null) : resolved;
                            if (target != null) {
                                HelidonInjectionPlan.Builder planBuilder = HelidonInjectionPlan.builder()
                                        .serviceProvider(self)
                                        .injectionPointInfo(ipInfo)
                                        .injectionPointQualifiedServiceProviders(toIpQualified(target))
                                        .unqualifiedProviders(toIpUnqualified(target))
                                        .wasResolved(resolved != null);

                                if (ipInfo.optionalWrapped()) {
                                    planBuilder.resolved((target instanceof Optional && ((Optional<?>) target).isEmpty())
                                                                 ? Optional.empty() : Optional.of(target));
                                } else {
                                    if (target instanceof Optional) {
                                        target = ((Optional<Object>) target).orElse(null);
                                    }
                                    if (target != null) {
                                        planBuilder.resolved(target);
                                    }
                                }

                                HelidonInjectionPlan plan = planBuilder.build();
                                Object prev = result.put(id, plan);
                                assert (prev == null) : ipInfo;
                            }
                        }
                    });
        }

        List<ServiceProvider<?>> tmpServiceProviders = services.lookupAll(depTo, false);
        if (tmpServiceProviders.isEmpty()) {
            if (VoidServiceProvider.INSTANCE.serviceInfo().matches(depTo)) {
                tmpServiceProviders = VoidServiceProvider.LIST_INSTANCE;
            } else {
                tmpServiceProviders = injectionPointProvidersFor(services, depTo);
            }
        }

        // filter down the selections to not include self
        List<ServiceProvider<?>> serviceProviders =
                (!tmpServiceProviders.isEmpty())
                        ? tmpServiceProviders.stream()
                        .filter(sp -> !isSelf(self, sp))
                        .collect(Collectors.toList())
                        : tmpServiceProviders;

        dep.injectionPointDependencies()
                .stream()
                .filter(ipInfo ->
                                (isPrivateSupported || ipInfo.access() != AccessModifier.PRIVATE)
                                        && (isStaticSupported || !ipInfo.staticDeclaration()))
                .forEach(ipInfo -> {
                    String id = ipInfo.id();
                    if (!result.containsKey(id)) {
                        Object resolved = (resolveIps)
                                ? resolve(self, ipInfo, serviceProviders, logger) : null;
                        if (!resolveIps && !ipInfo.optionalWrapped()
                                && serviceProviders.isEmpty()
                                && !allowNullableInjectionPoint(ipInfo)) {
                            throw DefaultServices.resolutionBasedInjectionError(
                                    ipInfo.dependencyToServiceInfo());
                        }
                        HelidonInjectionPlan plan = HelidonInjectionPlan.builder()
                                .injectionPointInfo(ipInfo)
                                .injectionPointQualifiedServiceProviders(serviceProviders)
                                .serviceProvider(self)
                                .wasResolved(resolveIps)
                                .update(builder -> {
                                    if (resolved instanceof Optional<?> opt) {
                                        opt.ifPresent(builder::resolved);
                                    } else {
                                        if (resolved != null) {
                                            builder.resolved(resolved);
                                        }
                                    }
                                })
                                .build();
                        Object prev = result.put(id, plan);
                        assert (prev == null) : ipInfo;
                    }
                });
    }

    private static List<ServiceProvider<?>> toIpQualified(Object target) {
        if (target instanceof Collection) {
            List<ServiceProvider<?>> result = new ArrayList<>();
            ((Collection<?>) target).stream()
                    .map(DefaultInjectionPlans::toIpQualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target instanceof AbstractServiceProvider)
                ? List.of((ServiceProvider<?>) target)
                : List.of();
    }

    private static List<?> toIpUnqualified(Object target) {
        if (target instanceof Collection) {
            List<Object> result = new ArrayList<>();
            ((Collection<?>) target).stream()
                    .map(DefaultInjectionPlans::toIpUnqualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target == null || target instanceof AbstractServiceProvider)
                ? List.of()
                : List.of(target);
    }

    private static boolean isSelf(ServiceProvider<?> self,
                                  Object other) {
        assert (self != null);

        if (self == other) {
            return true;
        }

        if (self instanceof ServiceProviderBindable) {
            Object selfInterceptor = ((ServiceProviderBindable<?>) self).interceptor().orElse(null);

            return other == selfInterceptor;
        }

        return false;
    }

    private static boolean allowNullableInjectionPoint(InjectionPointInfo ipInfo) {
        if (ipInfo.listWrapped()) {
            // allow empty lists to be injected
            return true;
        }

        ServiceInfoCriteria missingServiceInfo = ipInfo.dependencyToServiceInfo();
        Set<TypeName> contractsNeeded = missingServiceInfo.contractsImplemented();
        return (1 == contractsNeeded.size() && contractsNeeded.contains(INTERCEPTOR));
    }

    @SuppressWarnings({"unchecked", "rawTypes"})
    private static List<?> toEligibleInjectionRefs(InjectionPointInfo ipInfo,
                                                   ServiceProvider<?> self,
                                                   List<ServiceProvider<?>> list,
                                                   boolean expected) {
        List<?> result = new ArrayList<>();

        ContextualServiceQuery query = ContextualServiceQuery.builder()
                .injectionPointInfo(ipInfo)
                .serviceInfoCriteria(ipInfo.dependencyToServiceInfo())
                .expected(expected)
                .build();
        for (ServiceProvider<?> sp : list) {
            Collection instances = sp.list(query);
            result.addAll(instances);
        }

        if (expected && result.isEmpty()) {
            throw expectedToResolveCriteria(ipInfo, null, self);
        }

        return result;
    }

    private static ServiceProviderInjectionException expectedToResolveCriteria(InjectionPointInfo ipInfo,
                                                                               Throwable cause,
                                                                               ServiceProvider<?> self) {
        String msg = (cause == null) ? "Expected" : "Failed";
        return new ServiceProviderInjectionException(msg + " to resolve a service instance appropriate for '"
                                              + ipInfo.serviceTypeName() + "." + ipInfo.elementName()
                                              + "' with criteria = '" + ipInfo.dependencyToServiceInfo(),
                                                     cause, self);
    }

}
