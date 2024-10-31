/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.function.Supplier;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

/*
 All code generation should be done as part of main source processing, we can just start service registry and test
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InjectionTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;
    private static LifecycleReceiver lifecycleReceiver;

    @BeforeAll
    public static void initRegistry() {
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    public static void tearDownRegistry() {
        registryManager.shutdown();
        if (lifecycleReceiver != null) {
            assertThat("Pre destroy of a singleton should have been called", lifecycleReceiver.preDestroyCalled(), is(true));
        }
    }

    @Test
    @Order(0)
    public void testSingleton() {
        Supplier<SingletonService> provider = registry.supply(SingletonService.class);

        assertThat(provider, notNullValue());

        SingletonService first = provider.get();
        assertThat(first, notNullValue());

        SingletonService second = provider.get();
        // singleton should always yield the same instance
        assertThat(first, sameInstance(second));
    }

    @Test
    @Order(1)
    public void testLifecycle() {
        Supplier<LifecycleReceiver> provider = registry.supply(LifecycleReceiver.class);

        assertThat(provider, notNullValue());

        lifecycleReceiver = provider.get();
        assertThat(lifecycleReceiver.postConstructCalled(), is(true));
    }

    @Test
    @Order(2)
    public void testNonSingleton() {
        Supplier<NonSingletonService> provider = registry.supply(NonSingletonService.class);

        assertThat(provider, notNullValue());

        NonSingletonService first = provider.get();
        assertThat(first, notNullValue());

        NonSingletonService second = provider.get();
        // non-singleton should always yield different instance
        assertThat(first, not(sameInstance(second)));

        SingletonService firstSingleton = first.singletonService();
        SingletonService secondSingleton = second.singletonService();
        // singleton should always yield the same instance
        assertThat(firstSingleton, sameInstance(secondSingleton));
    }

    @Test
    @Order(3)
    public void testNamed() {
        Supplier<NamedReceiver> provider = registry.supply(NamedReceiver.class);

        assertThat(provider, notNullValue());

        NamedReceiver instance = provider.get();
        assertThat(instance.named(), notNullValue());
        assertThat(instance.named().name(), is("named"));
    }

    @Test
    @Order(4)
    public void testQualified() {
        Supplier<QualifiedReceiver> provider = registry.supply(QualifiedReceiver.class);

        assertThat(provider, notNullValue());

        QualifiedReceiver instance = provider.get();
        assertThat(instance.qualified(), notNullValue());
        assertThat(instance.qualified().qualifier(), is("qualified"));
    }

    @Test
    @Order(5)
    public void testProvider() {
        Supplier<ProviderReceiver> provider = registry.supply(ProviderReceiver.class);

        assertThat(provider, notNullValue());

        ProviderReceiver instance = provider.get();
        assertThat(instance.nonSingletonService(), notNullValue());
        assertThat(instance.listOfServices(), not(empty()));
        assertThat(instance.optionalService(), optionalPresent());
        assertThat(instance.contract(), notNullValue());

        NonSingletonService first = instance.nonSingletonService();
        NonSingletonService second = instance.nonSingletonService();
        assertThat(first, not(sameInstance(second)));
    }

    @Test
    @Order(6)
    public void testInnerTypes() {
        InnerTypes.InnerContract innerContract = registry.get(InnerTypes.InnerContract.class);
        assertThat(innerContract, instanceOf(InnerTypes.InnerService.class));
        assertThat(InnerTypes.InnerService.postConstructCount.get(), is(1));
    }

    @Test
    @Order(7)
    public void testSupplier() {
        Supplier<SuppliedContract> supplier = registry.supply(SuppliedContract.class);

        SuppliedContract first = supplier.get();
        // sanity check we use the counter per instance, not per call
        assertThat(first.message(), is("Supplied:1"));
        assertThat(first.message(), is("Supplied:1"));

        // second instance should be a new one
        SuppliedContract second = supplier.get();
        assertThat(second, not(sameInstance(first)));
        assertThat(second.message(), is("Supplied:2"));
    }
}