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

package io.helidon.service.tests.inject;

import java.util.List;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.registry.ServiceRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class InjectRegistryTest {
    private static InjectRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    public static void init() {
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    public static void shutdown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = null;
        registry = null;
    }

    @Test
    public void testRegistryGet() {
        MyContract myContract = registry.get(MyContract.class);
        // higher weight
        assertThat(myContract, instanceOf(MyService2.class));

        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }

    @Test
    public void testRegistryFirst() {
        MyContract myContract = registry.first(MyContract.class).get();
        // higher weight
        assertThat(myContract, instanceOf(MyService2.class));
        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }

    @Test
    public void testRegistryAll() {
        List<MyContract> myContracts = registry.all(MyContract.class);
        assertThat(myContracts, hasSize(3));
        // higher weight
        assertThat(myContracts.get(0), instanceOf(MyService2.class));
        assertThat(myContracts.get(1), instanceOf(MyService.class));
        assertThat(myContracts.get(2), instanceOf(MyService3.class));

        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }
}
