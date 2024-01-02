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

package io.helidon.inject.tests.inject.stacking;

import java.util.List;

import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests cases where service MostOuter injects Outer injects Inner using the same contract.
 */
class InjectionOfCommonContractStackingTest {

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
    void injectionStacking() {
        List<RegistryServiceProvider<CommonContract>> allIntercepted = services.serviceProviders().all(CommonContract.class);
        List<String> desc = allIntercepted.stream().map(RegistryServiceProvider::description).toList();
        // order matters here
        assertThat(desc, contains(
                "MostOuterCommonContractImpl:INIT",
                "OuterCommonContractImpl:INIT",
                "CommonContractImpl:INIT",
                "TestingSingleton:INIT"));

        List<String> injections = allIntercepted.stream()
                .map(sp -> {
                    CommonContract inner = sp.get().getInner();
                    return sp.serviceType().classNameWithEnclosingNames() + " injected with "
                            + (inner == null ? null : inner.getClass().getSimpleName());
                })
                .toList();
        assertThat(injections,
                   contains("MostOuterCommonContractImpl injected with OuterCommonContractImpl",
                            "OuterCommonContractImpl injected with CommonContractImpl",
                            "CommonContractImpl injected with null",
                            "TestingSingleton injected with MostOuterCommonContractImpl"));

        assertThat(services.get(CommonContract.class).get().sayHello("arg"),
                   equalTo("MostOuterCommonContractImpl:OuterCommonContractImpl:CommonContractImpl:arg"));
    }

}
