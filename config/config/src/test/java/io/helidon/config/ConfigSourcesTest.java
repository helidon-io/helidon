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

package io.helidon.config;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

import static io.helidon.config.ConfigSources.from;
import static io.helidon.config.ConfigSources.prefixed;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ConfigSources}.
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class ConfigSourcesTest {

    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-ConfigSourcesTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";

    @Test
    public void testEmptyDescription() {
        assertThat(ConfigSources.empty().description(), is("Empty"));
    }

    @Test
    public void testEmptyLoad() {
        assertThat(ConfigSources.empty().load(), is(Optional.empty()));
    }

    @Test
    public void testEmptyIsAlwaysTheSameInstance() {
        assertThat(ConfigSources.empty(), sameInstance(ConfigSources.empty()));
    }

    @Test
    public void testFromConfig() throws MalformedURLException, InterruptedException {
        Map<String, String> source = CollectionsHelper.mapOf("object.leaf", "value");

        ConfigSource originConfigSource = from(source).build();
        Config originConfig = Config.withSources(originConfigSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigSource configSource = from(originConfig);
        Config copy = Config.withSources(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(ConfigDiff.from(originConfig, copy).isEmpty(), is(true));
    }

    @Test
    public void testPrefix() {
        assertThat(Config.from(prefixed("security", from(CollectionsHelper.mapOf("credentials.username", "libor"))))
                           .get("security.credentials.username")
                           .asString(),
                   is("libor"));

    }

    @Test
    public void testPrefixDescription() {
        ConfigSource source = from(CollectionsHelper.mapOf("credentials.username", "libor")).build();
        assertThat(prefixed("security", source).description(), is("prefixed[security]:" + source.description()));
    }

    @Test
    public void testMapBuilderSupplierGetOnce() {
        ConfigSources.MapBuilder builder = from(CollectionsHelper.mapOf());

        ConfigSource configSource = builder.get();
        assertThat(configSource, sameInstance(builder.get()));
    }

    @Test
    public void testCompositeBuilderSupplierGetOnce() {
        ConfigSources.CompositeBuilder builder = ConfigSources.from();

        ConfigSource configSource = builder.get();
        assertThat(configSource, sameInstance(builder.get()));
    }

    @Test
    public void testLoadNoSource() {
        ConfigSource source = ConfigSources.load().build();
        source.init(mock(ConfigContext.class));

        assertThat(source.load(), is(Optional.empty()));
    }

    @Test
    public void testLoadSingleSource() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        ConfigSource meta1 = ConfigSources.from(
                ObjectNode.builder()
                        .addList("sources", ListNode.builder()
                                .addObject(ObjectNode.builder()
                                                   .addValue("type", "system-properties")
                                                   .build())
                                .build())
                        .build());

        ConfigSource source = ConfigSources.load(meta1).build();
        source.init(mock(ConfigContext.class));
        ObjectNode objectNode = source.load().get();
        assertThat(objectNode.get(TEST_SYS_PROP_NAME), valueNode(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testLoadMultipleSource() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        //meta1's `sources` property is used
        ConfigSource meta1 = ConfigSources.from(
                ObjectNode.builder()
                        .addList("sources", ListNode.builder()
                                .addObject(ObjectNode.builder()
                                                   .addValue("type", "classpath")
                                                   .addObject("properties", ObjectNode.builder()
                                                           .addValue("resource", "io/helidon/config/application.properties")
                                                           .build())
                                                   .build())
                                .build())
                        .build());

        //meta2's `sources` property is ignored
        ConfigSource meta2 = ConfigSources.from(
                ObjectNode.builder()
                        .addList("sources", ListNode.builder()
                                .addObject(ObjectNode.builder()
                                                   .addValue("type", "system-properties")
                                                   .build())
                                .build())
                        .build());

        //meta1 has precedence over meta2
        ConfigSource source = ConfigSources.load(meta1, meta2).build();
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser("text/x-java-properties")).thenReturn(Optional.of(ConfigParsers.properties()));

        source.init(context);

        ObjectNode objectNode = source.load().get();
        assertThat(objectNode.get(TEST_SYS_PROP_NAME), is(nullValue()));
        assertThat(objectNode.get("key1"), valueNode("val1"));
    }

}
