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

package io.helidon.service.inject;

import java.util.Map;
import java.util.Optional;

import io.helidon.service.inject.api.InjectRegistrySpi;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.RequestScopeControl;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ScopedRegistry;
import io.helidon.service.registry.ServiceInfo;

@Injection.Singleton
class RequestScopeControlImpl implements RequestScopeControl, Injection.ScopeHandler<Injection.RequestScope> {
    private static final ThreadLocal<RequestScope> REQUEST_SCOPES = new ThreadLocal<>();

    private final InjectRegistrySpi registry;

    @Injection.Inject
    RequestScopeControlImpl(InjectRegistrySpi registry) {
        this.registry = registry;
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.ofNullable(REQUEST_SCOPES.get());
    }

    @Override
    public Scope startRequestScope(String id, Map<ServiceInfo, Object> initialBindings) {
        // no need to synchronize, this is per-thread
        RequestScope scope = REQUEST_SCOPES.get();
        if (scope != null) {
            throw new IllegalStateException("Attempt to re-create request scope. Already exists for this request: " + scope);
        }

        scope = new RequestScope(registry.createForScope(Injection.RequestScope.TYPE, id, initialBindings));
        REQUEST_SCOPES.set(scope);
        scope.activate();
        return scope;
    }

    private static class RequestScope implements Scope {
        private final ScopedRegistry scopedRegistry;
        private final Thread thread;

        RequestScope(ScopedRegistry scopedRegistry) {
            this.scopedRegistry = scopedRegistry;
            this.thread = Thread.currentThread();
        }

        @Override
        public void close() {
            scopedRegistry.deactivate();
            Scope scope = REQUEST_SCOPES.get();
            if (scope != this) {
                throw new IllegalStateException("Memory leak! Attempting to close request scope in a different thread."
                                                        + " Expected scope: " + this
                                                        + ", thread scope: " + scope
                                                        + ", thread that started the scope: " + thread
                                                        + ", current thread: " + Thread.currentThread());
            }
            REQUEST_SCOPES.remove();
        }

        @Override
        public ScopedRegistry services() {
            return scopedRegistry;
        }

        @Override
        public String toString() {
            return "Request scope for thread: " + thread + ", id: " + hashCode();
        }

        void activate() {
            scopedRegistry.activate();
        }
    }
}
