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

package io.helidon.inject.tests.inject;

import io.helidon.config.Config;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.basicTestableConfig;
import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class EmptyListInjectionTest {
    private static final Config CONFIG = basicTestableConfig();

    private Services services;

    @BeforeEach
    void setUp() {
        this.services = testableServices(CONFIG).services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void acceptEmptyListInjectables() {
        ServiceProvider<AServiceUsingAContractWithNoServiceImplementations> sp =
                services.lookupFirst(AServiceUsingAContractWithNoServiceImplementations.class);
        assertThat(sp.get(), notNullValue());
    }

}
