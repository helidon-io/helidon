package io.helidon.service.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.registry.ServiceSupplies.ServiceInstanceSupply;
import io.helidon.service.registry.ServiceSupplies.ServiceInstanceSupplyList;
import io.helidon.service.registry.ServiceSupplies.ServiceInstanceSupplyOptional;
import io.helidon.service.registry.ServiceSupplies.ServiceSupply;
import io.helidon.service.registry.ServiceSupplies.ServiceSupplyList;
import io.helidon.service.registry.ServiceSupplies.ServiceSupplyOptional;

/**
 * Contains bindings for each service.
 * <p>
 * A binding is a map of injection point to zero or more service descriptors that satisfy it.
 */
class Bindings {
    private final Map<ResolvedType, List<DependencyBinding>> bindingsByContract = new HashMap<>();
    private final Map<ServiceInfo, ServiceBindingPlan> bindingPlans = new IdentityHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final CoreServiceRegistry registry;

    Bindings(CoreServiceRegistry registry) {
        this.registry = registry;
    }

    /*
    Register each service known to the registry (via service descriptors)
     */
    void register(ServiceInfo serviceInfo) {
        lock.lock();
        try {
            ServiceBindingPlan bindingPlan = new ServiceBindingPlan(registry, serviceInfo);
            this.bindingPlans.put(serviceInfo, bindingPlan);
            for (var binding : bindingPlan.allBindings()) {
                bindingsByContract.computeIfAbsent(ResolvedType.create(binding.dependency.contract()), it -> new ArrayList<>())
                        .add(binding);
            }
        } finally {
            lock.unlock();
        }
    }

    /*
    A contract was late bound through Services.set(...), we must forget all bindings for that contract
     */
    void forgetContract(ResolvedType type) {
        lock.lock();
        try {
            List<DependencyBinding> toRemove = bindingsByContract.remove(type);
            if (toRemove == null) {
                // nobody injects this contract
                return;
            }
            toRemove.forEach(DependencyBinding::clear);
        } finally {
            lock.unlock();
        }
    }

    /*
    Binding plan for a specific service, this allows us to:
    - get contract instances to actually inject the service
    - bind pre-built (compile time)
     */
    ServiceBindingPlan bindingPlan(ServiceInfo service) {
        ServiceBindingPlan bindingPlan = bindingPlans.get(service);
        if (bindingPlan == null) {
            // this means we failed to bind services on registry startup, we should have complete knowledge of all
            // available service infos
            throw new ServiceRegistryException("An attempt to get binding plan for service that was not discovered: "
                                                       + service.serviceType());
        }
        return bindingPlan;
    }

    static class ServiceBindingPlan {
        private final Map<Dependency, DependencyBinding> bindingPlan = new HashMap<>();
        private final ServiceInfo serviceInfo;
        private final CoreServiceRegistry registry;

        ServiceBindingPlan(CoreServiceRegistry registry, ServiceInfo serviceInfo) {
            this.serviceInfo = serviceInfo;
            this.registry = registry;
            createBindings();
        }

        void ensure() {
            for (Dependency dependency : serviceInfo.dependencies()) {
                bindingPlan.get(dependency)
                        .instanceSupply();
            }
        }

        Collection<DependencyBinding> allBindings() {
            return bindingPlan.values();
        }

        DependencyBinding binding(Dependency dependency) {
            return bindingPlan.get(dependency);
        }

        private void createBindings() {
            for (Dependency dependency : serviceInfo.dependencies()) {
                bindingPlan.put(dependency, new DependencyBinding(registry, serviceInfo, dependency));
            }
        }
    }

    static class DependencyBinding {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        private final CoreServiceRegistry registry;
        private final ServiceInfo serviceInfo;
        private final Dependency dependency;
        private final Lookup lookup;

        private boolean bound;
        private List<ServiceInfo> serviceInfos;
        private Supplier<Object> instanceSupply;

        private DependencyBinding(CoreServiceRegistry registry, ServiceInfo serviceInfo, Dependency dependency) {
            this.registry = registry;
            this.serviceInfo = serviceInfo;
            this.dependency = dependency;
            this.lookup = Lookup.builder()
                    .dependency(dependency)
                    .update(it -> {
                        if (serviceInfo.contracts().contains(ResolvedType.create(dependency.contract()))
                                && serviceInfo.qualifiers().containsAll(dependency.qualifiers())) {
                            // when injecting a contract that we also implement, we must inject a service of lower weight
                            it.weight(serviceInfo.weight());
                        }
                    })
                    .build();
        }

        public List<ServiceInfo> descriptors() {
            return serviceInfos;
        }

        Dependency dependency() {
            return dependency;
        }

        /*
        Bind from build time generated binding
         */
        void bind(List<ServiceInfo> serviceInfos) {
            lock.writeLock().lock();
            try {
                this.bound = true;
                this.serviceInfos = serviceInfos;
                createInstanceSupply();
            } finally {
                lock.writeLock().unlock();
            }
        }

        /*
        A supplier of value to be injected into this dependency
         */
        Supplier<Object> instanceSupply() {
            lock.readLock().lock();
            try {
                if (bound) {
                    return instanceSupply;
                }
            } finally {
                lock.readLock().unlock();
            }

            lock.writeLock().lock();
            try {
                if (bound) {
                    return instanceSupply;
                }
                // we will provide a value in the next block, we are in write lock, so nobody can read now
                bound = true;
                discoverBinding();
                createInstanceSupply();
                return this.instanceSupply;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /*
        Clear the binding if there was a late binding event
         */
        void clear() {
            lock.writeLock().lock();
            try {
                bound = false;
                serviceInfos = null;
                instanceSupply = null;
            } finally {
                lock.writeLock().unlock();
            }
        }

        private void createInstanceSupply() {
            if (dependency.isServiceInstance()) {
                createInstanceSupplyServiceInstance();
            } else {
                createInstanceSupplyDirectContract();
            }
            if (dependency.isSupplier()) {
                this.instanceSupply = new DependencySupplier(dependency, this.instanceSupply);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void createInstanceSupplyDirectContract() {
            this.instanceSupply = switch (dependency.cardinality()) {
                case REQUIRED -> new ServiceSupply<>(lookup,
                                                     managers(serviceInfos));
                case OPTIONAL -> new ServiceSupplyOptional<>(lookup,
                                                             managers(serviceInfos));
                case LIST -> new ServiceSupplyList<>(lookup,
                                                     managers(serviceInfos));
            };
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void createInstanceSupplyServiceInstance() {
            this.instanceSupply = switch (dependency.cardinality()) {
                case REQUIRED -> new ServiceInstanceSupply<>(lookup,
                                                             managers(serviceInfos));
                case OPTIONAL -> new ServiceInstanceSupplyOptional<>(lookup,
                                                                     managers(serviceInfos));
                case LIST -> new ServiceInstanceSupplyList<>(lookup,
                                                             managers(serviceInfos));
            };
        }

        @SuppressWarnings("rawtypes")
        private List managers(List<ServiceInfo> serviceInfos) {
            return serviceInfos.stream()
                    .map(registry::serviceManager)
                    .collect(Collectors.toUnmodifiableList());
        }

        private void discoverBinding() {
            // lookup services, exclude ourself (when doing chained injection, we lookup by weight)
            List<ServiceInfo> found = registry.lookupServices(lookup)
                    .stream()
                    .filter(it -> it != serviceInfo)
                    .collect(Collectors.toList());
            if (found.isEmpty() && (dependency.cardinality() == DependencyCardinality.REQUIRED)) {
                throw new ServiceRegistryException("There is no service in registry that satisfied this dependency: "
                                                           + dependency);
            }

            // we need all service descriptors, as a service may not yield a service
            // (such as optional suppliers, ServicesFactory etc.), so we use the next one

            this.serviceInfos = found;
        }

        private static class DependencySupplier implements Supplier<Object> {
            private final Dependency dependency;
            private final Supplier<Object> instanceSupply;

            DependencySupplier(Dependency dependency, Supplier<Object> instanceSupply) {
                this.dependency = dependency;
                this.instanceSupply = instanceSupply;
            }

            @Override
            public Object get() {
                return instanceSupply;
            }

            @Override
            public String toString() {
                return "DependencySupplier for " + dependency + ", supply: " + instanceSupply;
            }
        }
    }
}
