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

package io.helidon.inject.runtime;

import java.util.List;

import io.helidon.inject.Application;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.runtime.testsubjects.HelloInjectionWorld;
import io.helidon.inject.runtime.testsubjects.HelloInjectionWorldImpl;
import io.helidon.inject.runtime.testsubjects.HelloInjection__Application;
import io.helidon.inject.runtime.testsubjects.InjectionWorld;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Sanity type tests only. The "real" testing is in the tests submodules.
 */
class HelloInjectionWorldSanityTest {
    @AfterEach
    void tearDown() {
        HelloInjection__Application.ENABLED = true;
    }

    @Test
    void sanity() {
        InjectionServices injectionServices = InjectionServices.create();
        try {
            Services services = injectionServices.services();

            List<ServiceInfo> moduleProviders = services.lookupServices(Lookup.create(ModuleComponent.class));
            // config, inject itself, empty module, hello injection module
            assertThat(String.join(", ", InjectionTestingSupport.toTypes(moduleProviders)),
                       moduleProviders.size(),
                       equalTo(4));
            List<String> descriptions = InjectionTestingSupport.toTypes(moduleProviders);
            // helidon-config is now first
            assertThat(descriptions,
                       containsInAnyOrder("io.helidon.config.Injection__Module",
                                          "io.helidon.inject.Injection__Module",
                                          "io.helidon.inject.runtime.testsubjects.EmptyModule",
                                          "io.helidon.inject.runtime.testsubjects.HelloInjection__Module"));

            List<ServiceInfo> applications = services.lookupServices(Lookup.create(Application.class));
            assertThat(applications.size(),
                       equalTo(1));
            assertThat(InjectionTestingSupport.toTypes(applications),
                       containsInAnyOrder("io.helidon.inject.runtime.testsubjects.HelloInjection__Application"));
        } finally {
            InjectionTestingSupport.shutdown(injectionServices);
        }
    }

    @Test
    void standardActivationWithNoApplicationEnabled() {
        HelloInjection__Application.ENABLED = false;

        standardActivation();
    }

    @Test
    void standardActivationWithApplicationEnabled() {
        HelloInjection__Application.ENABLED = true;

        standardActivation();
    }

    void standardActivation() {
        InjectionServices injectionServices = InjectionServices.create();

        try {
            Services services = injectionServices.services();

            HelloInjectionWorld hello1 = services.get(HelloInjectionWorld.class);
            assertThat(hello1,
                       notNullValue());

            HelloInjectionWorldImpl hello2 = services.get(HelloInjectionWorldImpl.class);
            assertThat(hello2,
                       sameInstance(hello1));

            InjectionWorld world1 = services.get(InjectionWorld.class);
            assertThat(world1, notNullValue());

            // now activate
            assertThat(hello1.sayHello(),
                       equalTo("Hello inject"));

            assertThat(hello1, instanceOf(HelloInjectionWorldImpl.class));
            HelloInjectionWorldImpl hello1Impl = (HelloInjectionWorldImpl) hello1;
            // check the post construct counts
            assertThat(hello1Impl.postConstructCallCount(),
                       equalTo(1));
            assertThat(hello1Impl.preDestroyCallCount(),
                       equalTo(0));
        } finally {
            InjectionTestingSupport.shutdown(injectionServices);
        }
    }
}
