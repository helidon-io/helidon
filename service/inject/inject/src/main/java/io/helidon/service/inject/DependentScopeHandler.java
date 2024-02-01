package io.helidon.service.inject;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.ActivationRequest;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.GeneratedInjectService.InterceptionMetadata;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ScopedRegistry;

class DependentScopeHandler implements Injection.ScopeHandler {
    private final Scope scope;

    DependentScopeHandler(InjectServiceRegistry serviceRegistry) {
        this.scope = new DependentScope(serviceRegistry);
    }

    @Override
    public TypeName supportedScope() {
        return Injection.Dependent.TYPE_NAME;
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.of(scope);
    }

    Scope scope() {
        return scope;
    }

    private static class DependentScope implements Scope {
        private final ScopedRegistry services;

        DependentScope(InjectServiceRegistry serviceRegistry) {
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
        DependentScopeRegistry(InjectServiceRegistry serviceRegistry) {
            super(serviceRegistry, Injection.Dependent.TYPE_NAME, serviceRegistry.id(), Map.of());
        }
    }
}
