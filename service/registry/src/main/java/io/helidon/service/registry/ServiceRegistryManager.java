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

package io.helidon.service.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.metadata.DescriptorMetadata;

/**
 * Manager is responsible for managing the state of a {@link io.helidon.service.registry.ServiceRegistry}.
 * Each manager instances owns a single service registry.
 * <p>
 * To use a singleton service across application, either pass it through parameters, or use
 * {@link io.helidon.service.registry.GlobalServiceRegistry}.
 */
public final class ServiceRegistryManager {
    static final Comparator<ServiceInfo> SERVICE_INFO_COMPARATOR = Comparator
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
    private static final System.Logger LOGGER = System.getLogger(ServiceRegistryManager.class.getName());
    private static final InterceptionMetadata NO_OP_INTERCEPT_META = new NoOpInterceptMeta();

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final ServiceRegistryConfig config;
    private final ServiceDiscovery discovery;

    private CoreServiceRegistry registry;

    ServiceRegistryManager(ServiceRegistryConfig config, ServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.discovery = serviceDiscovery;
    }

    /**
     * Create a new service registry manager with default configuration.
     *
     * @return a new service registry manager
     */
    public static ServiceRegistryManager create() {
        return create(ServiceRegistryConfig.create());
    }

    /**
     * Create a new service registry manager with custom configuration.
     *
     * @param config configuration of this registry manager
     * @return a new configured service registry manager
     */
    public static ServiceRegistryManager create(ServiceRegistryConfig config) {
        ServiceDiscovery discovery = config.discoverServices() || config.discoverServicesFromServiceLoader()
                ? CoreServiceDiscovery.create(config)
                : CoreServiceDiscovery.noop();

        return new ServiceRegistryManager(config, discovery);
    }

    /**
     * Get (or initialize and get) the service registry managed by this manager.
     *
     * @return service registry ready to be used
     */
    @SuppressWarnings("checkstyle:methodLength") // already condensed; further extraction would decrease readability
    public ServiceRegistry registry() {
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
            // all descriptors
            Set<ServiceDescriptor<?>> descriptors = Collections.newSetFromMap(new IdentityHashMap<>());
            // scope handlers (scope type to service)
            Map<TypeName, ServiceInfo> scopeHandlers = new HashMap<>();
            // service descriptor singleton instance to its explicit value
            Map<io.helidon.service.registry.ServiceInfo, Object> explicitInstances = new IdentityHashMap<>();
            // implementation type to its manager
            Map<TypeName, ServiceInfo> servicesByType = new HashMap<>();
            // implemented contracts to their manager(s)
            Map<ResolvedType, Set<ServiceInfo>> servicesByContract = new HashMap<>();
            // map of qualifier type to a service info that can provide instances for it
            Map<TypeName, Set<ServiceInfo>> qualifiedProvidersByQualifier = new HashMap<>();
            // map of a qualifier type and contract to a service info
            Map<TypedQualifiedProviderKey, Set<ServiceInfo>> typedQualifiedProviders =
                    new HashMap<>();

            List<ServiceDescriptor<Binding>> bindings = new ArrayList<>();

            config.serviceInstances()
                    .forEach((desc, instance) -> {
                        var descriptor = desc;
                        if (descriptor instanceof VirtualDescriptor) {
                            // maybe we have a real descriptor for this type
                            descriptor = virtualDescriptor(config, discovery, descriptor);
                        }

                        descriptors.add(descriptor);
                        bind(bindings,
                             scopeHandlers,
                             servicesByType,
                             servicesByContract,
                             qualifiedProvidersByQualifier,
                             typedQualifiedProviders,
                             descriptor);
                        explicitInstances.putIfAbsent(descriptor, instance);
                    });

            for (var descriptor : config.serviceDescriptors()) {
                descriptors.add(descriptor);
                bind(bindings,
                     scopeHandlers,
                     servicesByType,
                     servicesByContract,
                     qualifiedProvidersByQualifier,
                     typedQualifiedProviders,
                     descriptor);
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
                    descriptors.add(descriptor);
                    bind(bindings,
                         scopeHandlers,
                         servicesByType,
                         servicesByContract,
                         qualifiedProvidersByQualifier,
                         typedQualifiedProviders,
                         descriptor);
                }
            }

            // add service registry information (service registry cannot be overridden in any way)
            ServiceDescriptor<?> scopesDescriptor = Scopes__ServiceDescriptor.INSTANCE;
            ServiceDescriptor<?> registryDescriptor = ServiceRegistry__ServiceDescriptor.INSTANCE;

            descriptors.add(scopesDescriptor);
            descriptors.add(registryDescriptor);

            bind(bindings,
                 scopeHandlers,
                 servicesByType,
                 servicesByContract,
                 qualifiedProvidersByQualifier,
                 typedQualifiedProviders,
                 registryDescriptor);
            // add injection metadata information
            ServiceDescriptor<?> interceptDescriptor = InterceptionMetadata__ServiceDescriptor.INSTANCE;
            descriptors.add(interceptDescriptor);

            bind(bindings,
                 scopeHandlers,
                 servicesByType,
                 servicesByContract,
                 qualifiedProvidersByQualifier,
                 typedQualifiedProviders,
                 interceptDescriptor);

            Map<ResolvedType, AtomicBoolean> accessedContracts = new HashMap<>();

            // we may create a few more atomic booleans than strictly necessary,
            // but cheaper than using putIfAbsent
            descriptors.stream()
                    .forEach(descriptor -> {
                        accessedContracts.put(ResolvedType.create(descriptor.serviceType()), new AtomicBoolean());
                        descriptor.contracts()
                                .forEach(contract -> accessedContracts.put(contract, new AtomicBoolean()));
                    });

            registry = new CoreServiceRegistry(config,
                                               descriptors,
                                               scopeHandlers,
                                               explicitInstances,
                                               servicesByType,
                                               servicesByContract,
                                               qualifiedProvidersByQualifier,
                                               typedQualifiedProviders,
                                               accessedContracts);

            // now check if we have an application, and if so, apply it
            if (config.useBinding()) {
                for (ServiceDescriptor<Binding> binding : bindings) {
                    // applications cannot have dependencies
                    Binding bindingInstance = (Binding) binding.instantiate(DependencyContext.create(Map.of()),
                                                                            NO_OP_INTERCEPT_META);
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

    /**
     * Shutdown the managed service registry.
     */
    public void shutdown() {
        Lock lock = lifecycleLock.writeLock();
        try {
            lock.lock();
            if (registry == null) {
                // registry was never requested,
                return;
            }

            registry.shutdown();
            registry = null;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("rawtypes")
    private static ServiceDescriptor<?> virtualDescriptor(ServiceRegistryConfig config,
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

    private static boolean contains(Set<ResolvedType> contracts, TypeName type) {
        return contracts.stream().anyMatch(it -> it.type().equals(type));
    }

    @SuppressWarnings("unchecked")
    private void bind(List<ServiceDescriptor<Binding>> bindings,
                      Map<TypeName, ServiceInfo> scopeHandlers,
                      Map<TypeName, ServiceInfo> servicesByType,
                      Map<ResolvedType, Set<ServiceInfo>> servicesByContract,
                      Map<TypeName, Set<ServiceInfo>> qualifiedProvidersByQualifier,
                      Map<TypedQualifiedProviderKey, Set<ServiceInfo>> typedQualifiedProviders,
                      ServiceDescriptor<?> descriptor) {

        if (contains(descriptor.contracts(), Binding.TYPE)) {
            bindings.add((ServiceDescriptor<Binding>) descriptor);
            // application is not bound to the registry
            return;
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            if (descriptor instanceof ServiceLoader__ServiceDescriptor sl) {
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
        if (contains(contracts, Service.ScopeHandler.TYPE)) {
            if (!Service.Singleton.TYPE.equals(descriptor.scope())) {
                throw new ServiceRegistryException("Services that provide ScopeHandler contract MUST be in Singleton scope, but "
                                                           + descriptor.serviceType().fqName() + " is in "
                                                           + descriptor.scope().fqName() + " scope.");
            }
            if (descriptor instanceof GeneratedService.ScopeHandlerDescriptor shd) {
                scopeHandlers.putIfAbsent(shd.handledScope(), descriptor);
            } else {
                throw new ServiceRegistryException("Service descriptors of services that implement ScopeHandler MUST"
                                                           + " implement ScopeHandlerDescriptor. Service "
                                                           + descriptor.descriptorType().fqName() + " does not.");
            }
        }

        if (descriptor.factoryType() == FactoryType.QUALIFIED) {
            // a special kind of service that matches ANY qualifier instance of a specific type, and also may match
            // a specific contract, or ANY contract
            if (descriptor instanceof GeneratedService.QualifiedFactoryDescriptor qpd) {
                TypeName qualifierType = qpd.qualifierType();
                if (contains(contracts, TypeNames.OBJECT)) {
                    // matches any contract
                    qualifiedProvidersByQualifier.computeIfAbsent(qualifierType, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                            .add(descriptor);
                } else {
                    // contract specific
                    Set<TypeName> realContracts = contracts.stream()
                            .map(ResolvedType::type)
                            .filter(Predicate.not(Service.QualifiedFactory.TYPE::equals))
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

        // and bind factory types, as we can also lookup factories, but only types that are not provided by contracts
        // as otherwise we would return a factory instance instead of constructed instance
        // there are cases where the structure can "trick" us into thinking something is a factory contract, such as
        // when an actual contract of the factory extends a Supplier that provides the same type as the factory itself
        var factoryContracts = descriptor.factoryContracts();
        for (ResolvedType factoryContract : factoryContracts) {
            if (!contracts.contains(factoryContract)) {
                servicesByContract.computeIfAbsent(factoryContract,
                                                   it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                        .add(descriptor);
            }
        }
    }

    record TypedQualifiedProviderKey(TypeName qualifier, ResolvedType contract) {
    }

    private static class ApplicationPlanBinder implements DependencyPlanBinder {
        private static final System.Logger LOGGER = System.getLogger(ApplicationPlanBinder.class.getName());

        private final Binding appInstance;
        private final CoreServiceRegistry registry;

        private ApplicationPlanBinder(Binding appInstance, CoreServiceRegistry registry) {
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

    private static class NoOpInterceptMeta implements InterceptionMetadata {
        @Override
        public <T> InterceptionInvoker<T> createInvoker(ServiceInfo descriptor,
                                                        Set<Qualifier> typeQualifiers,
                                                        List<Annotation> typeAnnotations,
                                                        TypedElementInfo element,
                                                        InterceptionInvoker<T> targetInvoker,
                                                        Set<Class<? extends Throwable>> checkedExceptions) {
            return targetInvoker;
        }

        @Override
        public <T> InterceptionInvoker<T> createInvoker(Object serviceInstance,
                                                        ServiceInfo descriptor,
                                                        Set<Qualifier> typeQualifiers,
                                                        List<Annotation> typeAnnotations,
                                                        TypedElementInfo element,
                                                        InterceptionInvoker<T> targetInvoker,
                                                        Set<Class<? extends Throwable>> checkedExceptions) {
            return targetInvoker;
        }
    }
}
