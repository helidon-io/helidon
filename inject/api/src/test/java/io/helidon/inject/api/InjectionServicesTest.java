/*
 * Copyright (c) 2022-2023 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import io.helidon.inject.api.testsubjects.InjectionServices2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Injection services tests.
 */
class InjectionServicesTest {

    @BeforeEach
    @AfterEach
    void reset() {
        InjectionServicesHolder.reset();
    }

    /**
     * Test basic loader.
     */
    @Test
    void testGetInjectionServices() {
        assertThat(InjectionServices.globalBootstrap(), optionalEmpty());
        Bootstrap bootstrap = Bootstrap.builder().build();
        InjectionServices.globalBootstrap(bootstrap);
        assertThat(InjectionServices.globalBootstrap().orElseThrow(), sameInstance(bootstrap));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> InjectionServices.globalBootstrap(bootstrap));
        assertThat(e.getMessage(),
                   equalTo("The bootstrap has already been set - "
                                   + "use the (-D and/or -A) tag 'inject.debug=true' to see full trace output."));

        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();
        assertThat(injectionServices, notNullValue());
        assertThat(injectionServices, instanceOf(InjectionServices2.class));
        assertThat(injectionServices, sameInstance(InjectionServices.injectionServices().orElseThrow()));

        assertThat(injectionServices.bootstrap(), sameInstance(bootstrap));
    }

    @Test
    void unrealizedServices() {
        assertThat(InjectionServices.unrealizedServices(), optionalEmpty());
    }

}
