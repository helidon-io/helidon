/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test to reproduce (and validate fix) of
 * <a href="https://github.com/oracle/helidon/issues/1182">Github issue 1182</a>.
 */
public class Gh1182Override {
    private static final Map<String, String> VALUE = Map.of("app1.node1.value", "false");
    private static final Map<String, String> LIST = Map.of(
            "app1.node1.value", "true",
            "app1.node1.value.1", "14",
            "app1.node1.value.2", "15",
            "app1.node1.value.3", "16"
    );

    @Test
    void testValue() {
        Config config = buildConfig(ConfigSources.create(VALUE));

        ConfigValue<String> value = config.get("app1.node1.value").asString();

        assertThat(value, is(ConfigValues.simpleValue("false")));
    }

    @Test
    void testList() {
        Config config = buildConfig(ConfigSources.create(LIST));

        ConfigValue<String> value = config.get("app1.node1.value").asString();

        assertThat(value, is(ConfigValues.simpleValue("true")));
    }

    @Test
    void testOverrideValueOverList() {
        Config config = buildConfig(ConfigSources.create(VALUE),
                                    ConfigSources.create(LIST));

        ConfigValue<String> value = config.get("app1.node1.value").asString();

        assertThat(value, is(ConfigValues.simpleValue("false")));
    }

    @Test
    void testOverrideListOverValue() {
        Config config = buildConfig(ConfigSources.create(LIST),
                                    ConfigSources.create(VALUE));

        ConfigValue<String> value = config.get("app1.node1.value").asString();

        assertThat(value, is(ConfigValues.simpleValue("true")));
    }

    private Config buildConfig(Supplier<? extends ConfigSource>... sources) {
        return Config.builder(sources)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }
}
