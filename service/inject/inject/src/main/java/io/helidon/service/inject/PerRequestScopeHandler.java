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

import java.util.Optional;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Scope;

@Injection.Singleton
@Injection.NamedByType(Injection.PerRequest.class)
class PerRequestScopeHandler implements Injection.ScopeHandler {
    private static final ThreadLocal<ScopeInfo> REQUEST_SCOPES = new ThreadLocal<>();

    @Override
    public Optional<Scope> currentScope() {
        return Optional.ofNullable(REQUEST_SCOPES.get()).map(ScopeInfo::scope);
    }

    @Override
    public void activate(Scope scope) {
        ScopeInfo currentScope = REQUEST_SCOPES.get();
        if (currentScope != null) {
            throw new IllegalStateException("Attempt to re-create request scope. Already exists for this request: "
                                                    + currentScope.scope);
        }
        REQUEST_SCOPES.set(new ScopeInfo(scope, Thread.currentThread()));
        scope.registry().activate();
    }

    @Override
    public void deactivate(Scope scope) {
        ScopeInfo currentScope = REQUEST_SCOPES.get();
        if (currentScope == null) {
            throw new IllegalStateException("Current scope already de-activated: " + scope);
        }
        if (currentScope.scope != scope) {
            throw new IllegalStateException("Memory leak! Attempting to close request scope in a different thread."
                                                    + " Expected scope: " + scope
                                                    + ", thread scope: " + currentScope
                                                    + ", thread that started the scope: " + currentScope.thread
                                                    + ", current thread: " + Thread.currentThread());
        }
        REQUEST_SCOPES.remove();
        scope.registry().deactivate();
    }

    private record ScopeInfo(Scope scope, Thread thread) {
    }
}
