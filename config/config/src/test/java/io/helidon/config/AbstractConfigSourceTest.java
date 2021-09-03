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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.Source;
import io.helidon.config.spi.WatchableSource;

import org.junit.jupiter.api.Test;

import static io.helidon.config.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link AbstractConfigSource}.
 */
public class AbstractConfigSourceTest {

    @Test
    public void testBuilderDefault() {
        TestingConfigSource.Builder builder = TestingConfigSource.builder();

        assertThat(builder.isOptional(), is(false));
        assertThat(builder.mediaTypeMapping(), is(Optional.empty()));
        assertThat(builder.parserMapping(), is(Optional.empty()));
        assertThat(builder.pollingStrategy(), is(Optional.empty()));
        assertThat(builder.changeWatcher(), is(Optional.empty()));
        assertThat(builder.mediaType(), is(Optional.empty()));
        assertThat(builder.parser(), is(Optional.empty()));
    }

    @Test
    public void testFormatDescriptionOptionalNoParams() {
        TestingConfigSource configSource = TestingConfigSource.builder()
                .optional()
                .build();

        assertThat(configSource.description(), is("TestingConfig[test]?"));
    }

    @Test
    public void testFormatDescriptionOptionalNoParamsNoPolling() {
        TestingConfigSource configSource = TestingConfigSource.builderNoPolling().optional().build();

        assertThat(configSource.description(), is("TestingConfig[test]?"));
    }

    @Test
    public void testFormatDescriptionOptionalWithParams() {
        TestingConfigSource configSource = TestingConfigSource.builder()
                .uid("PA,RAMS")
                .optional()
                .build();

        assertThat(configSource.description(), is("TestingConfig[PA,RAMS]?"));
    }

    @Test
    public void testFormatDescriptionMandatoryNoParams() {
        TestingConfigSource configSource = TestingConfigSource.builder()
                .uid("")
                .build();

        assertThat(configSource.description(), is("TestingConfig[]"));
    }

    @Test
    public void testFormatDescriptionMandatoryWithParams() {
        TestingConfigSource configSource = TestingConfigSource.builder()
                .uid("PA,RAMS")
                .build();

        assertThat(configSource.description(), is("TestingConfig[PA,RAMS]"));
    }

    @Test
    public void testNoMapping() {
        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .build();
        ObjectNode objectNode = configSource.load().get().data();
        assertThat(objectNode.get("key1"), valueNode("aaa=bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testMediaTypeMapping() {
        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        when(context.findParser(MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .mediaTypeMapping(key -> key.name().equals("key1")
                        ? Optional.of(MEDIA_TYPE_TEXT_JAVA_PROPERTIES)
                        : Optional.empty())
                .build();

        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        ObjectNode objectNode = runtime.load().get();

        ConfigNode mappedNode = objectNode.get("key1");
        assertThat(mappedNode, instanceOf(ObjectNode.class));
        assertThat(((ObjectNode) mappedNode).get("aaa"), valueNode("bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testParserMapping() {
        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        when(context.findParser(MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .parserMapping(key -> key.name().equals("key1") ? Optional.of(ConfigParsers.properties()) : Optional.empty())
                .build();

        ObjectNode objectNode = new ConfigSourceRuntimeImpl(context, configSource).load().get();

        assertThat(((ObjectNode) objectNode.get("key1")).get("aaa"), valueNode("bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testMediaTypeAndParserMapping() {
        //parser has priority
        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        when(context.findParser(MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .thenReturn(Optional.of(new ConfigParser() { //NOT used parser
                    @Override
                    public Set<String> supportedMediaTypes() {
                        return Set.of(MEDIA_TYPE_TEXT_JAVA_PROPERTIES);
                    }

                    @Override
                    public ObjectNode parse(Content content) throws ConfigParserException {
                        return ObjectNode.builder().build();
                    }
                }));

        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .mediaTypeMapping(key -> key.name().equals("key1")
                        ? Optional.of(MEDIA_TYPE_TEXT_JAVA_PROPERTIES)
                        : Optional.empty())
                .parserMapping(key -> key.name().equals("key1") ? Optional.of(ConfigParsers.properties()) : Optional.empty())
                .build();

        ObjectNode objectNode = new ConfigSourceRuntimeImpl(context, configSource).load().get();
        assertThat(((ObjectNode) objectNode.get("key1")).get("aaa"), valueNode("bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testInitAll() {
        TestingConfigSource.Builder builder = TestingConfigSource.builder().config(Config.create(ConfigSources.create(
                Map.of("media-type-mapping.yaml", "application/x-yaml",
                       "media-type-mapping.password", "application/base64"))));

        //media-type-mapping
        Function<Config.Key, Optional<String>> mapping = builder.mediaTypeMapping().get();
        assertThat(mapping.apply(Config.Key.create("yaml")), is(Optional.of("application/x-yaml")));
        assertThat(mapping.apply(Config.Key.create("password")), is(Optional.of("application/base64")));
        assertThat(mapping.apply(Config.Key.create("unknown")), is(Optional.empty()));
    }

    @Test
    public void testInitNothing() {
        TestingConfigSource.Builder builder = TestingConfigSource.builder().config((Config.empty()));

        //media-type-mapping
        assertThat(builder.mediaTypeMapping(), is(Optional.empty()));
    }

    // simple compilation test to make sure the base config source implements all the required methods
    private static final class TestConfigSource extends AbstractConfigSource
            implements PollableSource<Integer>,
                       WatchableSource<Path>,
                       ParsableSource,
                       ConfigSource,
                       Source,
                       LazyConfigSource {

        protected TestConfigSource(AbstractConfigSourceBuilder<?, ?> builder) {
            super(builder);
        }

        /*
         Business methods must be implemented by the implementation
         */
        @Override
        public boolean isModified(Integer stamp) {
            return false;
        }

        @Override
        public Optional<ConfigNode> node(String key) {
            return Optional.empty();
        }

        @Override
        public Path target() {
            return Path.of(".");
        }

        @Override
        public Optional<ConfigParser.Content> load() throws ConfigException {
            return Optional.empty();
        }

        /*
        Configuration methods are implemented in base
         */
        @Override
        public Optional<ConfigParser> parser() {
            return super.parser();
        }

        @Override
        public Optional<String> mediaType() {
            return super.mediaType();
        }

        @Override
        public Optional<ChangeWatcher<Object>> changeWatcher() {
            return super.changeWatcher();
        }

        @Override
        public Optional<PollingStrategy> pollingStrategy() {
            return super.pollingStrategy();
        }
    }

    private static final class TestConfigSource2 extends AbstractConfigSource
            implements NodeConfigSource {

        protected TestConfigSource2(AbstractConfigSourceBuilder<?, ?> builder) {
            super(builder);
        }

        @Override
        public Optional<ConfigContent.NodeContent> load() throws ConfigException {
            return Optional.empty();
        }
    }
}
