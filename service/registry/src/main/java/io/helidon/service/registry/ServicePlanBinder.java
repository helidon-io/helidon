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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.service.registry.ServiceSupplies.ServiceInstanceSupply;
import io.helidon.service.registry.ServiceSupplies.ServiceInstanceSupplyList;
import io.helidon.service.registry.ServiceSupplies.ServiceInstanceSupplyOptional;
import io.helidon.service.registry.ServiceSupplies.ServiceSupply;
import io.helidon.service.registry.ServiceSupplies.ServiceSupplyList;
import io.helidon.service.registry.ServiceSupplies.ServiceSupplyOptional;

class ServicePlanBinder implements DependencyPlanBinder.Binder {
    private final Map<Dependency, IpPlan<?>> injectionPlan = new LinkedHashMap<>();

    private final ServiceDescriptor<?> self;
    private final Consumer<Map<Dependency, IpPlan<?>>> injectionPlanConsumer;
    private final CoreServiceRegistry registry;

    private ServicePlanBinder(CoreServiceRegistry registry,
                              ServiceDescriptor<?> self,
                              Consumer<Map<Dependency, IpPlan<?>>> injectionPlanConsumer) {
        this.registry = registry;
        this.self = self;
        this.injectionPlanConsumer = injectionPlanConsumer;
    }

    static <T> DependencyPlanBinder.Binder create(CoreServiceRegistry registry,
                                                  ServiceDescriptor<T> descriptor,
                                                  Consumer<Map<Dependency, IpPlan<?>>> planConsumer) {
        return new ServicePlanBinder(registry, descriptor, planConsumer);
    }

    @Override
    public DependencyPlanBinder.Binder bind(Dependency dependency, ServiceInfo descriptor) {
        if (descriptor == InstanceName__ServiceDescriptor.INSTANCE) {
            injectionPlan.put(dependency, new IpPlan<>(new InstanceNameFailingSupplier(dependency), descriptor));
        } else {
            ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(dependency),
                                                          List.of(registry.serviceManager(descriptor)));

            injectionPlan.put(dependency, new IpPlan<>(supply, descriptor));
        }
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindServiceInstance(Dependency dependency, ServiceInfo descriptor) {
        var supply = new ServiceInstanceSupply<>(Lookup.create(dependency), List.of(registry.serviceManager(descriptor)));
        injectionPlan.put(dependency, new IpPlan<>(supply, descriptor));

        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindOptional(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(dependency),
                                                                      toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindOptionalOfServiceInstance(Dependency dependency, ServiceInfo... descriptors) {
        var supply = new ServiceInstanceSupplyOptional<>(Lookup.create(dependency),
                                                         toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindList(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(dependency),
                                                              toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindServiceInstanceList(Dependency dependency, ServiceInfo... descriptors) {
        var supply = new ServiceInstanceSupplyList<>(Lookup.create(dependency),
                                                     toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindSupplier(Dependency dependency, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(dependency),
                                                      toManagers(descriptor));

        injectionPlan.put(dependency, new IpPlan<>(() -> supply, descriptor));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindSupplierOfOptional(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(dependency),
                                                                      toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(() -> supply, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindSupplierOfList(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(dependency),
                                                              toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(() -> supply, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindOptionalOfSupplier(Dependency dependency, ServiceInfo... descriptors) {
        // we must resolve this right now, so we just use the first descriptor, and hope the user did not inject
        // this in a wrong scope
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(dependency),
                                                      toManagers(descriptors[0]));
        injectionPlan.put(dependency, new IpPlan<>(() -> Optional.of(supply), descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindListOfSuppliers(Dependency dependency, ServiceInfo... descriptors) {
        Lookup lookup = Lookup.create(dependency);
        // we must resolve the list right now (one for each descriptor)
        List<ServiceSupply<Object>> supplies = Stream.of(descriptors)
                .map(this::toManagers)
                .map(it -> new ServiceSupply<>(lookup, it))
                .toList();

        injectionPlan.put(dependency, new IpPlan<>(() -> supplies, descriptors));
        return this;
    }

    @Override
    public DependencyPlanBinder.Binder bindNull(Dependency dependency) {
        injectionPlan.put(dependency, new IpPlan<>(() -> null));
        return this;
    }

    @Override
    public void commit() {
        injectionPlanConsumer.accept(Map.copyOf(injectionPlan));
    }

    @Override
    public String toString() {
        return "Service plan binder for " + self.serviceType();
    }

    private <T> List<ServiceManager<T>> toManagers(ServiceInfo... descriptors) {
        List<ServiceManager<T>> result = new ArrayList<>();
        for (ServiceInfo descriptor : descriptors) {
            result.add(registry.serviceManager(descriptor));
        }
        return result;
    }

    private static final class InstanceNameFailingSupplier implements Supplier<Object> {
        private final Dependency dependency;

        private InstanceNameFailingSupplier(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public Object get() {
            throw new ServiceRegistryException(
                    "@" + Service.InstanceName.class.getName()
                            + "should have been resolved to correct name during lookup for "
                            + dependency);
        }
    }
}
