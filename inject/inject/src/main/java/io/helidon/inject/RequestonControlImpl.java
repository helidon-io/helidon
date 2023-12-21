package io.helidon.inject;

import java.util.Optional;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

@Injection.Singleton
class RequestonControlImpl implements RequestonControl {
    private static final ThreadLocal<Scope> REQUEST_SCOPES = new ThreadLocal<>();

    private final ServicesImpl services;

    @Injection.Inject
    RequestonControlImpl(ServicesImpl services) {
        this.services = services;
    }

    static Optional<Scope> currentScope() {
        return Optional.ofNullable(REQUEST_SCOPES.get());
    }

    @Override
    public Scope startRequestScope() {
        // no need to synchronize, this is per-thread
        Scope scope = REQUEST_SCOPES.get();
        if (scope != null) {
            throw new IllegalStateException("Attempt to re-create request scope. Already exists for this request: " + scope);
        }
        scope = new RequestScope(services, services.createForScope(InjectTypes.REQUESTON));
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

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public void bind(ServiceDescriptor<?> serviceInfo, Object serviceInstance) {
            thisScopeServices.bind(new RequestScopeServiceProvider(serviceInfo, serviceInstance));
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
        public String toString() {
            return "Request scope for thread: " + thread + ", id: " + hashCode();
        }

        private class RequestScopeServiceProvider<T> extends ServiceProviderBase<T> {
            RequestScopeServiceProvider(ServiceDescriptor<T> descriptor, T serviceInstance) {
                super(rootServices, descriptor);
                state(Phase.ACTIVE, serviceInstance);
            }
        }
    }
}
