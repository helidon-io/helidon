package io.helidon.service.inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.ServiceInstance;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

final class ServiceSupplies {
    private ServiceSupplies() {
    }

    private static <T> List<ServiceInstance<T>> explodeFilterAndSort(Lookup lookup,
                                                                     List<ServiceManager<T>> serviceManagers) {
        // this method is called when we resolve instances, so we can safely assume any scope is active

        List<ServiceInstance<T>> result = new ArrayList<>();

        for (ServiceManager<T> serviceManager : serviceManagers) {
            serviceManager.activator()
                    .instances(lookup)
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> serviceManager.registryInstance(lookup, it))
                    .forEach(result::add);
        }

        result.sort(RegistryInstanceComparator.instance());

        return List.copyOf(result);
    }

    private static class ServiceSupplyBase<T> {
        private final Lookup lookup;
        private final List<ServiceManager<T>> managers;

        private ServiceSupplyBase(Lookup lookup, List<ServiceManager<T>> managers) {
            this.managers = managers;
            this.lookup = lookup;
        }

        @Override
        public String toString() {
            return managers.stream()
                    .map(ServiceManager::descriptor)
                    .map(ServiceInfo::serviceType)
                    .map(TypeName::fqName)
                    .collect(Collectors.joining(", "));
        }
    }

    static class ServiceSupply<T> extends ServiceSupplyBase<T> implements Supplier<T> {
        private final Supplier<T> value;

        // supply a single instance at runtime based on the manager
        ServiceSupply(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);

            Supplier<T> supplier;

            supplier = () -> explodeFilterAndSort(lookup, managers)
                    .stream()
                    .findFirst()
                    .map(ServiceInstance::get)
                    .orElseThrow(() -> new ServiceRegistryException(
                            "Neither of matching services could provide a value. Descriptors: " + managers + ", "
                                    + "lookup: " + super.lookup));

            this.value = supplier;
        }

        @Override
        public T get() {
            return value.get();
        }
    }

    static class ServiceSupplyOptional<T> extends ServiceSupplyBase<T> implements Supplier<Optional<T>> {
        // supply a single instance at runtime based on the manager
        ServiceSupplyOptional(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
        }

        @Override
        public Optional<T> get() {
            Optional<ServiceInstance<T>> first = explodeFilterAndSort(super.lookup, super.managers)
                    .stream()
                    .findFirst();
            return first.map(Supplier::get);
        }
    }

    static class ServiceSupplyList<T> extends ServiceSupplyBase<T> implements Supplier<List<T>> {
        // supply a single instance at runtime based on the manager
        ServiceSupplyList(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
        }

        @Override
        public List<T> get() {
            Stream<ServiceInstance<T>> stream = explodeFilterAndSort(super.lookup, super.managers)
                    .stream();

            return stream.map(Supplier::get)
                    .toList();
        }
    }

    private static class RegistryInstanceComparator implements Comparator<ServiceInstance<?>> {
        private static final RegistryInstanceComparator INSTANCE = new RegistryInstanceComparator();

        private RegistryInstanceComparator() {
        }

        /**
         * Returns a service provider comparator.
         *
         * @return the service provider comparator
         */
        static RegistryInstanceComparator instance() {
            return INSTANCE;
        }

        @Override
        public int compare(ServiceInstance<?> p1,
                           ServiceInstance<?> p2) {
            if (p1 == p2) {
                return 0;
            }

            // unqualified instances always first (even if lower weight)
            if (p1.qualifiers().isEmpty() && !p2.qualifiers().isEmpty()) {
                return -1;
            }

            if (p2.qualifiers().isEmpty() && !p1.qualifiers().isEmpty()) {
                return 1;
            }

            // weights
            int comp = Double.compare(p2.weight(), p1.weight());
            if (comp != 0) {
                return comp;
            }

            // last by name
            return p1.serviceType().compareTo(p2.serviceType());
        }

    }
}
