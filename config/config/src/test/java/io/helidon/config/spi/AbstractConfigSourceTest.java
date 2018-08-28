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

package io.helidon.config.spi;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.config.Config;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static io.helidon.config.internal.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
import static io.helidon.config.spi.AbstractSource.Builder.DEFAULT_CHANGES_EXECUTOR;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link AbstractConfigSource}.
 */
public class AbstractConfigSourceTest {

    @Test
    public void testBuilderDefault() {
        TestingConfigSource.Builder builder = TestingConfigSource.builder();

        assertThat(builder.isMandatory(), is(true));
        assertThat(builder.getChangesExecutor(), is(DEFAULT_CHANGES_EXECUTOR));
        assertThat(builder.getChangesMaxBuffer(), is(Flow.defaultBufferSize()));
        assertThat(builder.getMediaTypeMapping(), is(nullValue()));
        assertThat(builder.getParserMapping(), is(nullValue()));
    }

    @Test
    public void testFormatDescriptionOptionalNoParams() {
        TestingConfigSource configSource = TestingConfigSource.builder().optional().build();

        assertThat(configSource.formatDescription(""), is("TestingConfig[]?*"));
    }

    @Test
    public void testFormatDescriptionOptionalNoParamsNoPolling() {
        TestingConfigSource configSource = TestingConfigSource.builderNoPolling().optional().build();

        assertThat(configSource.formatDescription(""), is("TestingConfig[]?"));
    }

    @Test
    public void testFormatDescriptionOptionalWithParams() {
        TestingConfigSource configSource = TestingConfigSource.builder().optional().build();

        assertThat(configSource.formatDescription("PA,RAMS"), is("TestingConfig[PA,RAMS]?*"));
    }

    @Test
    public void testFormatDescriptionMandatoryNoParams() {
        TestingConfigSource configSource = TestingConfigSource.builder().build();

        assertThat(configSource.formatDescription(""), is("TestingConfig[]*"));
    }

    @Test
    public void testFormatDescriptionMandatoryWithParams() {
        TestingConfigSource configSource = TestingConfigSource.builder().build();

        assertThat(configSource.formatDescription("PA,RAMS"), is("TestingConfig[PA,RAMS]*"));
    }

    @Test
    public void testBuilderCustomChanges() {
        Executor myExecutor = Runnable::run;
        TestingConfigSource.Builder builder = TestingConfigSource.builder()
                .changesExecutor(myExecutor)
                .changesMaxBuffer(1);

        assertThat(builder.getChangesExecutor(), is(myExecutor));
        assertThat(builder.getChangesMaxBuffer(), is(1));
    }

    @Test
    public void testNoMapping() {
        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .build();
        ObjectNode objectNode = configSource.load().get();
        assertThat(objectNode.get("key1"), valueNode("aaa=bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testMediaTypeMapping() {
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .mediaTypeMapping(key -> key.name().equals("key1") ? MEDIA_TYPE_TEXT_JAVA_PROPERTIES : null)
                .build();

        configSource.init(context);
        ObjectNode objectNode = configSource.load().get();
        assertThat(((ObjectNode) objectNode.get("key1")).get("aaa"), valueNode("bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testParserMapping() {
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        TestingConfigSource configSource = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder()
                                    .addValue("key1", "aaa=bbb")
                                    .addValue("key2", "ooo=ppp")
                                    .build())
                .parserMapping(key -> key.name().equals("key1") ? ConfigParsers.properties() : null)
                .build();

        configSource.init(context);
        ObjectNode objectNode = configSource.load().get();
        assertThat(((ObjectNode) objectNode.get("key1")).get("aaa"), valueNode("bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testMediaTypeAndParserMapping() {
        //parser has priority
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(MEDIA_TYPE_TEXT_JAVA_PROPERTIES))
                .thenReturn(Optional.of(new ConfigParser() { //NOT used parser
                    @Override
                    public Set<String> getSupportedMediaTypes() {
                        return CollectionsHelper.setOf(MEDIA_TYPE_TEXT_JAVA_PROPERTIES);
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
                .mediaTypeMapping(key -> key.name().equals("key1") ? MEDIA_TYPE_TEXT_JAVA_PROPERTIES : null)
                .parserMapping(key -> key.name().equals("key1") ? ConfigParsers.properties() : null)
                .build();

        configSource.init(context);
        ObjectNode objectNode = configSource.load().get();
        assertThat(((ObjectNode) objectNode.get("key1")).get("aaa"), valueNode("bbb"));
        assertThat(objectNode.get("key2"), valueNode("ooo=ppp"));
    }

    @Test
    public void testCompositeBuilderSupplierGetOnce() {
        AbstractConfigSource.Builder builder = new AbstractConfigSource.Builder(Void.class) {
            @Override
            public ConfigSource build() {
                return Optional::empty;
            }
        };

        ConfigSource configSource = builder.get();
        assertThat(configSource, sameInstance(builder.get()));
    }

    @Test
    public void testInitAll() {
        TestingConfigSource.TestingBuilder builder = TestingConfigSource.builder().init(Config.from(ConfigSources.from(
                CollectionsHelper.mapOf("media-type-mapping.yaml", "application/x-yaml",
                       "media-type-mapping.password", "application/base64"))));

        //media-type-mapping
        assertThat(builder.getMediaTypeMapping().apply(Config.Key.of("yaml")), is("application/x-yaml"));
        assertThat(builder.getMediaTypeMapping().apply(Config.Key.of("password")), is("application/base64"));
        assertThat(builder.getMediaTypeMapping().apply(Config.Key.of("unknown")), is(nullValue()));
    }

    @Test
    public void testInitNothing() {
        TestingConfigSource.TestingBuilder builder = TestingConfigSource.builder().init((Config.empty()));

        //media-type-mapping
        assertThat(builder.getMediaTypeMapping(), is(nullValue()));
    }

}
