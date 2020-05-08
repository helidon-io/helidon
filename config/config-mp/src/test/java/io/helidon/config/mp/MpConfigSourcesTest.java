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

package io.helidon.config.mp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class MpConfigSourcesTest {
    @Test
    void testHelidonMap() {
        Map<String, String> values = Map.of(
          "key.first", "first",
          "key.second", "second"
        );
        org.eclipse.microprofile.config.spi.ConfigSource mpSource = MpConfigSources.create(ConfigSources.create(values).build());

        assertThat(mpSource.getValue("key.first"), is("first"));
        assertThat(mpSource.getValue("key.second"), is("second"));
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
        public Optional<String> mediaType() {
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
