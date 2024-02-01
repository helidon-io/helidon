package io.helidon.service.inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.service.inject.ServiceSupplies.ServiceSupply;
import io.helidon.service.inject.ServiceSupplies.ServiceSupplyList;
import io.helidon.service.inject.ServiceSupplies.ServiceSupplyOptional;
import io.helidon.service.inject.api.DrivenByName__ServiceDescriptor;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

class ServicePlanBinder implements ServiceInjectionPlanBinder.Binder {
    private final Map<Dependency, IpPlan<?>> injectionPlan = new LinkedHashMap<>();

    private final Descriptor<?> self;
    private final Consumer<Map<Dependency, IpPlan<?>>> injectionPlanConsumer;
    private final InjectServiceRegistry registry;

    private ServicePlanBinder(InjectServiceRegistry registry,
                              Descriptor<?> self,
                              Consumer<Map<Dependency, IpPlan<?>>> injectionPlanConsumer) {
        this.registry = registry;
        this.self = self;
        this.injectionPlanConsumer = injectionPlanConsumer;
    }

    static <T> ServiceInjectionPlanBinder.Binder create(InjectServiceRegistry registry,
                                                        Descriptor<T> descriptor,
                                                        Consumer<Map<Dependency, IpPlan<?>>> planConsumer) {
        return new ServicePlanBinder(registry, descriptor, planConsumer);
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bind(Dependency dependency, ServiceInfo descriptor) {
        if (descriptor == DrivenByName__ServiceDescriptor.INSTANCE) {
            injectionPlan.put(dependency, new IpPlan<>(new DrivenByNameFailingSupplier(dependency), descriptor));
        } else {
            ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(dependency),
                                                          List.of(registry.serviceManager(descriptor)));

            injectionPlan.put(dependency, new IpPlan<>(supply, descriptor));
        }
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptional(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(dependency),
                                                                      toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindList(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(dependency),
                                                              toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplier(Dependency dependency, ServiceInfo descriptor) {
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(dependency),
                                                      toManagers(descriptor));

        injectionPlan.put(dependency, new IpPlan<>(() -> supply, descriptor));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplierOfOptional(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyOptional<?> supply = new ServiceSupplyOptional<>(Lookup.create(dependency),
                                                                      toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(() -> supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplierOfList(Dependency dependency, ServiceInfo... descriptors) {
        ServiceSupplyList<?> supply = new ServiceSupplyList<>(Lookup.create(dependency),
                                                              toManagers(descriptors));

        injectionPlan.put(dependency, new IpPlan<>(() -> supply, descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptionalOfSupplier(Dependency dependency, ServiceInfo... descriptors) {
        // we must resolve this right now, so we just use the first descriptor, and hope the user did not inject
        // this in a wrong scope
        ServiceSupply<?> supply = new ServiceSupply<>(Lookup.create(dependency),
                                                      toManagers(descriptors[0]));
        injectionPlan.put(dependency, new IpPlan<>(() -> Optional.of(supply), descriptors));
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindListOfSuppliers(Dependency dependency, ServiceInfo... descriptors) {
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
    public ServiceInjectionPlanBinder.Binder bindNull(Dependency dependency) {
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

    private static final class DrivenByNameFailingSupplier implements Supplier<Object> {
        private final Dependency dependency;

        private DrivenByNameFailingSupplier(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public Object get() {
            throw new ServiceRegistryException("@DrivenByName should have been resolved to correct name during lookup for "
                                                       + dependency);
        }
    }
}
