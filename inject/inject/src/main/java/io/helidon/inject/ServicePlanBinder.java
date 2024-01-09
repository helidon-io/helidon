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
    private final Consumer<Map<Ip, Supplier<?>>> injectionPlanConsumer;

    private final Services services;
    private final Map<Ip, Supplier<?>> injectionPlan = new LinkedHashMap<>();

    private ServicePlanBinder(Services services,
                              ServiceDescriptor<?> self,
                              Consumer<Map<Ip, Supplier<?>>> injectionPlanConsumer) {
        this.services = services;
        this.self = self;
        this.injectionPlanConsumer = injectionPlanConsumer;
    }

    static <T> ServiceInjectionPlanBinder.Binder create(Services registry,
                                                        ServiceDescriptor<T> descriptor,
                                                        Consumer<Map<Ip, Supplier<?>>> planConsumer) {
        return new ServicePlanBinder(registry, descriptor, planConsumer);
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bind(Ip injectionPoint, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(injectionPoint),
                                                      List.of(services.serviceManager(descriptor)));

        injectionPlan.put(injectionPoint, supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptional(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(injectionPoint),
                                                                      toManagers(descriptors));

        injectionPlan.put(injectionPoint, supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindList(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(injectionPoint),
                                                              toManagers(descriptors));

        injectionPlan.put(injectionPoint, supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplier(Ip injectionPoint, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(injectionPoint),
                                                      toManagers(descriptor));

        injectionPlan.put(injectionPoint, () -> supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplierOfOptional(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(injectionPoint),
                                                                      toManagers(descriptors));

        injectionPlan.put(injectionPoint, () -> supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplierOfList(Ip injectionPoint, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(injectionPoint),
                                                              toManagers(descriptors));

        injectionPlan.put(injectionPoint, () -> supply);
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptionalOfSupplier(Ip injectionPoint, ServiceInfo... descriptors) {
        // we must resolve this right now, so we just use the first descriptor, and hope the user did not inject
        // this in a wrong scope
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(injectionPoint),
                                                      toManagers(descriptors[0]));
        injectionPlan.put(injectionPoint, () -> Optional.of(supply));
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

        injectionPlan.put(injectionPoint, () -> supplies);
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
