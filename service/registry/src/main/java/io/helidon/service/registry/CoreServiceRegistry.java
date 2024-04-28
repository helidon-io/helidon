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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.GeneratedService.Descriptor;

/**
 * Basic implementation of the service registry with simple dependency support.
 */
class CoreServiceRegistry implements ServiceRegistry {
    private static final System.Logger LOGGER = System.getLogger(CoreServiceRegistry.class.getName());

    private static final Comparator<ServiceProvider> PROVIDER_COMPARATOR =
            Comparator.comparing(ServiceProvider::weight).reversed()
                    .thenComparing(ServiceProvider::descriptorType);

    private final Map<TypeName, Set<ServiceProvider>> providersByContract;

    CoreServiceRegistry(ServiceRegistryConfig config, ServiceDiscovery serviceDiscovery) {
        Map<TypeName, Set<ServiceProvider>> providers = new HashMap<>();

        // each just once
        Set<TypeName> processedDescriptorTypes = new HashSet<>();

        // add me
        ServiceRegistry__ServiceDescriptor registryDescriptor = ServiceRegistry__ServiceDescriptor.INSTANCE;
        processedDescriptorTypes.add(registryDescriptor.descriptorType());
        addContracts(providers, registryDescriptor.contracts(), new BoundInstance(registryDescriptor, this));

        // add explicit instances
        config.serviceInstances().forEach((descriptor, instance) -> {
            if (processedDescriptorTypes.add(descriptor.descriptorType())) {
                BoundInstance bi = new BoundInstance(descriptor, instance);
                addContracts(providers, descriptor.contracts(), bi);
            }
        });

        // add configured descriptors
        for (Descriptor<?> descriptor : config.serviceDescriptors()) {
            if (processedDescriptorTypes.add(descriptor.descriptorType())) {
                BoundDescriptor bd = new BoundDescriptor(this, descriptor, LazyValue.create(() -> instance(descriptor)));
                addContracts(providers, descriptor.contracts(), bd);
            }
        }

        boolean logUnsupported = LOGGER.isLoggable(Level.TRACE);

        // and finally add discovered instances
        for (DescriptorMetadata descriptorMeta : serviceDiscovery.allMetadata()) {
            if (!descriptorMeta.registryType().equals(DescriptorMetadata.REGISTRY_TYPE_CORE)) {
                // we can only support core services, others should be handled by other registry implementations
                if (logUnsupported) {
                    LOGGER.log(Level.TRACE,
                               "Ignoring service of type \"" + descriptorMeta.registryType() + "\": " + descriptorMeta);
                }
                continue;
            }
            if (processedDescriptorTypes.add(descriptorMeta.descriptorType())) {
                DiscoveredDescriptor dd = new DiscoveredDescriptor(this,
                                                                   descriptorMeta,
                                                                   LazyValue.create(() -> instance(descriptorMeta.descriptor())));
                addContracts(providers, descriptorMeta.contracts(), dd);
            }
        }
        this.providersByContract = Map.copyOf(providers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(TypeName contract) {
        var provider = firstProvider(contract)
                .orElseThrow(() -> new ServiceRegistryException("Contract " + contract.fqName()
                                                                        + " is not supported, there are no service "
                                                                        + "descriptors in this registry that can satisfy it."));
        return (T) provider.instance();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> first(TypeName contract) {
        return firstProvider(contract)
                .map(ServiceProvider::instance)
                .map(it -> (T) it);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> all(TypeName contract) {
        return allProviders(contract).stream()
                .map(ServiceProvider::instance)
                .map(it -> (T) it)
                .collect(Collectors.toList());
    }

    @Override
    public <T> Supplier<T> supply(TypeName contract) {
        return LazyValue.create(() -> get(contract));
    }

    @Override
    public <T> Supplier<Optional<T>> supplyFirst(TypeName contract) {
        return LazyValue.create(() -> first(contract));
    }

    @Override
    public <T> Supplier<List<T>> supplyAll(TypeName contract) {
        return LazyValue.create(() -> all(contract));
    }

    private static void addContracts(Map<TypeName, Set<ServiceProvider>> providers,
                                     Set<TypeName> contracts,
                                     ServiceProvider provider) {
        for (TypeName contract : contracts) {
            providers.computeIfAbsent(contract, it -> new TreeSet<>(PROVIDER_COMPARATOR))
                    .add(provider);
        }
    }

    private List<ServiceProvider> allProviders(TypeName contract) {
        Set<ServiceProvider> serviceProviders = providersByContract.get(contract);
        if (serviceProviders == null) {
            return List.of();
        }

        return new ArrayList<>(serviceProviders);
    }

    private Optional<ServiceProvider> firstProvider(TypeName contract) {
        Set<ServiceProvider> serviceProviders = providersByContract.get(contract);
        if (serviceProviders == null) {
            return Optional.empty();
        }
        ServiceProvider first = serviceProviders.iterator().next();
        return Optional.of(first);
    }

    private Object instance(Descriptor<?> descriptor) {
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

        return descriptor.instantiate(DependencyContext.create(collectedDependencies));
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
        Descriptor<?> descriptor();

        Object instance();

        double weight();

        TypeName descriptorType();
    }

    private record BoundInstance(Descriptor<?> descriptor, Object instance) implements ServiceProvider {
        @Override
        public double weight() {
            return descriptor.weight();
        }

        @Override
        public TypeName descriptorType() {
            return descriptor.descriptorType();
        }
    }

    private record BoundDescriptor(CoreServiceRegistry registry,
                                   Descriptor<?> descriptor,
                                   LazyValue<Object> lazyInstance,
                                   ReentrantLock lock) implements ServiceProvider {

        private BoundDescriptor(CoreServiceRegistry registry,
                                Descriptor<?> descriptor,
                                LazyValue<Object> lazyInstance) {
            this(registry, descriptor, lazyInstance, new ReentrantLock());
        }

        @Override
        public Object instance() {
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
    }

    private record DiscoveredDescriptor(CoreServiceRegistry registry,
                                        DescriptorMetadata metadata,
                                        LazyValue<Object> lazyInstance,
                                        ReentrantLock lock) implements ServiceProvider {

        private DiscoveredDescriptor(CoreServiceRegistry registry,
                                     DescriptorMetadata metadata,
                                     LazyValue<Object> lazyInstance) {
            this(registry, metadata, lazyInstance, new ReentrantLock());
        }

        @Override
        public Descriptor<?> descriptor() {
            return metadata.descriptor();
        }

        @Override
        public Object instance() {
            if (lazyInstance.isLoaded()) {
                return lazyInstance.get();
            }
            if (lock.isHeldByCurrentThread()) {
                throw new ServiceRegistryException("Cyclic dependency, attempting to obtain an instance of "
                                                           + metadata.descriptor().serviceType().fqName()
                                                           + " while instantiating it");
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
            return metadata.weight();
        }

        @Override
        public TypeName descriptorType() {
            return metadata.descriptorType();
        }
    }
}
