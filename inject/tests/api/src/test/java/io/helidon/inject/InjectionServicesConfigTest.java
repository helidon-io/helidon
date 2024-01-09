/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class InjectionServicesConfigTest {
    /**
     * This tests the default injection configuration.
     */
    @Test
    void testDefaults() {
        InjectionConfig cfg = InjectionConfig.create();
        assertThat(cfg.serviceLookupCaching(), is(false));
        assertThat(cfg.limitRuntimePhase(), is(Phase.ACTIVE));
        assertThat(cfg.useApplication(), is(true));
        assertThat(cfg.useModules(), is(true));
        assertThat(cfg.serviceDescriptors(), is(hasSize(0)));
    }

    @Test
    void testFromConfig() {
        Config config = io.helidon.config.Config.builder(
                        ConfigSources.create(
                                Map.of("inject.service-lookup-caching", "true",
                                       "inject.limit-runtime-phase", "INJECTING",
                                       "inject.use-application", "false",
                                       "inject.use-modules", "false"
                                ), "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        InjectionConfig cfg = InjectionConfig.create(config.get("inject"));

        assertThat(cfg.serviceLookupCaching(), is(true));
        assertThat(cfg.limitRuntimePhase(), is(Phase.INJECTING));
        assertThat(cfg.useApplication(), is(false));
        assertThat(cfg.useModules(), is(false));
    }

}
