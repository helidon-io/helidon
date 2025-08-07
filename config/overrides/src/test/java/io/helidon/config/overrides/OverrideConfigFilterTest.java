/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.config.overrides;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OverrideConfigFilterTest {
    @Test
    void testNoConfig() {
        Config config = Config.just(ConfigSources.classpath("/config.yaml"));

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("ERROR"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("ERROR"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("ERROR"));
    }

    @Test
    void testDocConfigPrototype() {
        var filter = OverrideConfigFilter.builder()
                .putOverrideExpression("prod.abcdef.logging.level", "FINEST")
                .putOverrideExpression("prod.*.logging.level", "WARNING")
                .putOverrideExpression("test.*.logging.level", "FINE")
                .build();

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testDocConfigSource() {
        var filter = OverrideConfigFilter.builder()
                .addConfigSource(ConfigSources.classpath("/overrides.properties").get())
                .build();

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testDocConfigInstance() {
        var filter = OverrideConfigFilter.create(Config.builder()
                                            .addSource(ConfigSources.classpath("/overrides.properties"))
                                            .disableEnvironmentVariablesSource()
                                            .disableSystemPropertiesSource()
                                            // we do not want to use an override filter for its own config source
                                            .disableFilterServices()
                                            .build());

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testInlinedOverrides() {
        Config config = Config.just(ConfigSources.classpath("/config.yaml"),
                                    ConfigSources.classpath("/config-with-overrides.yaml"));

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }
}

