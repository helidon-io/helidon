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

package io.helidon.inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.inject.Services.ServiceSupply;
import io.helidon.inject.Services.ServiceSupplyList;
import io.helidon.inject.Services.ServiceSupplyOptional;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

class ServicePlanBinder implements ServiceInjectionPlanBinder.Binder {
    private final ServiceDescriptor<?> self;
    private final Consumer<Map<Ip, IpPlan<?>>> injectionPlanConsumer;

    private final Services services;
    private final Map<Ip, IpPlan<?>> injectionPlan = new LinkedHashMap<>();

    private ServicePlanBinder(Services services,
                              ServiceDescriptor<?> self,
                              Consumer<Map<Ip, IpPlan<?>>> injectionPlanConsumer) {
        this.services = services;
        this.self = self;
        this.injectionPlanConsumer = injectionPlanConsumer;
    }

    static <T> ServiceInjectionPlanBinder.Binder create(Services registry,
                                                        ServiceDescriptor<T> descriptor,
                                                        Consumer<Map<Ip, IpPlan<?>>> planConsumer) {
        return new ServicePlanBinder(registry, descriptor, planConsumer);
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bind(Ip injectionPoint, ServiceInfo descriptor) {
        if (descriptor == DrivenByName__ServiceDescriptor.INSTANCE) {
            injectionPlan.put(injectionPoint, new IpPlan<>(new DrivenByNameFailingSupplier(injectionPoint), descriptor));
        } else {
            ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(injectionPoint),
                                                          List.of(services.serviceManager(descriptor)));

            injectionPlan.put(injectionPoint, new IpPlan<>(supply, descriptor));
        }
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptional(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(injectionPoint),
                                                                      toManagers(descriptors));

        injectionPlan.put(injectionPoint, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindList(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(injectionPoint),
                                                              toManagers(descriptors));

        injectionPlan.put(injectionPoint, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplier(Ip injectionPoint, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(injectionPoint),
                                                      toManagers(descriptor));

        injectionPlan.put(injectionPoint, new IpPlan<>(() -> supply, descriptor));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplierOfOptional(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(injectionPoint),
                                                                      toManagers(descriptors));

        injectionPlan.put(injectionPoint, new IpPlan<>(() -> supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplierOfList(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(injectionPoint),
                                                              toManagers(descriptors));

        injectionPlan.put(injectionPoint, new IpPlan<>(() -> supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptionalOfSupplier(Ip injectionPoint, ServiceInfo... descriptors) {
        // we must resolve this right now, so we just use the first descriptor, and hope the user did not inject
        // this in a wrong scope
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(injectionPoint),
                                                      toManagers(descriptors[0]));
        injectionPlan.put(injectionPoint, new IpPlan<>(() -> Optional.of(supply), descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindListOfSuppliers(Ip injectionPoint, ServiceInfo... descriptors) {
        Lookup lookup = Lookup.create(injectionPoint);
        // we must resolve the list right now (one for each descriptor)
        List<ServiceSupply<Object>> supplies = Stream.of(descriptors)
                .map(this::toManagers)
                .map(it -> new ServiceSupply<>(lookup, it))
                .toList();

        injectionPlan.put(injectionPoint, new IpPlan<>(() -> supplies, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindNull(Ip injectionPoint) {
        injectionPlan.put(injectionPoint, new IpPlan<>(() -> null));
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
        return Stream.of(descriptors)
                .map(services::<T>serviceManager)
                .toList();
    }

    private static final class DrivenByNameFailingSupplier implements Supplier<Object> {
        private final Ip ip;

        private DrivenByNameFailingSupplier(Ip ip) {
            this.ip = ip;
        }

        @Override
        public Object get() {
            throw new InjectionException("@DrivenByName should have been resolved to correct name during lookup for " + ip);
        }
    }
}
