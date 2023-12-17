/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.inject.provider;

import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Lookup;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PerRequestProviderTest {

    private final InjectionConfig config = InjectionTestingSupport.basicTestableConfig();
    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void setUp() {
        setUp(config);
    }

    void setUp(InjectionConfig config) {
        this.injectionServices = testableServices(config);
        this.services = injectionServices.services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void myConcreteClassContractTest() {
        ServiceProvider<MyConcreteClassContract> sp = services.getProvider(
                Lookup.builder()
                        .addContract(MyConcreteClassContract.class)
                        .build());
        assertThat(sp.description(),
                   equalTo("MyServices.MyConcreteClassContractPerRequestIPProvider:INIT"));
        MyConcreteClassContract instance0 = sp.get();
        assertThat(instance0.toString(),
                   equalTo("MyConcreteClassContractPerRequestIPProvider:instance_0, "
                                   + "MyConcreteClassContractPerRequestProvider:instance_0"));
        MyConcreteClassContract instance1 = sp.get();
        assertThat(instance1.toString(),
                   equalTo("MyConcreteClassContractPerRequestIPProvider:instance_1, "
                                   + "MyConcreteClassContractPerRequestProvider:instance_0"));
    }

}
