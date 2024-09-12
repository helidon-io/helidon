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
import io.helidon.service.inject.api.Qualifier;
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

    static class ServiceInstanceSupply<T> extends ServiceSupplyBase<T> implements Supplier<ServiceInstance<T>> {
        private final Supplier<ServiceInstance<T>> value;

        ServiceInstanceSupply(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
            Supplier<ServiceInstance<T>> supplier;

            supplier = () -> explodeFilterAndSort(lookup, managers)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new ServiceRegistryException(
                            "Neither of matching services could provide a value. Descriptors: " + managers + ", "
                                    + "lookup: " + super.lookup));

            this.value = supplier;
        }

        @Override
        public ServiceInstance<T> get() {
            return value.get();
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

    static class ServiceInstanceSupplyOptional<T> extends ServiceSupplyBase<T> implements Supplier<Optional<ServiceInstance<T>>> {
        // supply a single instance at runtime based on the manager
        ServiceInstanceSupplyOptional(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
        }

        @Override
        public Optional<ServiceInstance<T>> get() {
            return explodeFilterAndSort(super.lookup, super.managers)
                    .stream()
                    .findFirst();
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

    static class ServiceInstanceSupplyList<T> extends ServiceSupplyBase<T> implements Supplier<List<ServiceInstance<T>>> {
        // supply a single instance at runtime based on the manager
        ServiceInstanceSupplyList(Lookup lookup, List<ServiceManager<T>> managers) {
            super(lookup, managers);
        }

        @Override
        public List<ServiceInstance<T>> get() {
            return explodeFilterAndSort(super.lookup, super.managers)
                    .stream()
                    .collect(Collectors.toUnmodifiableList());
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

            // @default name before any other name
            if (p1.qualifiers().contains(Qualifier.DEFAULT_NAMED) && !p2.qualifiers().contains(Qualifier.DEFAULT_NAMED)) {
                return -1;
            }

            if (p2.qualifiers().contains(Qualifier.DEFAULT_NAMED) && !p1.qualifiers().contains(Qualifier.DEFAULT_NAMED)) {
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
