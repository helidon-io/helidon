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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;

import static io.helidon.service.registry.LookupTrace.traceLookup;
import static io.helidon.service.registry.LookupTrace.traceLookupInstance;
import static io.helidon.service.registry.LookupTrace.traceLookupInstances;

final class ServiceSupplies {
    private ServiceSupplies() {
    }

    private static <T> Optional<ServiceInstance<T>> oneInstance(Lookup lookup,
                                                                List<ServiceManager<T>> serviceManagers) {
        // we may have more than one service manager, as the first one(s) may not provide a value
        traceLookup(lookup, "explode, filter, and sort");

        for (ServiceManager<T> serviceManager : serviceManagers) {
            Optional<ServiceInstance<T>> thisManager = serviceManager.activator()
                    .instances(lookup)
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> serviceManager.registryInstance(lookup, it))
                    .findFirst();

            traceLookupInstance(lookup, serviceManager, thisManager.map(List::of).orElseGet(List::of));

            if (thisManager.isPresent()) {
                return thisManager;
            }
        }

        return Optional.empty();
    }

    private static <T> List<ServiceInstance<T>> explodeFilterAndSort(Lookup lookup,
                                                                     List<ServiceManager<T>> serviceManagers) {
        // this method is called when we resolve instances, so we can safely assume any scope is active
        traceLookup(lookup, "explode, filter, and sort");
        List<ServiceInstance<T>> result = new ArrayList<>();

        for (ServiceManager<T> serviceManager : serviceManagers) {
            List<ServiceInstance<T>> thisManager = new ArrayList<>();
            serviceManager.activator()
                    .instances(lookup)
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> serviceManager.registryInstance(lookup, it))
                    .forEach(thisManager::add);

            traceLookupInstance(lookup, serviceManager, thisManager);

            result.addAll(thisManager);
        }

        if (result.isEmpty() || result.size() == 1) {
            traceLookupInstances(lookup, result);
            return List.copyOf(result);
        }

        /*
        The instances are now ordered by weight of the service providers (implementation or factory)
        We now need to update the ordering, to put unqualified instances first (unless a qualified lookup was done)
        We cannot re-order them using the usual comparator, as the weight of the instance is never set
         */
        if (lookup.qualifiers().isEmpty()) {
            List<ServiceInstance<T>> unqualified = new ArrayList<>();
            List<ServiceInstance<T>> qualified = new ArrayList<>();
            for (ServiceInstance<T> instance : result) {
                if (instance.qualifiers().isEmpty()) {
                    unqualified.add(instance);
                } else {
                    qualified.add(instance);
                }
            }
            unqualified.addAll(qualified);
            result = unqualified;
        }

        traceLookupInstances(lookup, result);

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

            supplier = () -> oneInstance(lookup, managers)
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
            Optional<ServiceInstance<T>> first = oneInstance(super.lookup, super.managers);
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
            return oneInstance(super.lookup, super.managers);
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
}
