package io.helidon.service.inject;

import java.util.Map;
import java.util.Optional;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ScopedRegistry;

class DependentScopeHandler implements Injection.ScopeHandler<Injection.Dependent> {
    private final Scope scope;

    DependentScopeHandler(InjectServiceRegistryImpl serviceRegistry) {
        this.scope = new DependentScope(serviceRegistry);
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.of(scope);
    }

    void activate() {
        scope.services().activate();
    }

    Scope scope() {
        return scope;
    }

    private static class DependentScope implements Scope {
        private final ScopedRegistry services;

        DependentScope(InjectServiceRegistryImpl serviceRegistry) {
            this.services = new DependentScopeRegistry(serviceRegistry);
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public ScopedRegistry services() {
            return services;
        }
    }

    /**
     * {@link io.helidon.service.inject.api.ScopedRegistry} for services that do not have a scope.
     */
    private static class DependentScopeRegistry extends ScopedRegistryImpl {
        DependentScopeRegistry(InjectServiceRegistryImpl serviceRegistry) {
            super(serviceRegistry, Injection.Dependent.TYPE_NAME, serviceRegistry.id(), Map.of());
        }
    }
}
