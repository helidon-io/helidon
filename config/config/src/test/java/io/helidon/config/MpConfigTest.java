/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.internal.MapConfigSource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test MicroProfile config implementation.
 */
public class MpConfigTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        Object helidonConfig = io.helidon.config.Config.builder()
                .addSource(ConfigSources.classpath("io/helidon/config/application.properties"))
                .addSource(ConfigSources.create(Map.of("mp-1", "mp-value-1",
                                                       "mp-2", "mp-value-2",
                                                       "app.storageEnabled", "false",
                                                       "mp-array", "a,b,c",
                                                       "mp-list.0", "1",
                                                       "mp-list.1", "2",
                                                       "mp-list.2", "3")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(helidonConfig, instanceOf(Config.class));

        config = (Config) helidonConfig;
    }

    @Test
    void testConfigSources() {
        Iterable<ConfigSource> configSources = config.getConfigSources();
        List<ConfigSource> asList = new ArrayList<>();
        for (ConfigSource configSource : configSources) {
            asList.add(configSource);
        }

        assertThat(asList, hasSize(2));
        assertThat(asList.get(0), instanceOf(ClasspathConfigSource.class));
        assertThat(asList.get(1), instanceOf(MapConfigSource.class));

        ConfigSource classpath = asList.get(0);
        assertThat(classpath.getValue("app.storageEnabled"), is("true"));

        ConfigSource map = asList.get(1);
        assertThat(map.getValue("mp-1"), is("mp-value-1"));
        assertThat(map.getValue("mp-2"), is("mp-value-2"));
        assertThat(map.getValue("app.storageEnabled"), is("false"));
    }

    @Test
    void testOptionalValue() {
        assertThat(config.getOptionalValue("app.storageEnabled", Boolean.class), is(Optional.of(true)));
        assertThat(config.getOptionalValue("mp-1", String.class), is(Optional.of("mp-value-1")));
    }

    @Test
    void testStringArray() {
        String[] values = config.getValue("mp-array", String[].class);
        assertThat(values, arrayContaining("a", "b", "c"));
    }

    @Test
    void testIntArray() {
        Integer[] values = config.getValue("mp-list", Integer[].class);
        assertThat(values, arrayContaining(1, 2, 3));
    }

    // TODO if I use app1.node1.value instead to test overriding, the override fails
    //   probably relate to it being an object node in properties and value node in map?
}
