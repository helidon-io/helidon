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

package io.helidon.pico.tests.pico.provider;

import io.helidon.config.Config;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.testing.PicoTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PerRequestProviderTest {

    Config config = PicoTestingSupport.basicTestableConfig();
    PicoServices picoServices;
    Services services;

    @BeforeEach
    void setUp() {
        setUp(config);
    }

    void setUp(
            Config config) {
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void myConcreteClassContractTest() {
        ServiceProvider<MyConcreteClassContract> sp = services.lookupFirst(MyConcreteClassContract.class);
        assertThat(sp.description(),
                   equalTo("MyServices$MyConcreteClassContractPerRequestIPProvider:INIT"));
        MyConcreteClassContract instance0 = sp.get();
        assertThat(instance0.toString(),
                   equalTo("MyConcreteClassContractPerRequestIPProvider:instance_0, ContextualServiceQuery"
                                   + "(serviceInfoCriteria=ServiceInfoCriteria(serviceTypeName=Optional.empty, "
                                   + "scopeTypeNames=[], qualifiers=[], contractsImplemented=[], runLevel=Optional.empty, "
                                   + "weight=Optional.empty, externalContractsImplemented=[], activatorTypeName=Optional.empty,"
                                   + " moduleName=Optional.empty), injectionPointInfo=Optional.empty, expected=true), "
                                   + "MyConcreteClassContractPerRequestProvider:instance_0"));
        MyConcreteClassContract instance1 = sp.get();
        assertThat(instance1.toString(),
                   equalTo("MyConcreteClassContractPerRequestIPProvider:instance_1, ContextualServiceQuery"
                                   + "(serviceInfoCriteria=ServiceInfoCriteria(serviceTypeName=Optional.empty, "
                                   + "scopeTypeNames=[], qualifiers=[], contractsImplemented=[], runLevel=Optional.empty, "
                                   + "weight=Optional.empty, externalContractsImplemented=[], activatorTypeName=Optional.empty,"
                                   + " moduleName=Optional.empty), injectionPointInfo=Optional.empty, expected=true), "
                                   + "MyConcreteClassContractPerRequestProvider:instance_0"));
    }

}
