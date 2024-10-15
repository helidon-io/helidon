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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

/**
 * Basic implementation of the service registry with simple dependency support.
 */
class CoreServiceRegistry implements ServiceRegistry {
    private static final System.Logger LOGGER = System.getLogger(CoreServiceRegistry.class.getName());

    private static final Comparator<ServiceProvider> PROVIDER_COMPARATOR =
            Comparator.comparing(ServiceProvider::weight).reversed()
                    .thenComparing(ServiceProvider::descriptorType);

    private final Map<TypeName, List<ServiceProvider>> providersByContract;
    private final Map<ServiceInfo, ServiceProvider> providersByService;
    private final List<ServiceProvider> allProviders;

    @SuppressWarnings({"rawtypes", "unchecked"})
    CoreServiceRegistry(ServiceRegistryConfig config, ServiceDiscovery serviceDiscovery) {
        List<ServiceProvider> allProviders = new ArrayList<>();
        Map<ResolvedType, List<ServiceProvider>> providers = new HashMap<>();
        Map<ServiceInfo, ServiceProvider> providersByService = new IdentityHashMap<>();

        // each just once
        Set<TypeName> processedDescriptorTypes = new HashSet<>();

        // add me
        ServiceRegistry__ServiceDescriptor registryDescriptor = ServiceRegistry__ServiceDescriptor.INSTANCE;
        processedDescriptorTypes.add(registryDescriptor.descriptorType());
        addContracts(providers, registryDescriptor.contracts(), new BoundInstance(registryDescriptor, Optional.of(this)));

        // add explicit instances
        config.serviceInstances().forEach((descriptor, instance) -> {
            if (processedDescriptorTypes.add(descriptor.descriptorType())) {
                BoundInstance bi = new BoundInstance(descriptor, Optional.of(instance));
                allProviders.add(bi);
                providersByService.put(descriptor, bi);
                addContracts(providers, descriptor.contracts(), bi);
            }
        });

        // add configured descriptors
        for (ServiceDescriptor descriptor : config.serviceDescriptors()) {
            BoundDescriptor bd = new BoundDescriptor(this, descriptor, LazyValue.create(() -> {
                var instance = instance(descriptor);
                instance.ifPresent(descriptor::postConstruct);
                return instance;
            }));
            allProviders.add(bd);
            providersByService.put(descriptor, bd);
            addContracts(providers, descriptor.contracts(), bd);
        }

        boolean logUnsupported = LOGGER.isLoggable(Level.TRACE);

        // and finally add discovered instances
        for (DescriptorHandler descriptorMeta : serviceDiscovery.allMetadata()) {
            if (!descriptorMeta.registryType().equals(DescriptorHandler.REGISTRY_TYPE_CORE)) {
                // we can only support core services, others should be handled by other registry implementations
                if (logUnsupported) {
                    LOGGER.log(Level.TRACE,
                               "Ignoring service of type \"" + descriptorMeta.registryType() + "\": " + descriptorMeta);
                }
                continue;
            }
            if (processedDescriptorTypes.add(descriptorMeta.descriptor().serviceType())) {
                DiscoveredDescriptor dd = new DiscoveredDescriptor(this,
                                                                   descriptorMeta,
                                                                   instanceSupplier(descriptorMeta));
                allProviders.add(dd);
                providersByService.put(descriptorMeta.descriptor(), dd);
                addContracts(providers, descriptorMeta.contracts(), dd);
            }
        }
        // sort all the providers
        providers.values()
                .forEach(it -> it.sort(PROVIDER_COMPARATOR));
        allProviders.sort(PROVIDER_COMPARATOR);
        allProviders.reversed();

        this.providersByContract = Map.copyOf(providers);
        this.providersByService = providersByService;
        this.allProviders = List.copyOf(allProviders);
    }

    @Override
    public <T> T get(TypeName contract) {
        return this.<T>first(contract)
                .orElseThrow(() -> new ServiceRegistryException("Contract " + contract.fqName()
                                                                        + " is not supported, there are no service "
                                                                        + "descriptors in this registry that can satisfy it."));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> first(TypeName contract) {
        return allProviders(contract)
                .stream()
                .flatMap(it -> it.instance().stream())
                .findFirst()
                .map(it -> (T) it);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> all(TypeName contract) {
        return allProviders(contract)
                .stream()
                .flatMap(it -> it.instance().stream())
                .map(it -> (T) it)
                .collect(Collectors.toList());
    }

    @Override
    public <T> Supplier<T> supply(TypeName contract) {
        return () -> get(contract);
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(TypeName contract) {
        return () -> first(contract);
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(TypeName contract) {
        return () -> all(contract);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> get(ServiceInfo serviceInfo) {
        return Optional.ofNullable(providersByService.get(serviceInfo))
                .flatMap(ServiceProvider::instance)
                .map(it -> (T) it);
    }

    @Override
    public List<ServiceInfo> allServices(TypeName contract) {
        return Optional.ofNullable(providersByContract.get(contract))
                .orElseGet(List::of)
                .stream()
                .map(ServiceProvider::descriptor)
                .collect(Collectors.toUnmodifiableList());

    }

    void shutdown() {
        allProviders.forEach(ServiceProvider::close);
    }

    private static void addContracts(Map<ResolvedType, List<ServiceProvider>> providers,
                                     Set<ResolvedType> contracts,
                                     ServiceProvider provider) {
        for (ResolvedType contract : contracts) {
            providers.computeIfAbsent(contract, it -> new ArrayList<>())
                    .add(provider);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ServiceAndInstance instanceSupplier(DescriptorHandler descriptorMeta) {
        LazyValue<Optional<Object>> serviceInstance = LazyValue.create(() -> {
            ServiceDescriptor descriptor = descriptorMeta.descriptor();
            var instance = instance(descriptor);
            instance.ifPresent(descriptor::postConstruct);
            return instance;
        });

        if (descriptorMeta.factoryContracts().isEmpty()) {
            return new ServiceAndInstance(serviceInstance);
        } else {
            return new ServiceAndInstance(serviceInstance,
                                          () -> instanceFromSupplier(descriptorMeta.descriptor(), serviceInstance));
        }
    }

    private record ServiceAndInstance(LazyValue<Optional<Object>> serviceSupplier,
                                      Supplier<Optional<Object>> instanceSupplier) {
        ServiceAndInstance(LazyValue<Optional<Object>> serviceSupplier) {
            this(serviceSupplier, serviceSupplier);
        }
    }

    private List<ServiceProvider> allProviders(TypeName contract) {
        List<ServiceProvider> serviceProviders = providersByContract.get(contract);
        if (serviceProviders == null) {
            return List.of();
        }

        return List.copyOf(serviceProviders);
    }

    private Optional<Object> instanceFromSupplier(ServiceDescriptor<?> descriptor, LazyValue<Optional<Object>> serviceInstanceSupplier) {
        Optional<Object> serviceInstance = serviceInstanceSupplier.get();
        if (serviceInstance.isEmpty()) {
            return Optional.empty();
        }
        Object actualInstance = serviceInstance.get();

        // the service has a Supplier contract, so its instance should implement a supplier
        // services are always singleton for us, but the supplier returned value should be requested each time
        // we use it, to support non-thread-safe instances
        if (actualInstance instanceof Supplier<?> supplier) {
            return fromSupplierValue(supplier.get());
        } else {
            throw new ServiceRegistryException("Service " + descriptor.serviceType().fqName()
                                                       + " exposes Supplier as an interface, yet it does not"
                                                       + " implement it.");
        }
    }

    private Optional<Object> instance(ServiceDescriptor<?> descriptor) {
        var dependencyContext = collectDependencies(descriptor);

        Object serviceInstance = descriptor.instantiate(dependencyContext);
        return Optional.of(serviceInstance);
    }

    private DependencyContext collectDependencies(ServiceDescriptor<?> descriptor) {
        List<? extends Dependency> dependencies = descriptor.dependencies();
        Map<Dependency, Object> collectedDependencies = new HashMap<>();

        for (Dependency dependency : dependencies) {
            TypeName contract = dependency.contract();
            TypeName dependencyType = dependency.typeName();

            if (dependencyType.isSupplier()) {
                TypeName first = dependencyType.typeArguments().getFirst();
                collectedDependencies.put(dependency, (Supplier<Object>) () -> dependencyNoSupplier(first, contract));
            } else {
                collectedDependencies.put(dependency, dependencyNoSupplier(dependencyType, contract));
            }
        }

        return DependencyContext.create(collectedDependencies);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<Object> fromSupplierValue(Object value) {
        if (value instanceof Optional optValue) {
            return optValue;
        }
        return Optional.of(value);
    }

    private Object dependencyNoSupplier(TypeName dependencyType, TypeName contract) {
        if (dependencyType.isList()) {
            return all(contract);
        } else if (dependencyType.isOptional()) {
            return first(contract);
        } else {
            // contract dependency
            return get(contract);
        }
    }

    private interface ServiceProvider {
        ServiceDescriptor<?> descriptor();

        Optional<Object> instance();

        double weight();

        TypeName descriptorType();

        void close();
    }

    private record BoundInstance(ServiceDescriptor<?> descriptor, Optional<Object> instance) implements ServiceProvider {
        @Override
        public double weight() {
            return descriptor.weight();
        }

        @Override
        public TypeName descriptorType() {
            return descriptor.descriptorType();
        }

        @Override
        public void close() {
            // as the instance was provided from outside, we do not call pre-destroy
        }
    }

    private record BoundDescriptor(CoreServiceRegistry registry,
                                   ServiceDescriptor<?> descriptor,
                                   LazyValue<Optional<Object>> lazyInstance,
                                   ReentrantLock lock) implements ServiceProvider {

        private BoundDescriptor(CoreServiceRegistry registry,
                                ServiceDescriptor<?> descriptor,
                                LazyValue<Optional<Object>> lazyInstance) {
            this(registry, descriptor, lazyInstance, new ReentrantLock());
        }

        @Override
        public Optional<Object> instance() {
            if (lazyInstance.isLoaded()) {
                return lazyInstance.get();
            }

            if (lock.isHeldByCurrentThread()) {
                throw new ServiceRegistryException("Cyclic dependency, attempting to obtain an instance of "
                                                           + descriptor.serviceType().fqName() + " while instantiating it");
            }
            try {
                lock.lock();
                return lazyInstance.get();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public double weight() {
            return descriptor.weight();
        }

        @Override
        public TypeName descriptorType() {
            return descriptor.descriptorType();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void close() {
            if (lazyInstance.isLoaded()) {
                lazyInstance.get().ifPresent(it -> ((ServiceDescriptor) descriptor).preDestroy(it));
            }
        }
    }

    private record DiscoveredDescriptor(CoreServiceRegistry registry,
                                        DescriptorHandler metadata,
                                        ServiceAndInstance instances,
                                        ReentrantLock lock) implements ServiceProvider {

        private DiscoveredDescriptor(CoreServiceRegistry registry,
                                     DescriptorHandler metadata,
                                     ServiceAndInstance instances) {
            this(registry, metadata, instances, new ReentrantLock());
        }

        @Override
        public ServiceDescriptor<?> descriptor() {
            return metadata.descriptor();
        }

        @Override
        public Optional<Object> instance() {
            var instanceSupplier = instances.instanceSupplier();
            if ((instanceSupplier instanceof LazyValue<?> lv) && lv.isLoaded()) {
                return instanceSupplier.get();
            }
            if (lock.isHeldByCurrentThread()) {
                throw new ServiceRegistryException("Cyclic dependency, attempting to obtain an instance of "
                                                           + metadata.descriptor().serviceType().fqName()
                                                           + " while instantiating it");
            }
            try {
                lock.lock();
                return instanceSupplier.get();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public double weight() {
            return metadata.weight();
        }

        @Override
        public TypeName descriptorType() {
            return metadata.descriptorType();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void close() {
            var serviceSupplier = instances.serviceSupplier();
            if (serviceSupplier.isLoaded()) {
                serviceSupplier.get().ifPresent(it -> ((ServiceDescriptor) metadata.descriptor()).preDestroy(it));
            }
        }
    }
}
