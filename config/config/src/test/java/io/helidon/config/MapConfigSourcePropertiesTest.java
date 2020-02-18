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

import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.MapConfigSource} from {@link Properties} method.
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class MapConfigSourcePropertiesTest {

    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-MapConfigSourcePropertiesTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";

    @Test
    public void testFromProperties() {
        Properties props = new Properties();
        props.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        MapConfigSource configSource = ConfigSources.create(props).build();

        ConfigNode.ObjectNode objectNode = configSource.load().get().data();
        assertThat(objectNode.get(TEST_SYS_PROP_NAME), valueNode(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testFromPropertiesDescription() {
        Properties props = new Properties();
        ConfigSource configSource = ConfigSources.create(props).build();

        assertThat(configSource.description(), is("MapConfig[properties]"));
    }

    @Test
    public void testString() {
        Properties properties = new Properties();
        properties.setProperty("app.name", "app-name");

        Config config = Config.builder()
                .sources(ConfigSources.create(properties))
                .build();

        assertThat(config.get("app.name").asString().get(), CoreMatchers.is("app-name"));
    }

    @Test
    public void testInt() {
        Properties properties = new Properties();
        properties.setProperty("app.port", "8080");

        Config config = Config.builder()
                .sources(ConfigSources.create(properties))
                .build();

        assertThat(config.get("app").get("port").asInt().get(), is(8080));
        assertThat(config.get("app.port").asInt().get(), is(8080));
    }

    @Test
    public void testMissingValue() {
        assertThrows(MissingValueException.class, () -> {
            Properties properties = new Properties();

            Config config = Config.builder()
                    .sources(ConfigSources.create(properties))
                    .build();

            config.get("app.port").asInt().get();
        });
    }

    @Test
    public void testTraverse() {
        Properties properties = new Properties();
        properties.setProperty("app.name", "app-name");
        properties.setProperty("app.port", "8080");
        properties.setProperty("security", "on");

        Config config = Config.builder()
                .sources(ConfigSources.create(properties))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.traverse().count(), is(4L));
        assertThat(config.traverse().map(Config::key).map(Config.Key::toString).collect(Collectors.toList()),
                   Matchers.containsInAnyOrder("security", "app", "app.name", "app.port"));
    }

    @Test
    public void testChildren() {
        Properties properties = new Properties();
        properties.setProperty("app.name", "app-name");
        properties.setProperty("app.port", "8080");

        Config config = Config.builder()
                .sources(ConfigSources.create(properties))
                .build()
                .get("app");

        assertThat(config.asNodeList().get().size(), is(2));
        assertThat(config.asNodeList().get()
                           .stream()
                           .map(Config::key)
                           .map(Config.Key::toString)
                           .collect(Collectors.toList()),
                   Matchers.containsInAnyOrder("app.name", "app.port"));
    }

    @Test
    public void testMapToCustomClass() {
        Properties properties = new Properties();
        properties.setProperty("app.name", "app-name");

        Config config = Config.builder()
                .sources(ConfigSources.create(properties))
                .build();

        assertThat(config.get("app.name")
                           .asString()
                           .map(Name::fromString)
                           .map(Name::getName),
                   is(Optional.of("app-name")));
    }

    @Test
    public void testMapToArray() {
        Properties properties = new Properties();
        properties.setProperty("app.0", "zero");
        properties.setProperty("app.1", "one");

        Config config = Config.builder()
                .sources(ConfigSources.create(properties))
                .build();

        assertThat(config.get("app").asNodeList().get().size(), CoreMatchers.is(2));
    }

    @Test
    public void testMapToArrayWithParser() {
        final String PROPS = ""
                + "uri-array.0=http://localhost\n"
                + "uri-array.1=http://localhost\n"
                + "uri-array.2=http://localhost\n"
                + "uri-localhost=http://localhost\n"
                + "uri.array.0=http://localhost\n"
                + "uri.array.1=http://localhost\n"
                + "uri.array.2=http://localhost\n"
                + "uri.localhost=http://localhost\n";

        Config config = Config.builder()
                .sources(ConfigSources.create(PROPS, PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .addParser(ConfigParsers.properties())
                .build();

        assertThat(config.get("uri").asNodeList().get().size(), CoreMatchers.is(2));
        assertThat(config.get("uri.array").asNodeList().get().size(), CoreMatchers.is(3));
        assertThat(config.get("uri-array").asNodeList().get().size(), CoreMatchers.is(3));
    }

    private static final class Name {
        private final String name;

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
