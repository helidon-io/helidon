package io.helidon.service.inject;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Scope;

class SingletonScopeHandler implements Injection.ScopeHandler<Injection.Singleton> {
    private final Scope scope;

    SingletonScopeHandler(InjectServiceRegistryImpl serviceRegistry) {
        this.scope = new SingletonScope(serviceRegistry);
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.of(scope);
    }

    Scope scope() {
        return scope;
    }

    void activate() {
        scope.services().activate();
    }

    private static class SingletonScope implements Scope {
        private final LazyValue<ScopedRegistryImpl> services;

        SingletonScope(InjectServiceRegistryImpl serviceRegistry) {
            this.services = LazyValue.create(() -> serviceRegistry.createForScope(Injection.Singleton.TYPE_NAME,
                                                                                  serviceRegistry.id(),
                                                                                  Map.of()));
        }

        @Override
        public void close() {
            // no-op, singleton service registry is closed from InjectionServices
        }

        @Override
        public ScopedRegistryImpl services() {
            return services.get();
        }
    }
}
