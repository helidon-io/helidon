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

package io.helidon.config.yaml;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValues;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * This test could be done in main config module, but the problem was reported for yaml,
 *  and I wanted to make sure we use the exact YAML from the issue.
 */
class IgnoreRefsTest {
    @Test
    void testKeyRefsAreIgnored() {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("ignore-refs.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("server.port").as(Integer.class), is(ConfigValues.simpleValue(8080)));
        assertThat(config.get("openapi.schemas.Pet.content.application/json.schema.$ref").asString(),
                   is(ConfigValues.simpleValue("#/components/schemas/Pet")));
    }

    @Test
    void testKeyRefsAreNotIgnored() {
        Config.Builder configBuilder = Config.builder()
                .addSource(ConfigSources.classpath("ignore-refs-2.yaml"))
                .failOnMissingKeyReference(true)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource();

        ConfigException e = assertThrows(ConfigException.class, configBuilder::build);
        assertThat(e.getMessage(), is("Missing token 'ref' to resolve a key reference."));
    }

    @Test
    void testKeyRefsAreIgnoredFromBuilder() {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("ignore-refs-2.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        assertThat(config.get("server.port").as(Integer.class), is(ConfigValues.simpleValue(8080)));
        assertThat(config.get("openapi.schemas.Pet.content.application/json.schema.$ref").asString(),
                   is(ConfigValues.simpleValue("#/components/schemas/Pet")));
    }
}
