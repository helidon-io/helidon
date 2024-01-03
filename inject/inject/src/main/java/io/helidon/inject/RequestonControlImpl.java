package io.helidon.inject;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceInfo;

@Injection.Singleton
class RequestonControlImpl implements RequestonControl, ScopeHandler {
    private static final ThreadLocal<Scope> REQUEST_SCOPES = new ThreadLocal<>();

    private final ServicesImpl services;

    @Injection.Inject
    RequestonControlImpl(ServicesImpl services) {
        this.services = services;
    }

    @Override
    public TypeName supportedScope() {
        return InjectTypes.REQUESTON;
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.ofNullable(REQUEST_SCOPES.get());
    }

    @Override
    public Scope startRequestScope(Map<ServiceInfo, Object> initialBindings) {
        // no need to synchronize, this is per-thread
        Scope scope = REQUEST_SCOPES.get();
        if (scope != null) {
            throw new IllegalStateException("Attempt to re-create request scope. Already exists for this request: " + scope);
        }
        scope = new RequestScope(services, services.createForScope(InjectTypes.REQUESTON, initialBindings));
        REQUEST_SCOPES.set(scope);
        return scope;
    }

    private static class RequestScope implements Scope {
        private final ServicesImpl rootServices;
        private final ScopeServices thisScopeServices;
        private final String thread;

        RequestScope(ServicesImpl rootServices, ScopeServices scopeServices) {
            this.rootServices = rootServices;
            this.thisScopeServices = scopeServices;
            this.thread = Thread.currentThread().toString();
        }

        @Override
        public void close() {
            thisScopeServices.close();
            Scope scope = REQUEST_SCOPES.get();
            if (scope != this) {
                throw new IllegalStateException("Memory leak! Attempting to close request scope in a different thread."
                                                        + " Expected scope: " + this
                                                        + ", thread scope: " + scope);
            }
            REQUEST_SCOPES.remove();
        }

        @Override
        public Services services() {
            return thisScopeServices;
        }

        @Override
        public String toString() {
            return "Request scope for thread: " + thread + ", id: " + hashCode();
        }


    }
}
