/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.config.spi.ConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.MapConfigSource}.
 */
public class MapConfigSourceTest {

    @Test
    public void testDescription() {
        ConfigSource configSource = ConfigSources.create(Map.of()).build();

        assertThat(configSource.description(), is("MapConfig[map]"));
    }

    @Test
    public void testString() {
        Map<String, String> map = Map.of("app.name", "app-name");

        Config config = Config.builder()
                .sources(ConfigSources.create(map))
                .build();

        assertThat(config.get("app.name").asString().get(), is("app-name"));
    }

    @Test
    public void testInt() {
        Map<String, String> map = Map.of("app.port", "8080");

        Config config = Config.builder()
                .sources(ConfigSources.create(map))
                .build();

        assertThat(config.get("app").get("port").asInt().get(), is(8080));
        assertThat(config.get("app.port").asInt().get(), is(8080));
    }

    @Test
    public void testMissingValue() {
        Map<String, String> map = Map.of();

        assertThrows(MissingValueException.class, () -> {
            Config config = Config.builder()
                    .sources(ConfigSources.create(map))
                    .build();

            config.get("app.port").asInt().get();
        });
    }

    @Test
    public void testTraverse() {
        Map<String, String> map = Map.of(
                "app.name", "app-name",
                "app.port", "8080",
                "security", "on");

        Config config = Config.builder()
                .sources(ConfigSources.create(map))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.traverse().count(), is(4L));
        assertThat(config.traverse().map(Config::key).map(Config.Key::toString).collect(Collectors.toList()),
                   containsInAnyOrder("security", "app", "app.name", "app.port"));
    }

    @Test
    public void testChildren() {
        Map<String, String> map = Map.of(
                "app.name", "app-name",
                "app.port", "8080");

        Config config = Config.builder()
                .sources(ConfigSources.create(map))
                .build()
                .get("app");

        assertThat(config.asNodeList()
                           .get()
                           .size(),
                   is(2));

        assertThat(config.asNodeList()
                           .get()
                           .stream()
                           .map(Config::key)
                           .map(Config.Key::toString)
                           .collect(Collectors.toList()),
                   containsInAnyOrder("app.name", "app.port"));
    }

    @Test
    public void testMapToCustomClass() {
        Map<String, String> map = Map.of("app.name", "app-name");

        Config config = Config.builder()
                .sources(ConfigSources.create(map))
                .build();

        assertThat(config.get("app.name")
                           .asString()
                           .map(Name::fromString)
                           .map(Name::getName),
                   is(Optional.of("app-name")));
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
