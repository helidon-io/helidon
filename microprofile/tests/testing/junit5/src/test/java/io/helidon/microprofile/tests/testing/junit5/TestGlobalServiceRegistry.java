/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tests.testing.junit5;

import java.util.HashSet;
import java.util.Set;

import io.helidon.common.context.Contexts;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@HelidonTest
class TestGlobalServiceRegistry {

    private static final Set<Integer> INSTANCES = new HashSet<>();

    record MyService() {
    }

    static void invoke() {
        INSTANCES.add(System.identityHashCode(GlobalServiceRegistry.registry()));
    }

    static {
        invoke();
    }

    TestGlobalServiceRegistry() {
        invoke();
    }

    @BeforeAll
    static void beforeAll() {
        invoke();
    }

    @BeforeEach
    void beforeEach() {
        invoke();
    }

    @Test
    void firstTest() {
        invoke();
        assertThat(INSTANCES.size(), is(1));
        assertThat(INSTANCES.iterator().next(),
                is(not(System.identityHashCode(Contexts.globalContext()
                        .get("helidon-registry", ServiceRegistry.class)
                        .orElse(null)))));
    }

    @AfterEach
    void afterEach() {
        invoke();
    }

    @AfterAll
    static void afterAll() {
        try {
            invoke();
            assertThat(INSTANCES.size(), is(1));
        } finally {
            INSTANCES.clear();
        }
    }
}
