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

import java.io.StringReader;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.ValueNodeMatcher;
import io.helidon.config.internal.PropertiesConfigParser;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.core.Is;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ConfigSource}.
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class ConfigSourceTest {

    public static final String TEST_ENV_VAR_NAME = "CONFIG_SOURCE_TEST_PROPERTY";
    public static final String TEST_ENV_VAR_VALUE = "This Is My ENV VARS Value.";
    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-ConfigSourceTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";

    @Test
    public void testFromObjectNodeDescription() {
        ConfigSource configSource = ConfigSources.from(ObjectNode.empty());

        assertThat(configSource.description(), is("InMemoryConfig[ObjectNode]"));
    }

    @Test
    public void testFromObjectNodeLoad() {
        ConfigSource configSource = ConfigSources.from(ObjectNode.empty());

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.load().get().entrySet(), is(empty()));
    }

    @Test
    public void testFromReadableDescription() {
        ConfigSource configSource = ConfigSources
                .from(new StringReader("aaa=bbb"), PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        assertThat(configSource.description(), is("InMemoryConfig[Readable]"));
    }

    @Test
    public void testFromReadableLoad() {
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(any())).thenReturn(Optional.of(ConfigParsers.properties()));

        ConfigSource configSource = ConfigSources
                .from(new StringReader("aaa=bbb"), PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        configSource.init(context);
        assertThat(configSource.load().get().get("aaa"), ValueNodeMatcher.valueNode("bbb"));
    }

    @ExtendWith(RestoreSystemPropertiesExt.class)
    @Test
    public void testFromTextDescription() {
        ConfigSource configSource = ConfigSources.from("aaa=bbb", PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        assertThat(configSource.description(), is("InMemoryConfig[String]"));
    }

    @Test
    public void testFromTextLoad() {
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(
                argThat(PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES::equals)))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        ConfigSource configSource = ConfigSources.from("aaa=bbb", PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

        configSource.init(context);
        assertThat(configSource.load().get().get("aaa"), ValueNodeMatcher.valueNode("bbb"));
    }

    @Test
    public void testFromSystemPropertiesDescription() {
        ConfigSource configSource = ConfigSources.systemProperties();

        assertThat(configSource.description(), is("MapConfig[sys-props]"));
    }

    @Test
    public void testFromSystemProperties() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        ConfigSource configSource = ConfigSources.systemProperties();

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.load().get().get(TEST_SYS_PROP_NAME),
                   ValueNodeMatcher.valueNode(TEST_SYS_PROP_VALUE));
    }

    @Test
    public void testFromEnvironmentVariablesDescription() {
        ConfigSource configSource = ConfigSources.environmentVariables();

        assertThat(configSource.description(), is("MapConfig[env-vars]"));
    }

    @Test
    public void testFromEnvironmentVariables() {
        ConfigSource configSource = ConfigSources.environmentVariables();

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.load().get().get(TEST_ENV_VAR_NAME),
                   ValueNodeMatcher.valueNode(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testChangesDefault() throws InterruptedException {
        ConfigSource configSource = Optional::empty;

        CountDownLatch onComplete = new CountDownLatch(1);
        configSource.changes().subscribe(new Flow.Subscriber<Optional<ObjectNode>>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Optional<ObjectNode> item) {
                fail("onNext should not be invoked");
            }

            @Override
            public void onError(Throwable throwable) {
                fail("onError should not be invoked");
            }

            @Override
            public void onComplete() {
                onComplete.countDown();
            }
        });
        assertThat(onComplete.await(10, TimeUnit.MILLISECONDS), Is.is(true));
    }

}
