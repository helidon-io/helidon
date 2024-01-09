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

package io.helidon.webserver;

import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class WebServerConfigDrivenTest {
    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void init() {
        injectionServices = InjectionServices.create();
    }

    @AfterEach
    void reset() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    @Test
    void testConfigDriven() {
        // make sure the service was active
        // assertThat(webServerSp.currentActivationPhase(), is(Phase.ACTIVE));
    }

}
