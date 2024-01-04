package io.helidon.inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.inject.ServicesImpl.ServiceSupply;
import io.helidon.inject.ServicesImpl.ServiceSupplyList;
import io.helidon.inject.ServicesImpl.ServiceSupplyOptional;
import io.helidon.inject.service.ContextualLookup;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

class ServicePlanBinder implements ServiceInjectionPlanBinder.Binder {
    private final ServiceDescriptor<?> self;
    private final Consumer<Map<Ip, Supplier<?>>> injectionPlanConsumer;

    private final ServicesImpl services;
    private final Map<Ip, Supplier<?>> injectionPlan = new LinkedHashMap<>();

    private ServicePlanBinder(ServicesImpl services,
                              ServiceDescriptor<?> self,
                              Consumer<Map<Ip, Supplier<?>>> injectionPlanConsumer) {
        this.services = services;
        this.self = self;
        this.injectionPlanConsumer = injectionPlanConsumer;
    }

    static <T> ServiceInjectionPlanBinder.Binder create(ServicesImpl registry,
                                                        ServiceDescriptor<T> descriptor,
                                                        Consumer<Map<Ip, Supplier<?>>> planConsumer) {
        return new ServicePlanBinder(registry, descriptor, planConsumer);
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bind(Ip injectionPoint, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(ContextualLookup.create(injectionPoint),
                                                      List.of(services.serviceManager(descriptor)));

        injectionPlan.put(injectionPoint, supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptional(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(ContextualLookup.create(injectionPoint),
                                                                      toManagers(descriptors));

        injectionPlan.put(injectionPoint, supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindList(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(ContextualLookup.create(injectionPoint),
                                                              toManagers(descriptors));

        injectionPlan.put(injectionPoint, supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplier(Ip injectionPoint, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(ContextualLookup.create(injectionPoint),
                                                      toManagers(descriptor));

        injectionPlan.put(injectionPoint, () -> supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptionalSupplier(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(ContextualLookup.create(injectionPoint),
                                                                      toManagers(descriptors));

        injectionPlan.put(injectionPoint, () -> supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindListSupplier(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(ContextualLookup.create(injectionPoint),
                                                              toManagers(descriptors));

        injectionPlan.put(injectionPoint, () -> supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindNull(Ip injectionPoint) {
        injectionPlan.put(injectionPoint, () -> null);
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
}
