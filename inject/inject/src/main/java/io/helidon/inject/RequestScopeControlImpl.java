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

package io.helidon.inject;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

@Injection.Singleton
class RequestScopeControlImpl implements RequestScopeControl, ScopeHandler {
    private static final ThreadLocal<RequestScope> REQUEST_SCOPES = new ThreadLocal<>();

    private final ServicesSpi services;

    @Injection.Inject
    RequestScopeControlImpl(ServicesSpi services) {
        this.services = services;
    }

    @Override
    public TypeName supportedScope() {
        return Injection.RequestScope.TYPE_NAME;
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.ofNullable(REQUEST_SCOPES.get());
    }

    @Override
    public Scope startRequestScope(String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        // no need to synchronize, this is per-thread
        RequestScope scope = REQUEST_SCOPES.get();
        if (scope != null) {
            throw new IllegalStateException("Attempt to re-create request scope. Already exists for this request: " + scope);
        }

        scope = new RequestScope(services.createForScope(Injection.RequestScope.TYPE_NAME, id, initialBindings));
        REQUEST_SCOPES.set(scope);
        scope.activate();
        return scope;
    }

    private static class RequestScope implements Scope {
        private final ScopeServices thisScopeServices;
        private final String thread;

        RequestScope(ScopeServices scopeServices) {
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
        public ScopeServices services() {
            return thisScopeServices;
        }

        @Override
        public String toString() {
            return "Request scope for thread: " + thread + ", id: " + hashCode();
        }

        void activate() {
            thisScopeServices.activate();
        }
    }
}
