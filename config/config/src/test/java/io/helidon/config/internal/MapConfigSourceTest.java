/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link MapConfigSource}.
 */
public class MapConfigSourceTest {

    @Test
    public void testDescription() throws MalformedURLException {
        ConfigSource configSource = ConfigSources.from(CollectionsHelper.mapOf()).build();

        assertThat(configSource.description(), is("MapConfig[map]"));
    }

    @Test
    public void testString() {
        Map<String, String> map = CollectionsHelper.mapOf("app.name", "app-name");

        Config config = Config.builder()
                .sources(ConfigSources.from(map))
                .build();

        assertThat(config.get("app.name").asString(), is("app-name"));
    }

    @Test
    public void testInt() {
        Map<String, String> map = CollectionsHelper.mapOf("app.port", "8080");

        Config config = Config.builder()
                .sources(ConfigSources.from(map))
                .build();

        assertThat(config.get("app").get("port").asInt(), is(8080));
        assertThat(config.get("app.port").asInt(), is(8080));
    }

    @Test
    public void testMissingValue() {
        Map<String, String> map = CollectionsHelper.mapOf();

        Assertions.assertThrows(MissingValueException.class, () -> {
            Config config = Config.builder()
                    .sources(ConfigSources.from(map))
                    .build();

            config.get("app.port").asInt();
        });
    }

    @Test
    public void testTraverse() {
        Map<String, String> map = CollectionsHelper.mapOf(
                "app.name", "app-name",
                "app.port", "8080",
                "security", "on");

        Config config = Config.builder()
                .sources(ConfigSources.from(map))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.traverse().count(), is(4L));
        assertThat(config.traverse().map(Config::key).map(Config.Key::toString).collect(Collectors.toList()),
                   containsInAnyOrder("security", "app", "app.name", "app.port"));
    }

    @Test
    public void testChildren() {
        Map<String, String> map = CollectionsHelper.mapOf(
                "app.name", "app-name",
                "app.port", "8080");

        Config config = Config.builder()
                .sources(ConfigSources.from(map))
                .build()
                .get("app");

        assertThat(config.asNodeList().size(), is(2));
        assertThat(config.asNodeList().stream().map(Config::key).map(Config.Key::toString).collect(Collectors.toList()),
                   containsInAnyOrder("app.name", "app.port"));
    }

    @Test
    public void testMapToCustomClass() {
        Map<String, String> map = CollectionsHelper.mapOf("app.name", "app-name");

        Config config = Config.builder()
                .sources(ConfigSources.from(map))
                .build();

        assertThat(config.get("app.name").map(Name::fromString).getName(), is("app-name"));
    }

    @Test
    @Disabled // since list and object nodes can now contain "direct" values, this no longer fails
    public void testBuilderOverlapParentFirst() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("app", "app-name");
        map.put("app.port", "8080");

        MapConfigSource mapConfigSource = (MapConfigSource) ConfigSources.from(map).lax().build();
        mapConfigSource.init(mock(ConfigContext.class));
        ObjectNode objectNode = mapConfigSource.load().get();

        assertThat(objectNode.entrySet(), hasSize(1));
        assertThat(objectNode.get("app"), valueNode("app-name"));
    }

    @Test
    @Disabled // since list and object nodes can now contain "direct" values, this no longer fails
    public void testBuilderOverlapParentLast() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("app.port", "8080");
        map.put("app", "app-name");

        MapConfigSource mapConfigSource = (MapConfigSource) ConfigSources.from(map).lax().build();
        mapConfigSource.init(mock(ConfigContext.class));
        ObjectNode objectNode = mapConfigSource.load().get();

        assertThat(objectNode.entrySet(), hasSize(1));
        assertThat(((ObjectNode) objectNode.get("app")).get("port"), valueNode("8080"));
    }

    @Test
    @Disabled // since list and object nodes can now contain "direct" values, this no longer fails
    public void testBuilderOverlapStrictParentFirst() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("app", "app-name");
        map.put("app.port", "8080");

        MapConfigSource mapConfigSource = (MapConfigSource) ConfigSources.from(map).build();

        Assertions.assertThrows(ConfigException.class, () -> {
            mapConfigSource.init(mock(ConfigContext.class));
            mapConfigSource.load();
        });
    }

    @Test
    @Disabled // since list and object nodes can now contain "direct" values, this no longer fails
    public void testBuilderOverlapStrictParentLast() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("app.port", "8080");
        map.put("app", "app-name");

        MapConfigSource mapConfigSource = (MapConfigSource) ConfigSources.from(map).build();

        Assertions.assertThrows(ConfigException.class, () -> {
            mapConfigSource.init(mock(ConfigContext.class));
            mapConfigSource.load();
        });
    }

    private static class Name {
        private String name;

        private Name(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        static Name fromString(String name) {
            return new Name(name);
        }
    }
}
