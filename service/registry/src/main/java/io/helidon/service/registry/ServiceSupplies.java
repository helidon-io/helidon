/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

    private static <T> Optional<ResolvedInstance<T>> oneInstance(Lookup lookup,
                                                                 List<ServiceManager<T>> serviceManagers) {
        // we may have more than one service manager, as the first one(s) may not provide a value
        traceLookup(lookup, "explode, filter, and sort");

        for (ServiceManager<T> serviceManager : serviceManagers) {
            Optional<ServiceInstance<T>> thisManager = serviceManager.instances(lookup)
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> serviceManager.registryInstance(lookup, it))
                    .findFirst();

            traceLookupInstance(lookup, serviceManager, thisManager.map(List::of).orElseGet(List::of));

            if (thisManager.isPresent()) {
                return thisManager.map(it -> new ResolvedInstance<>(serviceManager, it));
            }
        }

        return Optional.empty();
    }

    private static <T> List<ResolvedInstance<T>> explodeFilterAndSort(Lookup lookup,
                                                                      List<ServiceManager<T>> serviceManagers) {
        traceLookup(lookup, "explode, filter, and sort");
        List<ResolvedInstance<T>> result = new ArrayList<>();

        for (ServiceManager<T> serviceManager : serviceManagers) {
            List<ServiceInstance<T>> thisManager = new ArrayList<>();
            serviceManager.instances(lookup)
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> serviceManager.registryInstance(lookup, it))
                    .forEach(thisManager::add);

            traceLookupInstance(lookup, serviceManager, thisManager);

            thisManager.stream()
                    .map(it -> new ResolvedInstance<>(serviceManager, it))
                    .forEach(result::add);
        }

        if (result.isEmpty() || result.size() == 1) {
            traceLookupInstances(lookup, result.stream().map(ResolvedInstance::instance).toList());
            return List.copyOf(result);
        }

        /*
        The instances are now ordered by weight of the service providers (implementation or factory)
        We now need to update the ordering, to put unqualified instances first (unless a qualified lookup was done)
        We cannot re-order them using the usual comparator, as the weight of the instance is never set
         */
        if (lookup.qualifiers().isEmpty()) {
            List<ResolvedInstance<T>> unqualified = new ArrayList<>();
            List<ResolvedInstance<T>> qualified = new ArrayList<>();
            for (ResolvedInstance<T> instance : result) {
                if (instance.instance().qualifiers().isEmpty()) {
                    unqualified.add(instance);
                } else {
                    qualified.add(instance);
                }
            }
            unqualified.addAll(qualified);
            result = unqualified;
        }

        traceLookupInstances(lookup, result.stream().map(ResolvedInstance::instance).toList());

        return List.copyOf(result);
    }

    private record ResolvedInstance<T>(ServiceManager<T> manager, ServiceInstance<T> instance) {
        ServiceInstance<T> injected(Lookup lookup, ServiceInfo dependent) {
            manager.dependency(lookup, dependent);
            return instance;
        }
    }

    private static class ServiceSupplyBase<T> {
        private final Lookup lookup;
        private final List<ServiceManager<T>> managers;
        private final ServiceInfo dependent;

        private ServiceSupplyBase(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            this.managers = managers;
            this.lookup = lookup;
            this.dependent = dependent;
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
            this(lookup, managers, null);
        }

        ServiceInstanceSupply(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            super(lookup, managers, dependent);
            Supplier<ServiceInstance<T>> supplier;

            supplier = () -> explodeFilterAndSort(lookup, managers)
                    .stream()
                    .findFirst()
                    .map(it -> it.injected(lookup, dependent))
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
            this(lookup, managers, null);
        }

        ServiceSupply(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            super(lookup, managers, dependent);

            Supplier<T> supplier;

            supplier = () -> oneInstance(lookup, managers)
                    .map(it -> it.injected(lookup, dependent))
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
            this(lookup, managers, null);
        }

        ServiceSupplyOptional(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            super(lookup, managers, dependent);
        }

        @Override
        public Optional<T> get() {
            Optional<ServiceInstance<T>> first = oneInstance(super.lookup, super.managers)
                    .map(it -> it.injected(super.lookup, super.dependent));
            return first.map(Supplier::get);
        }
    }

    static class ServiceInstanceSupplyOptional<T> extends ServiceSupplyBase<T> implements Supplier<Optional<ServiceInstance<T>>> {
        // supply a single instance at runtime based on the manager
        ServiceInstanceSupplyOptional(Lookup lookup, List<ServiceManager<T>> managers) {
            this(lookup, managers, null);
        }

        ServiceInstanceSupplyOptional(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            super(lookup, managers, dependent);
        }

        @Override
        public Optional<ServiceInstance<T>> get() {
            return oneInstance(super.lookup, super.managers)
                    .map(it -> it.injected(super.lookup, super.dependent));
        }
    }

    static class ServiceSupplyList<T> extends ServiceSupplyBase<T> implements Supplier<List<T>> {
        // supply a single instance at runtime based on the manager
        ServiceSupplyList(Lookup lookup, List<ServiceManager<T>> managers) {
            this(lookup, managers, null);
        }

        ServiceSupplyList(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            super(lookup, managers, dependent);
        }

        @Override
        public List<T> get() {
            Stream<ServiceInstance<T>> stream = explodeFilterAndSort(super.lookup, super.managers)
                    .stream()
                    .map(it -> it.injected(super.lookup, super.dependent));

            return stream.map(Supplier::get)
                    .toList();
        }
    }

    static class ServiceInstanceSupplyList<T> extends ServiceSupplyBase<T> implements Supplier<List<ServiceInstance<T>>> {
        // supply a single instance at runtime based on the manager
        ServiceInstanceSupplyList(Lookup lookup, List<ServiceManager<T>> managers) {
            this(lookup, managers, null);
        }

        ServiceInstanceSupplyList(Lookup lookup, List<ServiceManager<T>> managers, ServiceInfo dependent) {
            super(lookup, managers, dependent);
        }

        @Override
        public List<ServiceInstance<T>> get() {
            return explodeFilterAndSort(super.lookup, super.managers)
                    .stream()
                    .map(it -> it.injected(super.lookup, super.dependent))
                    .collect(Collectors.toUnmodifiableList());
        }
    }
}
