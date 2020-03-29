/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.config.ConfigSources.DEFAULT_MAP_NAME;
import static io.helidon.config.ConfigSources.DEFAULT_PROPERTIES_NAME;
import static io.helidon.config.ConfigSources.prefixed;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

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
        ConfigSource empty = ConfigSources.empty();
        assertThat(empty, instanceOf(NodeConfigSource.class));
        NodeConfigSource emptyNodeSource = (NodeConfigSource) empty;
        assertThat(emptyNodeSource.load(), is(Optional.empty()));
    }

    @Test
    public void testEmptyIsAlwaysTheSameInstance() {
        assertThat(ConfigSources.empty(), sameInstance(ConfigSources.empty()));
    }

    @Test
    public void testFromConfig() {
        Map<String, String> source = Map.of("object.leaf", "value");

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
        assertThat(Config.create(prefixed("security", ConfigSources.create(Map.of("credentials.username", "libor"))))
                           .get("security.credentials.username")
                           .asString(),
                   is(ConfigValues.simpleValue("libor")));

    }

    @Test
    public void testPrefixDescription() {
        ConfigSource source = ConfigSources.create(Map.of("credentials.username", "libor")).build();
        assertThat(prefixed("security", source).description(), is("prefixed[security]:" + source.description()));
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

        List<ConfigSource> sources = MetaConfig.configSources(Config.create(meta1));
        assertThat(sources, hasSize(1));

        ConfigSource source = sources.get(0);
        source.init(mock(ConfigContext.class));
        ObjectNode objectNode = ((NodeConfigSource) source).load().get().data();
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
    }

    @Test
    public void testSystemPropertiesSourceType() {
        ConfigSource source = ConfigSources.systemProperties().build();
        assertThat(source, is(instanceOf(ConfigSources.SystemPropertiesConfigSource.class)));
        assertThat(source.description(), is("SystemPropertiesConfig[]"));
    }

    @Test
    public void testEnvironmentVariablesSourceType() {
        ConfigSource source = ConfigSources.environmentVariables();
        assertThat(source, is(instanceOf(ConfigSources.EnvironmentVariablesConfigSource.class)));
        assertThat(source.description(), is("EnvironmentVariablesConfig[]"));
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
    public void testEnvironmentVariableAliases() {
        Config config = Config.builder()
                              .disableSystemPropertiesSource()
                              .build();

        assertValue("simple", "unmapped-env-value", config);

        assertValue("_underscore", "mapped-env-value", config);
        assertValue("/underscore", "mapped-env-value", config);

        assertValue("FOO_BAR", "mapped-env-value", config);
        assertValue("foo.bar", "mapped-env-value", config);
        assertValue("foo/bar", "mapped-env-value", config);
        assertValue("foo#bar", "mapped-env-value", config);

        assertValue("com_ACME_size", "mapped-env-value", config);
        assertValue("com.ACME.size", "mapped-env-value", config);
        assertValue("com!ACME@size", "mapped-env-value", config);
        assertValue("com#ACME$size", "mapped-env-value", config);
        assertValue("com/ACME/size", "mapped-env-value", config);

        assertValue("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value", config);
        assertValue("SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE", "mapped-env-value", config);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", config);
        assertValue("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value", config);
    }

    @Test
    public void testPrecedence() {

        // NOTE: This code should be kept in sync with MpcSourceEnvironmentVariablesTest.testPrecedence(), as we want
        //       SE and MP to be as symmetrical as possible. There are two differences:
        //
        //       1. This is now resolved - SE and MP have the same behavior related
        //          to System proprerties and Environment variables
        //
        //       2. An upper-to-lower case mapping is performed in SE but is not in MP (correctly, per spec). This is a
        //          consequence of the static mapping (see EnvironmentVariables.expand()) required in SE to preserve
        //          precedence of aliased env vars during the merge step (see MergingStrategy.merge()). Fixing this
        //          is much harder but probably not very important.
        //
        //       The assertions below that differ are marked with a "DIFFERENCE" N comment.

        System.setProperty("com.ACME.size", "sys-prop-value");
        Map<String, String> appValues = Map.of("app.key", "app-value",
                                              "com.ACME.size", "app-value",
                                              "server.executor-service.max-pool-size", "app-value");

        ConfigSource appSource = ConfigSources.create(appValues).build();

        // Application source only.
        // Key mapping should NOT occur.

        Config appOnly = Config.builder(appSource)
                               .disableEnvironmentVariablesSource()
                               .disableSystemPropertiesSource()
                               .build();

        assertValue("app.key", "app-value", appOnly);
        assertValue("com.ACME.size", "app-value", appOnly);
        assertValue("server.executor-service.max-pool-size", "app-value", appOnly);

        assertNoValue("com.acme.size", appOnly);
        assertNoValue("com/ACME/size", appOnly);
        assertNoValue("server/executor-service/max-pool-size", appOnly);


        // Application and system property sources.
        // System properties should take precedence over application values.
        // Key mapping should NOT occur.

        Config appAndSys = Config.builder(appSource)
                              .disableEnvironmentVariablesSource()
                              .build();

        assertValue("app.key", "app-value", appAndSys);
        assertValue("com.ACME.size", "sys-prop-value", appAndSys);
        assertValue("server.executor-service.max-pool-size", "app-value", appAndSys);

        assertNoValue("com.acme.size", appOnly);
        assertNoValue("com/ACME/size", appAndSys);
        assertNoValue("server/executor-service/max-pool-size", appAndSys);


        // Application and environment variable sources.
        // Environment variables should take precedence over application values.
        // Key mapping SHOULD occur.

        Config appAndEnv = Config.builder(appSource)
                                 .disableSystemPropertiesSource()
                                 .build();

        assertValue("app.key", "app-value", appAndEnv);
        assertValue("com.ACME.size", "mapped-env-value", appAndEnv);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", appAndEnv);

        assertValue("com.acme.size","mapped-env-value", appAndEnv);  // DIFFERENCE 2: should not exist
        assertValue("com/ACME/size", "mapped-env-value", appAndEnv);
        assertValue("server/executor-service/max-pool-size", "mapped-env-value", appAndEnv);


        // Application, system property and environment variable sources.
        // System properties should take precedence over environment variables.
        // Environment variables should take precedence over application values.
        // Key mapping SHOULD occur.

        Config appSysAndEnv = Config.builder(appSource)
                                    .build();

        assertValue("app.key", "app-value", appSysAndEnv);
        assertValue("com.ACME.size", "sys-prop-value", appSysAndEnv);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", appSysAndEnv);

        assertValue("com.acme.size","mapped-env-value", appAndEnv);  // DIFFERENCE 2: should not exist
        assertValue("com/ACME/size", "mapped-env-value", appSysAndEnv);
        assertValue("server/executor-service/max-pool-size", "mapped-env-value", appSysAndEnv);
    }

    static void assertValue(final String key, final String expectedValue, final Config config) {
        assertThat(config.get(key).asString().get(), is(expectedValue));
    }

    static void assertNoValue(final String key, final Config config) {
        assertThat(config.get(key).exists(), is(false));
    }
}
