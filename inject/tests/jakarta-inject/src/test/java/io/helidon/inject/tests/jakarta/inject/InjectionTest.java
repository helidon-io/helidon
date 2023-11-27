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

package io.helidon.inject.tests.jakarta.inject;

import java.util.function.Supplier;

import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
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
class InjectionTest {
    private static InjectionServices injectionServices;
    private static Services services;
    private static LifecycleReceiver lifecycleReceiver;

    @BeforeAll
    static void initRegistry() {
        injectionServices = InjectionServices.create();
        services = injectionServices.services();
    }

    @AfterAll
    static void tearDownRegistry() {
        injectionServices.shutdown();
        if (lifecycleReceiver != null) {
            assertThat("Pre destroy of a singleton should have been called", lifecycleReceiver.preDestroyCalled(), is(true));
        }

        InjectionTestingSupport.shutdown(injectionServices);
    }

    @Test
    @Order(0)
    void testSingleton() {
        Supplier<SingletonService> provider = services.supply(SingletonService.class);

        assertThat(provider, notNullValue());

        SingletonService first = provider.get();
        assertThat(first, notNullValue());

        SingletonService second = provider.get();
        // singleton should always yield the same instance
        assertThat(first, sameInstance(second));
    }

    @Test
    @Order(1)
    void testLifecycle() {
        Supplier<LifecycleReceiver> provider = services.supply(LifecycleReceiver.class);

        assertThat(provider, notNullValue());

        lifecycleReceiver = provider.get();
        assertThat(lifecycleReceiver.postConstructCalled(), is(true));
    }

    @Test
    @Order(2)
    void testNonSingleton() {
        Supplier<NonSingletonService> provider = services.supply(NonSingletonService.class);

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
    void testNamed() {
        Supplier<NamedReceiver> provider = services.supply(NamedReceiver.class);

        assertThat(provider, notNullValue());

        NamedReceiver instance = provider.get();
        assertThat(instance.named(), notNullValue());
        assertThat(instance.named().name(), is("named"));
    }

    @Test
    @Order(4)
    void testQualified() {
        Supplier<QualifiedReceiver> provider = services.supply(QualifiedReceiver.class);

        assertThat(provider, notNullValue());

        QualifiedReceiver instance = provider.get();
        assertThat(instance.qualified(), notNullValue());
        assertThat(instance.qualified().qualifier(), is("qualified"));
    }

    @Test
    @Order(5)
    void testProvider() {
        Supplier<ProviderReceiver> provider = services.supply(ProviderReceiver.class);

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
}