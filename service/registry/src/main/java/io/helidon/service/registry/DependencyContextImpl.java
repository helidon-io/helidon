package io.helidon.service.registry;

import java.util.Map;

class DependencyContextImpl implements DependencyContext {
    private final Map<Dependency, Object> dependencies;

    DependencyContextImpl(Map<Dependency, Object> dependencies) {
        this.dependencies = dependencies;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T dependency(Dependency dependency) {
        return (T) dependencies.get(dependency);
    }
}
