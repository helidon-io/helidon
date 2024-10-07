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

package io.helidon.service.tests.inject.scopes;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.RequestScopeControl;
import io.helidon.service.inject.api.Scope;
import io.helidon.service.inject.api.ScopeNotActiveException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestScopes {
    private InjectRegistryManager registryManager;
    private InjectRegistry registry;

    @BeforeEach
    void init() {
        registryManager = InjectRegistryManager.create(InjectConfig.create());
        registry = registryManager.registry();
    }

    @AfterEach
    void destroy() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void testScopeNotAvailable() {
        Supplier<SingletonContract> serviceProvider = registry.supply(SingletonContract.class);
        SingletonContract service = serviceProvider.get();

        ScopeNotActiveException scopeNotAvailableException = assertThrows(ScopeNotActiveException.class, service::id);
        assertThat(scopeNotAvailableException.scope(), is(Injection.PerRequest.TYPE));
    }

    @Test
    void testDifferentScopeDifferentValue() {
        Supplier<SingletonContract> serviceProvider = registry.supply(SingletonContract.class);
        SingletonContract service = serviceProvider.get();

        RequestScopeControl requestonControl = registry.get(RequestScopeControl.class);

        int id;
        try (Scope scope = requestonControl.startRequestScope("test-1", Map.of())) {
            id = service.id();
            assertThat("We should get a request scope based id", id, not(-1));
        }

        ScopeNotActiveException scopeNotAvailableException = assertThrows(ScopeNotActiveException.class, service::id);
        assertThat("We should not be in scope when it has been closed",
                   scopeNotAvailableException.scope(),
                   is(Injection.PerRequest.TYPE));

        try (Scope scope = requestonControl.startRequestScope("test-2", Map.of())) {
            int nextId = service.id();
            assertThat("We should get a request scope based id", nextId, not(-1));
            assertThat("We should get a different request scope than last time", nextId, not(id));
        }
    }

    @Test
    void testDifferentScopeDifferentValueCustomScope() {
        CustomScopeControl ctrl = registry.get(CustomScopeControl.class);
        Supplier<CustomScopedContract> supply = registry.supply(CustomScopedContract.class);

        int id;
        try (Scope scope = ctrl.startScope()) {
            id = supply.get().id();
        }

        ScopeNotActiveException scopeNotAvailableException = assertThrows(ScopeNotActiveException.class, supply::get);
        assertThat("We should not be in scope when it has been closed",
                   scopeNotAvailableException.scope(),
                   is(CustomScope.TYPE));

        try (Scope scope = ctrl.startScope()) {
            int nextId = supply.get().id();
            assertThat("We should get a different custom scope than last time", nextId, not(id));
        }
    }
}
