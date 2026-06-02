/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.tests.interception;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class FieldInjectionInterceptionTest {
    private static final ServiceRegistryManager REGISTRY_MANAGER = ServiceRegistryManager.create();
    private static final ServiceRegistry REGISTRY = REGISTRY_MANAGER.registry();

    @AfterAll
    static void shutdown() {
        REGISTRY_MANAGER.shutdown();
    }

    @BeforeEach
    void beforeEach() {
        ModifyingInterceptor.lastCall();
    }

    @Test
    void fieldInjectionCanBeIntercepted() {
        FieldInjectionService service = REGISTRY.get(FieldInjectionService.class);
        Invocation invocation = ModifyingInterceptor.lastCall();

        assertAll(
                () -> assertThat(service.dependencyMessage(), is("field injected")),
                () -> assertThat(invocation, notNullValue()),
                () -> assertThat(invocation.methodName(), equalTo("dependency")),
                () -> assertThat(invocation.args().length, is(1)),
                () -> assertThat(invocation.args()[0], instanceOf(FieldInjectedDependency.class))
        );
    }
}
