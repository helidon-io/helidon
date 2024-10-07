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

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ScopedRegistry;

class DependentScopeHandler implements Injection.ScopeHandler<Injection.PerLookup> {
    private final Scope scope;

    DependentScopeHandler(InjectServiceRegistryImpl serviceRegistry) {
        this.scope = new DependentScope(serviceRegistry);
    }

    @Override
    public Optional<Scope> currentScope() {
        return Optional.of(scope);
    }

    void activate() {
        scope.registry().activate();
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
        public ScopedRegistry registry() {
            return services;
        }
    }

    /**
     * {@link io.helidon.service.inject.api.ScopedRegistry} for services that do not have a scope.
     */
    private static class DependentScopeRegistry extends ScopedRegistryImpl {
        DependentScopeRegistry(InjectServiceRegistryImpl serviceRegistry) {
            super(serviceRegistry, Injection.PerLookup.TYPE, serviceRegistry.id(), Map.of());
        }
    }
}
