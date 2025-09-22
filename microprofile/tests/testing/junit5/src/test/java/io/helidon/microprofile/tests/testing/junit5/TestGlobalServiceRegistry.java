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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest
class TestGlobalServiceRegistry {
    private static final List<String> ORDER = new ArrayList<>();
    private static final Set<Integer> INSTANCES = new HashSet<>();

    static {
        invoke("static-initializer");
    }

    TestGlobalServiceRegistry() {
        invoke("constructor");
    }

    static void invoke(String source) {
        ORDER.add(source);
        INSTANCES.add(System.identityHashCode(GlobalServiceRegistry.registry()));
        if (INSTANCES.size() > 1) {
            fail(source + " added a new registry instance. Order: " + ORDER);
        }
    }

    @BeforeAll
    static void beforeAll() {
        invoke("beforeAll");
    }

    @AfterAll
    static void afterAll() {
        try {
            invoke("afterAll");
            assertThat(INSTANCES.size(), is(1));
        } finally {
            INSTANCES.clear();
        }
    }

    @BeforeEach
    void beforeEach() {
        invoke("beforeEach");
    }

    @Test
    void firstTest() {
        invoke("firstTest");
        assertThat(INSTANCES.size(), is(1));
        assertThat(INSTANCES.iterator().next(),
                   is(not(System.identityHashCode(Contexts.globalContext()
                                                          .get("helidon-registry", ServiceRegistry.class)
                                                          .orElse(null)))));
    }

    @AfterEach
    void afterEach() {
        invoke("afterEach");
    }

    record MyService() {
    }
}
