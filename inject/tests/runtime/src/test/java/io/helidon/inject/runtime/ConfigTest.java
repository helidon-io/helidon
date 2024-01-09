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

import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Phase;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
Test that configuration is used if not configured explicitly
 */
class ConfigTest {
    @Test
    void testConfigUsed() {
        InjectionConfig config = InjectionServices.instance()
                .config();

        assertThat(config.useModules(), is(false));
        assertThat(config.useApplication(), is(false));
        assertThat(config.limitRuntimePhase(), is(Phase.PENDING));
        assertThat(config.interceptionEnabled(), is(false));
        assertThat(config.serviceLookupCaching(), is(true));
    }
}
