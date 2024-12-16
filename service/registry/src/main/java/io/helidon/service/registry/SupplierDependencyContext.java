package io.helidon.service.registry;

import java.util.Map;
import java.util.function.Supplier;

class SupplierDependencyContext implements DependencyContext {
    private final Map<Dependency, Supplier<Object>> dependencyPlan;

    SupplierDependencyContext(Map<Dependency, Supplier<Object>> dependencyPlan) {
        this.dependencyPlan = dependencyPlan;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T dependency(Dependency dependency) {
        return (T) dependencyPlan.get(dependency).get();
    }
}
