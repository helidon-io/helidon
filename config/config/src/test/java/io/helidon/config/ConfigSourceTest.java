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

import io.helidon.config.BuilderImpl.ConfigContextImpl;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.config.TestHelper.toInputStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link io.helidon.config.spi.ConfigSource}.
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class ConfigSourceTest {

    public static final String TEST_ENV_VAR_NAME = "CONFIG_SOURCE_TEST_PROPERTY";
    public static final String TEST_ENV_VAR_VALUE = "This Is My ENV VARS Value.";
    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-ConfigSourceTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";

    @Test
    public void testFromObjectNodeDescription() {
        ConfigSource configSource = ConfigSources.create(ObjectNode.empty());

        assertThat(configSource.description(), is("NodeInMemory[ObjectNode]"));
    }

    @Test
    public void testFromObjectNodeLoad() {
        ConfigSource configSource = ConfigSources.create(ObjectNode.empty());

        ConfigContextImpl context = mock(ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);
        assertThat(runtime.load().get().entrySet(), is(empty()));
    }

    @Test
    public void testFromReadableDescription() {
        ConfigSource configSource = ConfigSources
                .create(toInputStream("aaa=bbb"),
                        PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        assertThat(configSource.description(), is("ParsableInMemory[Readable]"));
    }

    @Test
    public void testFromReadableLoad() {
        ConfigSource configSource = ConfigSources
                .create(toInputStream("aaa=bbb"),
                        PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        ConfigContextImpl context = mock(ConfigContextImpl.class);
        when(context.findParser(any())).thenReturn(Optional.of(ConfigParsers.properties()));
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        assertThat(runtime.load().get().get("aaa"), ValueNodeMatcher.valueNode("bbb"));
    }

    @ExtendWith(RestoreSystemPropertiesExt.class)
    @Test
    public void testFromTextDescription() {
        ConfigSource configSource = ConfigSources.create("aaa=bbb", PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        assertThat(configSource.description(), is("ParsableInMemory[String]"));
    }

    @Test
    public void testFromTextLoad() {
        ConfigContextImpl context = mock(ConfigContextImpl.class);
        when(context.findParser(
                argThat(PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES::equals)))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        ConfigSource configSource = ConfigSources.create("aaa=bbb", PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);
        assertThat(runtime.load().get().get("aaa"), ValueNodeMatcher.valueNode("bbb"));
    }

    @Test
    public void testFromSystemProperties() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        ConfigSource configSource = ConfigSources.systemProperties().build();

        ConfigContextImpl context = mock(ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        assertThat(runtime.load().get().get(TEST_SYS_PROP_NAME),
                   ValueNodeMatcher.valueNode(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testFromEnvironmentVariablesDescription() {
        ConfigSource configSource = ConfigSources.environmentVariables();

        assertThat(configSource.description(), is("EnvironmentVariablesConfig[]"));
    }

    @Test
    public void testFromEnvironmentVariables() {
        ConfigSource configSource = ConfigSources.environmentVariables();

        ConfigContextImpl context = mock(ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        assertThat(runtime.load().get().get(TEST_ENV_VAR_NAME),
                   ValueNodeMatcher.valueNode(TEST_ENV_VAR_VALUE));
    }
}
