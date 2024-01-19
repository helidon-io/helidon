/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.Map;

import io.helidon.builder.test.testsubjects.ConfigMap;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

public class ConfigMapTest {
    private static Config config;

    @BeforeAll
    static void beforeAll() {
        config = Config.just(ConfigSources.classpath("config-map-test.yaml"));
    }

    @Test
    void testMap() {
        ConfigMap configMap = ConfigMap.create(config);
        Map<String, String> properties = configMap.properties();

        assertThat(properties, hasEntry("my.first.key", "firstValue"));
        assertThat(properties, hasEntry("my.second.key", "secondValue"));
    }
}
