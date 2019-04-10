/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Properties;

import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.common.CollectionsHelper.mapOf;
import static io.helidon.config.ConfigSources.DEFAULT_MAP_NAME;
import static io.helidon.config.ConfigSources.DEFAULT_PROPERTIES_NAME;
import static io.helidon.config.ConfigSources.ENV_VARS_NAME;
import static io.helidon.config.ConfigSources.SYS_PROPS_NAME;
import static io.helidon.config.ConfigSources.prefixed;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
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
    public void testFromConfig() {
        Map<String, String> source = mapOf("object.leaf", "value");

        ConfigSource originConfigSource = ConfigSources.create(source).build();
        Config originConfig = Config.builder(originConfigSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigSource configSource = ConfigSources.create(originConfig);
        Config copy = Config.builder(configSource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(ConfigDiff.from(originConfig, copy).isEmpty(), is(true));
    }

    @Test
    public void testPrefix() {
        assertThat(Config.create(prefixed("security", ConfigSources.create(mapOf("credentials.username", "libor"))))
                           .get("security.credentials.username")
                           .asString(),
                   is(ConfigValues.simpleValue("libor")));

    }

    @Test
    public void testPrefixDescription() {
        ConfigSource source = ConfigSources.create(mapOf("credentials.username", "libor")).build();
        assertThat(prefixed("security", source).description(), is("prefixed[security]:" + source.description()));
    }

    @Test
    public void testMapBuilderSupplierGetOnce() {
        ConfigSources.MapBuilder builder = ConfigSources.create(mapOf());

        ConfigSource configSource = builder.get();
        assertThat(configSource, sameInstance(builder.get()));
    }

    @Test
    public void testCompositeBuilderSupplierGetOnce() {
        ConfigSources.CompositeBuilder builder = ConfigSources.create();

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

        ConfigSource meta1 = ConfigSources.create(
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
        ConfigSource meta1 = ConfigSources.create(
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
        ConfigSource meta2 = ConfigSources.create(
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

    @Test
    public void testSystemPropertiesSourceName() {
        assertThat(ConfigSources.systemProperties().get().description(), containsString(SYS_PROPS_NAME));
    }

    @Test
    public void testEnvironmentVariablesSourceName() {
        assertThat(ConfigSources.environmentVariables().get().description(), containsString(ENV_VARS_NAME));
    }

    @Test
    public void testDefaultMapSourceName() {
        assertThat(ConfigSources.create(emptyMap()).get().description(), containsString(DEFAULT_MAP_NAME));
    }

    @Test
    public void testDefaultPropertiesSourceName() {
        assertThat(ConfigSources.create(new Properties()).get().description(), containsString(DEFAULT_PROPERTIES_NAME));
    }

    @Test
    public void testEnvironmentVariablesSourceMappings() {
        Config config = Config.builder()
                              .disableSystemPropertiesSource()
                              .build();

        assertValue("simple", "unmapped-env-value", config);

        assertValue("_unmapped", "unmapped-env-value", config);
        assertThat(config.get(".unmapped").exists(), is(false));

        assertValue("com_ACME_size", "mapped-env-value", config);
        assertValue("com.ACME.size", "mapped-env-value", config);
        assertValue("com.acme.size", "mapped-env-value", config);

        assertValue("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value", config);
        assertValue("SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE", "mapped-env-value", config);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", config);
    }

    @Test
    public void testEnvironmentVariableOverrides() {
        Map<String, String> appValues = mapOf("app.key", "app-value",
                                              "com.acme.size", "app-value",
                                              "server.executor-service.max-pool-size", "app-value");

        ConfigSource appSource = ConfigSources.create(appValues).build();

        Config appOnly = Config.builder(appSource)
                               .disableEnvironmentVariablesSource()
                               .disableSystemPropertiesSource()
                               .build();

        assertValue("app.key", "app-value", appOnly);
        assertValue("com.acme.size", "app-value", appOnly);
        assertValue("server.executor-service.max-pool-size", "app-value", appOnly);

        Config merged = Config.builder(appSource)
                              .build();

        assertValue("app.key", "app-value", merged);
        assertValue("com.acme.size", "mapped-env-value", merged);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", merged);
    }

    static void assertValue(final String key, final String expectedValue, final Config config) {
        assertThat(config.get(key).asString().get(), is(expectedValue));
    }
}
