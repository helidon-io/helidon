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

package io.helidon.tests.service.inject.lifecycle;

import java.util.function.Supplier;

import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.Activator.Phase;
import io.helidon.service.inject.api.InjectRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class ServiceLifecycleLimitPhaseTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void initClass() {
        ASingletonContractImpl.INJECTIONS.set(0);
        ASingletonContractImpl.INSTANCES.set(0);
        AServiceContractImpl.INJECTIONS.set(0);
        AServiceContractImpl.INSTANCES.set(0);

        registryManager = InjectRegistryManager.create(InjectConfig.builder()
                                                               .limitRuntimePhase(Phase.CONSTRUCTING)
                                                               .build());
        registry = registryManager.registry();

        assertThat(registry, notNullValue());
    }

    @AfterAll
    static void destroyClass() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = null;
        registry = null;
    }

    @Test
    void singletonLifecycleTest() {
        // there should be no instances "just" created when registry starts
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(0));

        Supplier<ASingletonContract> supplier = registry.supply(ASingletonContract.class);

        // there should be no instances created even when I lookup a supplier
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(0));
        assertThat(supplier, notNullValue());

        ASingletonContract instance = supplier.get();

        assertThat(instance, notNullValue());

        // now we should have created a single instance
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(1));
        assertThat(ASingletonContractImpl.INJECTIONS.get(), is(0));

        // now on the next lookup, I should get the same instance
        supplier = registry.supply(ASingletonContract.class);
        ASingletonContract secondInstance = supplier.get();

        // must be the same
        assertThat(secondInstance, sameInstance(instance));
        // and we still should have created only a single instance
        assertThat(ASingletonContractImpl.INSTANCES.get(), is(1));
        assertThat(ASingletonContractImpl.INJECTIONS.get(), is(0));

        InjectedService firstService = instance.service();
        assertThat(firstService, nullValue());
        InjectedService secondService = secondInstance.service();
        assertThat(secondService, nullValue());
    }

    @Test
    void serviceLifecycleTest() {
        // there should be no instances "just" created when registry starts
        assertThat(AServiceContractImpl.INSTANCES.get(), is(0));

        Supplier<AServiceContract> supplier = registry.supply(AServiceContract.class);

        // there should be no instances created even when I lookup a supplier
        assertThat(AServiceContractImpl.INSTANCES.get(), is(0));
        assertThat(supplier, notNullValue());

        AServiceContract instance = supplier.get();

        assertThat(instance, notNullValue());

        // now we should have created a single instance
        assertThat(AServiceContractImpl.INSTANCES.get(), is(1));
        assertThat(AServiceContractImpl.INJECTIONS.get(), is(0));

        AServiceContract secondInstance = supplier.get();

        // now we should have created another instance
        assertThat(AServiceContractImpl.INSTANCES.get(), is(2));
        assertThat(AServiceContractImpl.INJECTIONS.get(), is(0));

        // and it should be a different instance
        assertThat(secondInstance, not(sameInstance(instance)));

        // now on the next lookup, we should get another instance
        supplier = registry.supply(AServiceContract.class);
        AServiceContract thirdInstance = supplier.get();

        // and another instance created
        assertThat(AServiceContractImpl.INSTANCES.get(), is(3));
        assertThat(AServiceContractImpl.INJECTIONS.get(), is(0));

        // must not be the same
        assertThat(thirdInstance, not(sameInstance(instance)));
        assertThat(thirdInstance, not(sameInstance(secondInstance)));

        // now validate the injected singleton
        InjectedService service = instance.service();
        assertThat(service, nullValue());

        InjectedService secondService = secondInstance.service();
        assertThat(secondService, nullValue());

        InjectedService thirdService = thirdInstance.service();
        assertThat(thirdService, nullValue());
    }
}
