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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectRegistrySpi__ServiceDescriptor;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.InterceptionMetadata__ServiceDescriptor;
import io.helidon.service.inject.api.ProviderType;
import io.helidon.service.metadata.DescriptorMetadata;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.DescriptorHandler;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.VirtualDescriptor;

/**
 * Manager is responsible for managing the state of a {@link io.helidon.service.inject.api.InjectRegistry}.
 * Each manager instances owns a single service registry.
 * <p>
 * To use a singleton service across application, either pass it through parameters, or use
 * {@link io.helidon.service.registry.GlobalServiceRegistry}.
 */
public class InjectRegistryManager implements ServiceRegistryManager {
    static final Comparator<InjectServiceInfo> SERVICE_INFO_COMPARATOR = Comparator
            .comparingDouble(InjectServiceInfo::weight)
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
            .thenComparing(InjectServiceInfo::serviceType);
    private static final System.Logger LOGGER = System.getLogger(InjectRegistryManager.class.getName());
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final InjectConfig config;
    private final ServiceDiscovery discovery;

    private InjectServiceRegistryImpl registry;

    InjectRegistryManager(InjectConfig config, ServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.discovery = serviceDiscovery;
    }

    /**
     * Create a new inject registry manager with default configuration.
     *
     * @return a new inject registry manager
     */
    public static InjectRegistryManager create() {
        return create(InjectConfig.create());
    }

    /**
     * Create a new inject registry manager with custom configuration.
     *
     * @param config configuration to use
     * @return a new configured inject registry manager
     */
    public static InjectRegistryManager create(InjectConfig config) {
        // we provide the service, this
        return new InjectRegistryManager(config,
                                         config.discoverServices()
                                                 ? ServiceDiscovery.create()
                                                 : ServiceDiscovery.noop());
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public InjectRegistry registry() {
        Lock readLock = lifecycleLock.readLock();
        try {
            readLock.lock();
            if (registry != null) {
                return registry;
            }
        } finally {
            readLock.unlock();
        }

        Lock writeLock = lifecycleLock.writeLock();
        try {
            writeLock.lock();
            if (registry != null) {
                return registry;
            }
            // map of the service descriptor instance to Described (we need to have a single instance always used)
            Map<io.helidon.service.registry.ServiceInfo, Described> descriptorToDescribed = new IdentityHashMap<>();

            // scope handlers (scope type to service)
            Map<TypeName, InjectServiceInfo> scopeHandlers = new HashMap<>();
            // service descriptor singleton instance to its explicit value
            Map<io.helidon.service.registry.ServiceInfo, Object> explicitInstances = new IdentityHashMap<>();
            // implementation type to its manager
            Map<TypeName, InjectServiceInfo> servicesByType = new HashMap<>();
            // implemented contracts to their manager(s)
            Map<ResolvedType, Set<InjectServiceInfo>> servicesByContract = new HashMap<>();
            // map of qualifier type to a service info that can provide instances for it
            Map<TypeName, Set<InjectServiceInfo>> qualifiedProvidersByQualifier = new HashMap<>();
            // map of a qualifier type and contract to a service info
            Map<TypedQualifiedProviderKey, Set<InjectServiceInfo>> typedQualifiedProviders =
                    new HashMap<>();

            List<ServiceDescriptor<Binding>> bindings = new ArrayList<>();

            config.serviceInstances()
                    .forEach((desc, instance) -> {
                        var descriptor = desc;
                        if (descriptor instanceof VirtualDescriptor) {
                            // maybe we have a real descriptor for this type
                            descriptor = virtualDescriptor(config, discovery, descriptor);
                        }
                        Described described = toDescribed(descriptorToDescribed, descriptor);
                        bind(bindings,
                             scopeHandlers,
                             servicesByType,
                             servicesByContract,
                             qualifiedProvidersByQualifier,
                             typedQualifiedProviders,
                             described);
                        explicitInstances.putIfAbsent(descriptor, instance);
                    });

            for (var descriptor : config.serviceDescriptors()) {
                bind(bindings,
                     scopeHandlers,
                     servicesByType,
                     servicesByContract,
                     qualifiedProvidersByQualifier,
                     typedQualifiedProviders,
                     toDescribed(descriptorToDescribed, descriptor));
            }

            boolean logUnsupported = LOGGER.isLoggable(System.Logger.Level.TRACE);

            for (var descriptorMeta : discovery.allMetadata()) {
                String registryType = descriptorMeta.registryType();
                if (!(DescriptorMetadata.REGISTRY_TYPE_CORE.equals(registryType) || "inject".equals(registryType))) {
                    // we support only core and inject
                    if (logUnsupported) {
                        LOGGER.log(System.Logger.Level.TRACE,
                                   "Ignoring service of type \"" + descriptorMeta.registryType() + "\": " + descriptorMeta);
                    }
                    continue;
                }

                ServiceDescriptor<?> descriptor = descriptorMeta.descriptor();
                if (contains(descriptor.contracts(), Binding.TYPE)) {
                    bindings.add((ServiceDescriptor<Binding>) descriptor);
                    // applications are not bound to the registry
                } else {
                    Described described = toDescribed(descriptorToDescribed, descriptor);

                    bind(bindings,
                         scopeHandlers,
                         servicesByType,
                         servicesByContract,
                         qualifiedProvidersByQualifier,
                         typedQualifiedProviders,
                         described);
                }
            }

            // add service registry information (service registry cannot be overridden in any way)
            InjectServiceDescriptor<?> registrySpiDescriptor = InjectRegistrySpi__ServiceDescriptor.INSTANCE;
            Described registrySpiDescribed = new Described(registrySpiDescriptor, registrySpiDescriptor, false);
            descriptorToDescribed.put(registrySpiDescriptor, registrySpiDescribed);
            bind(bindings,
                 scopeHandlers,
                 servicesByType,
                 servicesByContract,
                 qualifiedProvidersByQualifier,
                 typedQualifiedProviders,
                 registrySpiDescribed);
            // add injection metadata information
            InjectServiceDescriptor<?> interceptDescriptor = InterceptionMetadata__ServiceDescriptor.INSTANCE;
            Described described = new Described(interceptDescriptor, interceptDescriptor, false);
            descriptorToDescribed.put(interceptDescriptor, described);
            bind(bindings,
                 scopeHandlers,
                 servicesByType,
                 servicesByContract,
                 qualifiedProvidersByQualifier,
                 typedQualifiedProviders,
                 described);

            registry = new InjectServiceRegistryImpl(config,
                                                     descriptorToDescribed,
                                                     scopeHandlers,
                                                     explicitInstances,
                                                     servicesByType,
                                                     servicesByContract,
                                                     qualifiedProvidersByQualifier,
                                                     typedQualifiedProviders);

            // now check if we have an application, and if so, apply it
            if (config.useBinding()) {
                for (ServiceDescriptor<Binding> binding : bindings) {
                    // applications cannot have dependencies
                    Binding bindingInstance = (Binding) binding.instantiate(DependencyContext.create(Map.of()));
                    bindingInstance.configure(new ApplicationPlanBinder(bindingInstance, registry));
                }
            }

            // and if application was not bound using binding(s), we need to create the bindings now
            registry.ensureInjectionPlans();

            return registry;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void shutdown() {
        Lock lock = lifecycleLock.writeLock();
        try {
            lock.lock();
            if (registry == null) {
                // registry was never requested,
                return;
            }

            registry.close();
            registry = null;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("rawtypes")
    private static ServiceDescriptor<?> virtualDescriptor(InjectConfig config,
                                                          ServiceDiscovery discovery,
                                                          ServiceDescriptor<?> descriptor) {
        TypeName serviceType = descriptor.serviceType();
        var fromConfig = config.serviceDescriptors()
                .stream()
                .filter(registered -> registered.serviceType().equals(serviceType))
                .findFirst();
        if (fromConfig.isPresent()) {
            return fromConfig.get();
        }

        return discovery.allMetadata()
                .stream()
                .filter(handler -> contains(handler.contracts(), serviceType))
                .map(DescriptorHandler::descriptor)
                .filter(desc -> desc.serviceType().equals(serviceType))
                .findFirst()
                .map(it -> (ServiceDescriptor) it)
                .orElse(descriptor);
    }

    private Described toDescribed(Map<io.helidon.service.registry.ServiceInfo, Described> descriptorToDescribed,
                                  ServiceDescriptor<?> descriptor) {
        return descriptorToDescribed.computeIfAbsent(descriptor, it -> {
            if (descriptor instanceof InjectServiceDescriptor<?> injectDescriptor) {
                return new Described(descriptor, injectDescriptor, false);
            } else {
                return new Described(descriptor, CoreWrappers.create(descriptor), true);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void bind(List<ServiceDescriptor<Binding>> bindings,
                      Map<TypeName, InjectServiceInfo> scopeHandlers,
                      Map<TypeName, InjectServiceInfo> servicesByType,
                      Map<ResolvedType, Set<InjectServiceInfo>> servicesByContract,
                      Map<TypeName, Set<InjectServiceInfo>> qualifiedProvidersByQualifier,
                      Map<TypedQualifiedProviderKey, Set<InjectServiceInfo>> typedQualifiedProviders,
                      Described described) {

        InjectServiceDescriptor<?> descriptor = described.injectDescriptor();

        if (contains(descriptor.contracts(), Binding.TYPE)) {
            bindings.add((ServiceDescriptor<Binding>) described.coreDescriptor());
            // application is not bound to the registry
            return;
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            if (descriptor.coreInfo() instanceof ServiceLoader__ServiceDescriptor sl) {
                LOGGER.log(System.Logger.Level.TRACE,
                           "Binding service loader descriptor: " + sl + ")");
            } else {
                LOGGER.log(System.Logger.Level.TRACE, "Binding service descriptor: " + descriptor.descriptorType()
                        .fqName());
            }
        }

        // by service type
        servicesByType.putIfAbsent(descriptor.serviceType(), descriptor);
        // service type is a contract as well (to make lookup easier)
        servicesByContract.computeIfAbsent(ResolvedType.create(descriptor.serviceType()),
                                           it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                .add(descriptor);

        Set<ResolvedType> contracts = descriptor.contracts();
        // by contract
        for (ResolvedType contract : contracts) {
            servicesByContract.computeIfAbsent(contract,
                                               it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                    .add(descriptor);
        }

        // scope handlers have a very specific meaning
        if (contains(contracts, Injection.ScopeHandler.TYPE)) {
            if (!Injection.Singleton.TYPE.equals(descriptor.scope())) {
                throw new ServiceRegistryException("Services that provide ScopeHandler contract MUST be in Singleton scope, but "
                                                           + descriptor.serviceType().fqName() + " is in "
                                                           + descriptor.scope().fqName() + " scope.");
            }
            if (descriptor instanceof GeneratedInjectService.ScopeHandlerDescriptor shd) {
                scopeHandlers.putIfAbsent(shd.handledScope(), descriptor);
            } else {
                throw new ServiceRegistryException("Service descriptors of services that implement ScopeHandler MUST"
                                                           + " implement ScopeHandlerDescriptor. Service "
                                                           + descriptor.descriptorType().fqName() + " does not.");
            }
        }

        if (descriptor.providerType() == ProviderType.QUALIFIED_PROVIDER) {
            // a special kind of service that matches ANY qualifier instance of a specific type, and also may match
            // a specific contract, or ANY contract
            if (descriptor instanceof GeneratedInjectService.QualifiedProviderDescriptor qpd) {
                TypeName qualifierType = qpd.qualifierType();
                if (contains(contracts, TypeNames.OBJECT)) {
                    // matches any contract
                    qualifiedProvidersByQualifier.computeIfAbsent(qualifierType, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                            .add(descriptor);
                } else {
                    // contract specific
                    Set<TypeName> realContracts = contracts.stream()
                            .filter(Predicate.not(Injection.QualifiedProvider.TYPE::equals))
                            .collect(Collectors.toSet());
                    for (TypeName realContract : realContracts) {
                        TypedQualifiedProviderKey key = new TypedQualifiedProviderKey(qualifierType,
                                                                                      ResolvedType.create(realContract));
                        typedQualifiedProviders.computeIfAbsent(key, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                                .add(descriptor);
                    }
                }
            } else {
                throw new ServiceRegistryException("Service descriptors of services that implement QualifiedProvider MUST"
                                                           + " implement QualifiedProviderDescriptor. Service "
                                                           + descriptor.descriptorType().fqName() + " does not.");
            }
        }
    }

    private static boolean contains(Set<ResolvedType> contracts, TypeName type) {
        return contracts.stream().anyMatch(it -> it.genericTypeName().equals(type));
    }

    record TypedQualifiedProviderKey(TypeName qualifier, ResolvedType contract) {
    }

    /**
     * @param coreDescriptor   instance that is generated and used to register injection points etc.
     * @param injectDescriptor instance that satisfies the inject descriptor API
     * @param isCore           if false, both are the same instance
     */
    record Described(ServiceDescriptor<?> coreDescriptor,
                     InjectServiceDescriptor<?> injectDescriptor,
                     boolean isCore) {
    }

    private static class ApplicationPlanBinder implements InjectionPlanBinder {
        private static final System.Logger LOGGER = System.getLogger(ApplicationPlanBinder.class.getName());

        private final Binding appInstance;
        private final InjectServiceRegistryImpl registry;

        private ApplicationPlanBinder(Binding appInstance, InjectServiceRegistryImpl registry) {
            this.appInstance = appInstance;
            this.registry = registry;
        }

        @Override
        public Binder bindTo(ServiceInfo descriptor) {
            ServiceManager<Object> serviceManager = registry.serviceManager(descriptor);

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "binding injection plan to " + serviceManager);
            }

            return serviceManager.servicePlanBinder();
        }

        @Override
        public void interceptors(ServiceInfo... descriptors) {
            registry.interceptors(descriptors);
        }

        @Override
        public String toString() {
            return "Service binder for application: " + appInstance.name();
        }
    }
}
