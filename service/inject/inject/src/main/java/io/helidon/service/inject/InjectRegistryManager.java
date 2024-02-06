package io.helidon.service.inject;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.ServiceRegistry__ServiceDescriptor;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.DescriptorMetadata;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;

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

    public static InjectRegistryManager create() {
        return create(InjectConfig.create());
    }

    public static InjectRegistryManager create(InjectConfig config) {
        // we provide the service, this
        return new InjectRegistryManager(config,
                                         config.discoverServices()
                                                 ? ServiceDiscovery.instance()
                                                 : ServiceDiscovery.noop());
    }

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
            Map<TypeName, Set<InjectServiceInfo>> servicesByContract = new HashMap<>();
            // map of qualifier type to a service info that can provide instances for it
            Map<TypeName, Set<InjectServiceInfo>> qualifiedProvidersByQualifier = new HashMap<>();
            // map of a qualifier type and contract to a service info
            Map<TypedQualifiedProviderKey, Set<InjectServiceInfo>> typedQualifiedProviders =
                    new HashMap<>();

            config.serviceInstances()
                    .forEach((descriptor, instance) -> {
                        Described described = toDescribed(descriptorToDescribed, descriptor);
                        bind(scopeHandlers,
                             servicesByType,
                             servicesByContract,
                             qualifiedProvidersByQualifier,
                             typedQualifiedProviders,
                             described);
                        explicitInstances.putIfAbsent(descriptor, instance);
                    });

            for (var descriptor : config.serviceDescriptors()) {
                bind(scopeHandlers,
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

                GeneratedService.Descriptor<?> descriptor = descriptorMeta.descriptor();
                Described described = toDescribed(descriptorToDescribed, descriptor);

                bind(scopeHandlers,
                     servicesByType,
                     servicesByContract,
                     qualifiedProvidersByQualifier,
                     typedQualifiedProviders,
                     described);
            }

            // and as the last step, add service registry information (service registry cannot be overridden in any way)
            Descriptor<?> myDescriptor = ServiceRegistry__ServiceDescriptor.INSTANCE;
            Described described = new Described(myDescriptor, myDescriptor, false);
            descriptorToDescribed.put(myDescriptor, described);
            bind(scopeHandlers,
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

    private Described toDescribed(Map<io.helidon.service.registry.ServiceInfo, Described> descriptorToDescribed,
                                  GeneratedService.Descriptor<?> descriptor) {
        return descriptorToDescribed.computeIfAbsent(descriptor, it -> {
            if (descriptor instanceof Descriptor<?> injectDescriptor) {
                return new Described(descriptor, injectDescriptor, false);
            } else {
                return new Described(descriptor, new CoreDescriptorWrapper(descriptor), true);
            }
        });
    }

    private void bind(Map<TypeName, InjectServiceInfo> scopeHandlers,
                      Map<TypeName, InjectServiceInfo> servicesByType,
                      Map<TypeName, Set<InjectServiceInfo>> servicesByContract,
                      Map<TypeName, Set<InjectServiceInfo>> qualifiedProvidersByQualifier,
                      Map<TypedQualifiedProviderKey, Set<InjectServiceInfo>> typedQualifiedProviders,
                      Described described) {

        Descriptor<?> descriptor = described.injectDescriptor();
        GeneratedService.Descriptor<?> coreDescriptor = described.coreDescriptor();
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Binding service descriptor: " + descriptor.descriptorType()
                    .fqName());
        }

        // by service type
        servicesByType.putIfAbsent(descriptor.serviceType(), descriptor);
        // service type is a contract as well (to make lookup easier)
        servicesByContract.computeIfAbsent(descriptor.serviceType(), it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                .add(descriptor);

        Set<TypeName> contracts = descriptor.contracts();
        // by contract
        for (TypeName contract : contracts) {
            servicesByContract.computeIfAbsent(contract, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                    .add(descriptor);
        }

        // scope handlers have a very specific meaning
        if (contracts.contains(Injection.ScopeHandler.TYPE_NAME)) {
            if (!Injection.Singleton.TYPE_NAME.equals(descriptor.scope())) {
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

        if (contracts.contains(Injection.QualifiedProvider.TYPE_NAME)) {
            // a special kind of service that matches ANY qualifier instance of a specific type, and also may match
            // a specific contract, or ANY contract
            if (descriptor instanceof GeneratedInjectService.QualifiedProviderDescriptor qpd) {
                TypeName qualifierType = qpd.qualifierType();
                if (contracts.contains(TypeNames.OBJECT)) {
                    // matches any contract
                    qualifiedProvidersByQualifier.computeIfAbsent(qualifierType, it -> new TreeSet<>(SERVICE_INFO_COMPARATOR))
                            .add(descriptor);
                } else {
                    // contract specific
                    Set<TypeName> realContracts = contracts.stream()
                            .filter(Predicate.not(Injection.QualifiedProvider.TYPE_NAME::equals))
                            .collect(Collectors.toSet());
                    for (TypeName realContract : realContracts) {
                        TypedQualifiedProviderKey key = new TypedQualifiedProviderKey(qualifierType, realContract);
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

    record TypedQualifiedProviderKey(TypeName qualifier, TypeName contract) {
    }

    /**
     * @param coreDescriptor   instance that is generated and used to register injection points etc.
     * @param injectDescriptor instance that satisfies the inject descriptor API
     * @param isCore           if false, both are the same instance
     */
    record Described(GeneratedService.Descriptor<?> coreDescriptor,
                     Descriptor<?> injectDescriptor,
                     boolean isCore) {
    }

    private static class CoreDescriptorWrapper implements Descriptor<Object> {
        private final GeneratedService.Descriptor<?> delegate;
        private List<Ip> injectionPoints;

        CoreDescriptorWrapper(GeneratedService.Descriptor<?> delegate) {
            this.delegate = delegate;

            injectionPoints = delegate.dependencies()
                    .stream()
                    .map(it -> Ip.builder()
                            .from(it)
                            .elementKind(ElementKind.CONSTRUCTOR)
                            .build())
                    .collect(Collectors.toList());
        }

        @Override
        public List<Ip> injectionPoints() {
            return injectionPoints;
        }

        @Override
        public TypeName scope() {
            // core services are equal in functionality to singletons
            return Injection.Singleton.TYPE_NAME;
        }

        @Override
        public TypeName serviceType() {
            return delegate.serviceType();
        }

        @Override
        public TypeName descriptorType() {
            return delegate.descriptorType();
        }

        @Override
        public Set<TypeName> contracts() {
            return delegate.contracts();
        }

        @Override
        public List<Dependency> dependencies() {
            return delegate.dependencies();
        }

        @Override
        public boolean isAbstract() {
            return delegate.isAbstract();
        }

        @Override
        public Object instantiate(DependencyContext ctx) {
            return delegate.instantiate(ctx);
        }

        @Override
        public double weight() {
            return delegate.weight();
        }

        @Override
        public Object instantiate(DependencyContext ctx, GeneratedInjectService.InterceptionMetadata interceptionMetadata) {
            return delegate.instantiate(ctx);
        }

        @Override
        public io.helidon.service.registry.ServiceInfo coreInfo() {
            return delegate;
        }
    }
}
