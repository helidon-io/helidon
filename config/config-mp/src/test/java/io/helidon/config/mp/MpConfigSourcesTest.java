/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.media.type.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.PropertiesConfigParser;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.ParsableSource;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class MpConfigSourcesTest {
    @Test
    void testHelidonMap() {
        Map<String, String> values = Map.of(
          "key.first", "first",
          "key.second", "second",
          "key.third", "<>{}().,:;/|\\~`?!@#$%^&*-=+*"
        );
        org.eclipse.microprofile.config.spi.ConfigSource mpSource = MpConfigSources.create(ConfigSources.create(values).build());

        assertThat(mpSource.getValue("key.first"), is("first"));
        assertThat(mpSource.getValue("key.second"), is("second"));
        assertThat(mpSource.getValue("key.third"), is("<>{}().,:;/|\\~`?!@#$%^&*-=+*"));
    }

    @Test
    void testProfileSpecificProperty() {
        Map<String, String> values = Map.of(
            "%dev.vehicle.name", "car",
            "vehicle.name", "bike",
            "%dev.vehicle.color", "blue",
            "vehicle.color", "red",
            "%dev.vehicle.size", "large"
        );
        org.eclipse.microprofile.config.spi.ConfigSource mapSource = MpConfigSources.create(ConfigSources.create(values).build());
        assertThat(mapSource.getOrdinal(), is(100));
        assertThat(mapSource.getValue("vehicle.name"), is("bike"));

        // One data source. The profile specific property should take precedence
        MpConfigImpl config = new MpConfigImpl(List.of(mapSource), new HashMap<>(), Collections.emptyList(), "dev");
        assertThat(config.getConfigValue("vehicle.name").getValue(), is("car"));
        assertThat(config.getOptionalValue("vehicle.name", String.class).orElse("error"), is("car"));

        System.setProperty("vehicle.name", "jet");
        System.setProperty("%dev.vehicle.make", "tucker");
        org.eclipse.microprofile.config.spi.ConfigSource propertySource = MpConfigSources.systemProperties();
        assertThat(propertySource.getOrdinal(), is(400));
        assertThat(propertySource.getValue("vehicle.name"), is("jet"));

        // Create Config from both data sources with the "dev" profile
        config = new MpConfigImpl(List.of(propertySource, mapSource), new HashMap<>(), Collections.emptyList(), "dev");

        // The vanilla property in the higher ordinal data source should trump the profile specific property in the
        // lower ordinal data source
        assertThat(config.getConfigValue("vehicle.name").getValue(), is("jet"));
        assertThat(config.getOptionalValue("vehicle.name", String.class).orElse("error"), is("jet"));

        // Within one DataSource the profile specific property takes precedence
        assertThat(config.getConfigValue("vehicle.color").getValue(), is("blue"));
        assertThat(config.getOptionalValue("vehicle.color", String.class).orElse("error"), is("blue"));

        // Make sure missing vanilla values do not mess things up
        assertThat(config.getConfigValue("vehicle.size").getValue(), is("large"));
        assertThat(config.getOptionalValue("vehicle.size", String.class).orElse("error"), is("large"));
        assertThat(config.getConfigValue("vehicle.make").getValue(), is("tucker"));
        assertThat(config.getOptionalValue("vehicle.make", String.class).orElse("error"), is("tucker"));

        System.clearProperty("vehicle.name");
        System.clearProperty("%dev.vehicle.name");
    }

    @Test
    void testHelidonParsable() {
        ParsableImpl helidonSource = new ParsableImpl();
        org.eclipse.microprofile.config.spi.ConfigSource mpSource = MpConfigSources.create(helidonSource);

        assertThat(mpSource.getValue(ParsableImpl.KEY + ".notThere"), nullValue());
        assertThat(mpSource.getValue(ParsableImpl.KEY), is(ParsableImpl.VALUE));

        assertThat(mpSource.getName(), is(ParsableImpl.DESCRIPTION));
        assertThat("init called exactly once", helidonSource.inits.get(), is(1));
        assertThat("exists called exactly once", helidonSource.exists.get(), is(1));
    }
    @Test
    void testHelidonNode() {
        NodeImpl helidonSource = new NodeImpl();
        org.eclipse.microprofile.config.spi.ConfigSource mpSource = MpConfigSources.create(helidonSource);

        assertThat(mpSource.getValue(NodeImpl.KEY + ".notThere"), nullValue());
        assertThat(mpSource.getValue(NodeImpl.KEY), is(NodeImpl.VALUE));

        assertThat(mpSource.getName(), is(NodeImpl.DESCRIPTION));
        assertThat("init called exactly once", helidonSource.inits.get(), is(1));
        assertThat("exists called exactly once", helidonSource.exists.get(), is(1));
    }

    @Test
    void testHelidonLazy() {
        LazyImpl lazy = new LazyImpl();

        org.eclipse.microprofile.config.spi.ConfigSource mpSource = MpConfigSources.create(lazy);
        assertThat(mpSource.getValue("key-1"), nullValue());

        lazy.put("key-1", "value-1");
        assertThat(mpSource.getValue("key-1"), is("value-1"));

        lazy.remove("key-1");
        assertThat(mpSource.getValue("key-1"), nullValue());

        assertThat(mpSource.getName(), is(LazyImpl.DESCRIPTION));
        assertThat("init called exactly once", lazy.inits.get(), is(1));
        assertThat("exists called exactly once", lazy.exists.get(), is(1));
    }

    @Test
    void testMpConfigSourcesNullConfig() {
        NullPointerException npe = assertThrows(NullPointerException.class, () -> MpConfigSources.create((Config) null));
        assertThat(npe.getMessage(), is("Config cannot be null"));
    }

    @Test
    void testSystemPropertiesConfigSourceDefaultOrdinal() {
        org.eclipse.microprofile.config.spi.ConfigSource configSource = MpConfigSources.systemProperties();
        assertThat(configSource.getOrdinal(), is(400));
    }

    @Test
    void testEnvironmentVariablesConfigSourceDefaultOrdinal() {
        org.eclipse.microprofile.config.spi.ConfigSource configSource = MpConfigSources.environmentVariables();
        assertThat(configSource.getOrdinal(), is(300));
    }

    @Test
    void testPropertiesMetaConfigProvider() {
        typeChecks("properties", """
                another1.key=another1.value
                another2.key=another2.value
            """);
    }

    private void typeChecks(String type, String content) {
        org.eclipse.microprofile.config.spi.ConfigSource source =
                MpConfigSources.create(type, new StringReader(content));
        assertThat(source.getValue("another1.key"), is("another1.value"));
        assertThat(source.getValue("another2.key"), is("another2.value"));
    }

    private static final class NodeImpl implements ConfigSource, NodeConfigSource {
        private static final String DESCRIPTION = "node-unit-test";
        private static final String KEY = "key";
        private static final String VALUE = "value";

        private final AtomicInteger inits = new AtomicInteger();
        private final AtomicInteger exists = new AtomicInteger();

        @Override
        public Optional<ConfigContent.NodeContent> load() throws ConfigException {
            return Optional.of(ConfigContent.NodeContent.builder()
                                       .node(ConfigNode.ObjectNode.builder()
                                                     .addValue(KEY, VALUE)
                                                     .build())
                                       .build());
        }

        @Override
        public void init(ConfigContext context) {
            inits.incrementAndGet();
        }

        @Override
        public boolean exists() {
            exists.incrementAndGet();
            return true;
        }

        @Override
        public String description() {
            return DESCRIPTION;
        }
    }

    private static final class LazyImpl implements ConfigSource, LazyConfigSource {
        private static final String DESCRIPTION = "lazy-unit-test";

        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final AtomicInteger inits = new AtomicInteger();
        private final AtomicInteger exists = new AtomicInteger();

        @Override
        public void init(ConfigContext context) {
            inits.incrementAndGet();
        }

        @Override
        public boolean exists() {
            exists.incrementAndGet();
            return true;
        }

        @Override
        public String description() {
            return DESCRIPTION;
        }

        @Override
        public Optional<ConfigNode> node(String key) {
            return Optional.ofNullable(values.get(key))
                    .map(ConfigNode.ValueNode::create);
        }

        private void put(String key, String value) {
            values.put(key, value);
        }

        private void remove(String key) {
            values.remove(key);
        }
    }

    private static final class ParsableImpl implements ConfigSource, ParsableSource {
        private static final String DESCRIPTION = "parsable-unit-test";
        private static final String KEY = "parsable.key";
        private static final String VALUE = "parsableValue";
        private static final String CONTENT = KEY + "=" + VALUE;

        private final AtomicInteger inits = new AtomicInteger();
        private final AtomicInteger exists = new AtomicInteger();

        @Override
        public Optional<ConfigParser.Content> load() throws ConfigException {
            return Optional.of(content());
        }

        private ConfigParser.Content content() {
            return ConfigParser.Content.builder()
                    .charset(StandardCharsets.UTF_8)
                    .data(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)))
                    .mediaType(PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES)
                    .build();
        }

        @Override
        public Optional<ConfigParser> parser() {
            return Optional.empty();
        }

        @Override
        public Optional<MediaType> mediaType() {
            return Optional.empty();
        }

        @Override
        public void init(ConfigContext context) {
            inits.incrementAndGet();
        }

        @Override
        public boolean exists() {
            exists.incrementAndGet();
            return true;
        }

        @Override
        public String description() {
            return DESCRIPTION;
        }
    }
}
