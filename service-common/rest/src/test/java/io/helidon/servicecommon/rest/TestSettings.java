/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.servicecommon.rest;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestSettings {

    private static final String ROUTING_VALUE = "my-routing";
    private static final String WEB_CONTEXT_VALUE = "myservice";
    private static final String OVERRIDING_ROUTING_VALUE = "your-routing";

    @Test
    void testConfig() {
        Map<String, String> configSettings = Map.of(
                RestServiceSettings.Builder.ROUTING_NAME_CONFIG_KEY, ROUTING_VALUE,
                RestServiceSettings.Builder.WEB_CONTEXT_CONFIG_KEY, WEB_CONTEXT_VALUE);

        Config config = Config.just(ConfigSources.create(configSettings));
        RestServiceSettings settings = RestServiceSettings.builder()
                .config(config)
                .build();
        assertThat("Routing from config only", settings.routing(), is(ROUTING_VALUE));
        assertThat("Web context from config only", settings.webContext(), is(WEB_CONTEXT_VALUE));

        RestServiceSettings settingsWithOverride = RestServiceSettings.builder()
                .config(config)
                .routing(OVERRIDING_ROUTING_VALUE)
                .build();
        assertThat("Routing from override", settingsWithOverride.routing(), is(OVERRIDING_ROUTING_VALUE));
        assertThat("Web context from config w/o override", settingsWithOverride.webContext(), is(WEB_CONTEXT_VALUE));
    }
}
