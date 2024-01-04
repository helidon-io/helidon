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

import io.helidon.common.types.TypeName;
import io.helidon.inject.ActivationResult;
import io.helidon.inject.Activator;
import io.helidon.inject.Application;
import io.helidon.inject.DeActivationRequest;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Phase;
import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.ServiceProviderRegistry;
import io.helidon.inject.Services;
import io.helidon.inject.runtime.testsubjects.HelloInjectionWorld;
import io.helidon.inject.runtime.testsubjects.HelloInjectionWorldImpl;
import io.helidon.inject.runtime.testsubjects.HelloInjection__Application;
import io.helidon.inject.runtime.testsubjects.InjectionWorld;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ModuleComponent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Sanity type tests only. The "real" testing is in the tests submodules.
 */
class HelloInjectionWorldSanityTest {
    // helidon-config is now one of the modules
    private static final int EXPECTED_MODULES = 3;

    @BeforeEach
    void setUp() {
        tearDown();
        InjectionConfig cfg = InjectionConfig.builder()
                .permitsDynamic(true)
                .build();

        InjectionServices.configure(cfg);
    }

    @AfterEach
    void tearDown() {
        HelloInjection__Application.ENABLED = true;
        SimpleInjectionTestingSupport.resetAll();
    }

    @Test
    void sanity() {
        Services serviceRegistry = InjectionServices.instance().services();
        ServiceProviderRegistry services = serviceRegistry.serviceProviders();

        List<RegistryServiceProvider<ModuleComponent>> moduleProviders = services.all(ModuleComponent.class);
        assertThat(moduleProviders.size(),
                   equalTo(EXPECTED_MODULES));
        List<String> descriptions = ProviderUtil.toDescriptions(moduleProviders);
        // helidon-config is now first
        assertThat(descriptions,
                   containsInAnyOrder("Injection__Module:ACTIVE",
                                      "EmptyModule:ACTIVE",
                                      "HelloInjection__Module:ACTIVE"));

        List<RegistryServiceProvider<Application>> applications = services.all(Application.class);
        assertThat(applications.size(),
                   equalTo(1));
        assertThat(ProviderUtil.toDescriptions(applications),
                   containsInAnyOrder("HelloInjection__Application:ACTIVE"));
    }

    @Test
    void standardActivationWithNoApplicationEnabled() {
        HelloInjection__Application.ENABLED = false;

        SimpleInjectionTestingSupport.resetAll();

        standardActivation();
    }

    @Test
    void standardActivationWithApplicationEnabled() {
        HelloInjection__Application.ENABLED = true;

        SimpleInjectionTestingSupport.resetAll();

        standardActivation();
    }

    void standardActivation() {
        Services serviceRegistry = InjectionServices.instance().services();
        ServiceProviderRegistry services = serviceRegistry.serviceProviders();

        RegistryServiceProvider<HelloInjectionWorld> helloProvider1 =
                services.get(HelloInjectionWorld.class);
        assertThat(helloProvider1,
                   notNullValue());

        RegistryServiceProvider<HelloInjectionWorldImpl> helloProvider2 = services.get(HelloInjectionWorldImpl.class);
        assertThat(helloProvider1,
                   sameInstance(helloProvider2));
        assertThat(helloProvider1.id(),
                   equalTo(HelloInjectionWorldImpl.class.getName()));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.INIT));
        assertThat(helloProvider1.description(),
                   equalTo(HelloInjectionWorldImpl.class.getSimpleName() + ":" + Phase.INIT));

        assertThat(helloProvider1.serviceType(),
                   equalTo(TypeName.create(HelloInjectionWorldImpl.class)));
        assertThat(helloProvider1.contracts(),
                   containsInAnyOrder(TypeName.create(HelloInjectionWorld.class)));
        assertThat(helloProvider1.scope(),
                   is(Injection.Singleton.TYPE_NAME));
        assertThat(helloProvider1.qualifiers().size(),
                   equalTo(0));
        assertThat(helloProvider1.runLevel(),
                   equalTo(Injection.RunLevel.NORMAL));
        assertThat(helloProvider1.weight(),
                   equalTo(Services.INJECT_WEIGHT));

        RegistryServiceProvider<InjectionWorld> worldProvider1 = services.get(InjectionWorld.class);
        assertThat(worldProvider1, notNullValue());
        assertThat(worldProvider1.description(),
                   equalTo("InjectionWorldImpl:INIT"));

        // now activate
        HelloInjectionWorld hello1 = helloProvider1.get();
        assertThat(hello1.sayHello(),
                   equalTo("Hello inject"));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloInjectionWorldImpl:ACTIVE"));

        // world should be active now too, since Hello should have implicitly activated it
        assertThat(worldProvider1.description(),
                   equalTo("InjectionWorldImpl:ACTIVE"));

        // check the post construct counts
        assertThat(((HelloInjectionWorldImpl) helloProvider1.get()).postConstructCallCount(),
                   equalTo(1));
        assertThat(((HelloInjectionWorldImpl) helloProvider1.get()).preDestroyCallCount(),
                   equalTo(0));

        // deactivate just the Hello service
        ActivationResult result = ((Activator<?>) helloProvider1).deactivate(DeActivationRequest.create());
        assertThat(result.success(),
                   is(true));
        assertThat(result.serviceProvider(),
                   sameInstance(helloProvider2));
        assertThat(result.finishingActivationPhase(),
                   is(Phase.DESTROYED));
        assertThat(result.startingActivationPhase(),
                   is(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloInjectionWorldImpl:DESTROYED"));
        assertThat(((HelloInjectionWorldImpl) hello1).postConstructCallCount(),
                   equalTo(1));
        assertThat(((HelloInjectionWorldImpl) hello1).preDestroyCallCount(),
                   equalTo(1));
        assertThat(worldProvider1.description(),
                   equalTo("InjectionWorldImpl:ACTIVE"));
    }
}
