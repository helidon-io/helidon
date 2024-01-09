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

package io.helidon.inject.tests.stacking;

import java.util.List;

import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.toSimpleTypes;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests cases where service MostOuter injects Outer injects Inner using the same contract.
 * <p>
 * Expected (and why):
 * - MostOuterCommonContractImpl - weight + 3
 * - OuterCommonContractImpl - weight + 2
 * - CommonContractImpl - weight + 1
 */
class StackingTest {

    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void setUp() {
        this.injectionServices = InjectionServices.create();
        this.services = injectionServices.services();
    }

    @AfterEach
    void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    @Test
    void injectionStacking() {
        List<String> commonContractServiceNames = toSimpleTypes(services.lookupServices(Lookup.create(CommonContract.class)));
        // order matters here
        assertThat(commonContractServiceNames, contains(
                "MostOuterCommonContractImpl",
                "OuterCommonContractImpl",
                "CommonContractImpl"));

        List<CommonContract> allIntercepted = services.all(CommonContract.class);

        List<String> injections = allIntercepted.stream()
                .map(commonContract -> {
                    CommonContract inner = commonContract.getInner();
                    return commonContract.getClass().getSimpleName() + " injected with "
                            + (inner == null ? null : inner.getClass().getSimpleName());
                })
                .toList();
        assertThat(injections,
                   contains("MostOuterCommonContractImpl injected with OuterCommonContractImpl",
                            "OuterCommonContractImpl injected with CommonContractImpl",
                            "CommonContractImpl injected with null"));

        assertThat(services.get(CommonContract.class).sayHello("arg"),
                   equalTo("MostOuterCommonContractImpl:OuterCommonContractImpl:CommonContractImpl:arg"));
    }

}
