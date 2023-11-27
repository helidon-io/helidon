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

package io.helidon.inject.tests.scopes;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.RequestScopeControl;
import io.helidon.inject.Scope;
import io.helidon.inject.ScopeNotActiveException;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestScopes {
    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void init() {
        injectionServices = InjectionServices.create(InjectionConfig.create());
        services = injectionServices.services();
    }

    @AfterEach
    void destroy() {
        InjectionServices toShutdown = injectionServices;
        injectionServices = null;
        services = null;

        if (toShutdown != null) {
            toShutdown.shutdown();
        }
    }

    @Test
    void testScopeNotAvailable() {
        Supplier<SingletonContract> serviceProvider = services.supply(SingletonContract.class);
        SingletonContract service = serviceProvider.get();

        ScopeNotActiveException scopeNotAvailableException = assertThrows(ScopeNotActiveException.class, service::id);
        assertThat(scopeNotAvailableException.scope(), is(Injection.RequestScope.TYPE_NAME));
    }

    @Test
    void testDifferentScopeDifferentValue() {
        Supplier<SingletonContract> serviceProvider = services.supply(SingletonContract.class);
        SingletonContract service = serviceProvider.get();

        RequestScopeControl requestonControl = services.get(RequestScopeControl.class);

        int id;
        try (Scope scope = requestonControl.startRequestScope("test-1", Map.of())) {
            id = service.id();
            assertThat("We should get a request scope based id", id, not(-1));
        }

        ScopeNotActiveException scopeNotAvailableException = assertThrows(ScopeNotActiveException.class, service::id);
        assertThat("We should not be in scope when it has been closed",
                   scopeNotAvailableException.scope(),
                   is(Injection.RequestScope.TYPE_NAME));

        try (Scope scope = requestonControl.startRequestScope("test-2", Map.of())) {
            int nextId = service.id();
            assertThat("We should get a request scope based id", nextId, not(-1));
            assertThat("We should get a different request scope than last time", nextId, not(id));
        }
    }
}
