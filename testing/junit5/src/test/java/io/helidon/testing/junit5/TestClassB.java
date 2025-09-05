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

package io.helidon.testing.junit5;

import java.util.HashSet;
import java.util.Set;

import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
Tests that each test class has its own instance of global registry.
All three classes are required:
TestClassA - custom instance set using Services.set
TestClassB - custom instance set using Services.set, to make sure we can set different static values from different test classes
TestClassDefault - expecting default (injected) value
 */
@Testing.Test
public class TestClassB {
    private static final Set<Integer> INSTANCES = new HashSet<>();

    @BeforeAll
    public static void beforeAll() {
        Services.set(TestService.class, new TestService("testB"));
        INSTANCES.add(System.identityHashCode(GlobalServiceRegistry.registry()));
    }

    @AfterAll
    public static void afterAll() {
        INSTANCES.add(System.identityHashCode(GlobalServiceRegistry.registry()));
        assertThat("We should only use a single registry instance when annotated with @Testing.Test", INSTANCES.size(), is(1));
    }

    @Test
    public void testRegistry() {
        TestService testService = Services.get(TestService.class);

        assertThat(testService.message(), is("testB"));

        INSTANCES.add(System.identityHashCode(GlobalServiceRegistry.registry()));
    }

    @Test
    public void testRegistry2() {
        TestService testService = Services.get(TestService.class);

        assertThat(testService.message(), is("testB"));

        INSTANCES.add(System.identityHashCode(GlobalServiceRegistry.registry()));
    }
}
