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
            this.services = LazyValue.create(() -> serviceRegistry.createForScope(Injection.Singleton.TYPE,
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
