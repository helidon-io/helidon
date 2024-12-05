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

package io.helidon.service.tests.interception;

import java.util.List;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.tests.interception.AbstractClassTypes.MyAbstractClassContract;
import io.helidon.service.tests.interception.AbstractClassTypes.MyAbstractClassContractImpl;
import io.helidon.service.tests.interception.AbstractClassTypes.MyServiceInterceptor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

// test interceptor annotated on abstract method of an abstract class
class AbstractClassInterceptionTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    static void init() {
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    static void shutdown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void testInterceptor() {
        MyServiceInterceptor.INVOKED.clear();

        var myAbstractClassContract = registry.get(MyAbstractClassContract.class);
        assertThat(myAbstractClassContract.sayHello("Jessica"), is("Hello Jessica!"));
        assertThat(myAbstractClassContract.sayHello("Juliet"), is("Hello Juliet!"));
        assertThat(myAbstractClassContract.sayHelloDirect("John"), is("Hello John!"));

        assertThat(MyServiceInterceptor.INVOKED, is(List.of(
                "%s.sayHello: [Jessica]".formatted(MyAbstractClassContractImpl.class.getName()),
                "%s.sayHello: [Juliet]".formatted(MyAbstractClassContractImpl.class.getName()),
                "%s.sayHelloDirect: [John]".formatted(MyAbstractClassContractImpl.class.getName()))));
    }
}
