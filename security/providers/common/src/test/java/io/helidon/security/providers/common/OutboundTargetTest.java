/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.common;

import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link io.helidon.security.providers.common.OutboundTarget}.
 */
public class OutboundTargetTest {
    @Test
    public void testAnyMatchNulls() {
        OutboundTarget instance = OutboundTarget.builder("name").build();

        assertThat(instance.matches("http", "localhost", null, null), is(true));
    }

    @Test
    public void testAnyMatchHosts() {
        OutboundTarget instance = OutboundTarget.builder("name").addTransport("https").build();

        assertThat(instance.matches("http", "localhost", null, null), is(false));
        assertThat(instance.matches("https", "localhost", null, null), is(true));
        assertThat(instance.matches("https", "192.168.1.1", null, null), is(true));
        assertThat(instance.matches("https", "www.google.com", null, null), is(true));
    }

    @Test
    public void testAnyMatchProtocol() {
        OutboundTarget instance = OutboundTarget.builder("name").addHost("localhost").build();

        assertThat(instance.matches("https", "192.168.1.1", null, null), is(false));
        assertThat(instance.matches("http", "localhost", null, null), is(true));
        assertThat(instance.matches("https", "localhost", null, null), is(true));
        assertThat(instance.matches("jms", "localhost", null, null), is(true));
        assertThat(instance.matches("t3", "localhost", null, null), is(true));
        assertThat(instance.matches("iiop", "localhost", null, null), is(true));
    }

    @Test
    public void testAnyMatchMethod() {
        OutboundTarget instance = OutboundTarget.builder("name").addHost("localhost").build();

        assertThat(instance.matches(null, "192.168.1.1", null, "GET"), is(false));
        assertThat(instance.matches(null, "localhost", null, "PUT"), is(true));
        assertThat(instance.matches(null, "localhost", null, "POST"), is(true));
        assertThat(instance.matches(null, "localhost", null, "PATCH"), is(true));
        assertThat(instance.matches(null, "localhost", null, "CUSTOM"), is(true));
        assertThat(instance.matches(null, "localhost", null, "DELETE"), is(true));
    }

    @Test
    public void testExactValues() {
        OutboundTarget instance = OutboundTarget
                .builder("name")
                .addTransport("http")
                .addTransport("https")
                .addHost("localhost")
                .addHost("192.168.1.14")
                .addHost("10.17.17.1")
                .addMethod("PUT")
                .addMethod("POST")
                .addMethod("DELETE")
                .build();

        assertThat(instance.matches("http", "localhost", null, "PUT"), is(true));
        assertThat(instance.matches("http", "192.168.1.14", null, "POST"), is(true));
        assertThat(instance.matches("http", "10.17.17.1", null, "DELETE"), is(true));
        assertThat(instance.matches("https", "localhost", null, "PUT"), is(true));
        assertThat(instance.matches("https", "192.168.1.14", null, "POST"), is(true));
        assertThat(instance.matches("https", "10.17.17.1", null, "DELETE"), is(true));

        assertThat(instance.matches("http", "192.168.1.13", null, null), is(false));
        assertThat(instance.matches("iiop", "localhost", null, null), is(false));
        assertThat(instance.matches("http", "localhost", null, "GET"), is(false));
        assertThat(instance.matches("http", "192.168.1.14", null, null), is(false));
    }

    @Test
    public void testConfiguredKnownMethodsMatchOriginalAndUppercase() {
        Map.of("get", "GET", "Post", "POST", "query", "QUERY", "Put", "PUT", "delete", "DELETE", "Head", "HEAD",
               "patch", "PATCH", "Options", "OPTIONS", "trace", "TRACE", "Connect", "CONNECT")
                .forEach((configured, uppercase) -> {
                    OutboundTarget instance = OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                            "name", "test",
                            "methods.0", configured))));

                    assertThat("Uppercase alias for " + configured, instance.matches(null, null, null, uppercase), is(true));
                    assertThat("Original method " + configured, instance.matches(null, null, null, configured), is(true));
                    assertThat("Unconfigured custom method for " + configured,
                               instance.matches(null, null, null, "Follow"), is(false));
                });
    }

    @Test
    public void testConfiguredNonUppercaseMethodWarnsWithMigrationGuidance() {
        var logger = Logger.getLogger(OutboundTarget.class.getName());
        Level previousLevel = logger.getLevel();
        var handler = mock(Handler.class);
        logger.addHandler(handler);
        try {
            logger.setLevel(Level.ALL);
            OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                    "security.outbound.0.name", "test",
                    "security.outbound.0.methods.0", "PoSt"))).get("security.outbound.0"));
            OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                    "name", "test",
                    "methods.0", "POST",
                    "methods.1", "Follow"))));
            OutboundTarget.builder("test").addMethod("get").build();

            ArgumentCaptor<LogRecord> records = ArgumentCaptor.forClass(LogRecord.class);
            verify(handler).publish(records.capture());
            verifyNoMoreInteractions(handler);
            LogRecord warning = records.getValue();
            assertThat(warning.getLevel(), is(Level.WARNING));
            assertThat(new SimpleFormatter().formatMessage(warning),
                       allOf(containsString("security.outbound.0.methods"),
                             containsString("HTTP method \"PoSt\""),
                             containsString("Use \"POST\" instead"),
                             containsString("will be removed in a future major version"),
                             containsString("will then be matched case-sensitively")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    public void testConfiguredUppercaseMethodUsesExactCase() {
        OutboundTarget instance = OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                "name", "test",
                "methods.0", "GET"))));

        assertThat("Uppercase method", instance.matches(null, null, null, "GET"), is(true));
        assertThat("Lowercase method", instance.matches(null, null, null, "get"), is(false));
        assertThat("Mixed-case method", instance.matches(null, null, null, "Get"), is(false));
    }

    @Test
    public void testConfiguredCustomMethodsUseExactCase() {
        Map.of("Follow", "FOLLOW", "po\u017ft", "POST", "opt\u0131ons", "OPTIONS")
                .forEach((configured, uppercase) -> {
                    OutboundTarget instance = OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                            "name", "test",
                            "methods.0", configured))));

                    assertThat("Custom method " + configured, instance.matches(null, null, null, configured), is(true));
                    assertThat("No uppercase alias for " + configured,
                               instance.matches(null, null, null, uppercase), is(false));
                    assertThat("No lowercase custom alias", instance.matches(null, null, null, "follow"), is(false));
                });
    }

    @Test
    public void testConfiguredDuplicateMethods() {
        OutboundTarget instance = OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                "name", "test",
                "methods.0", "get",
                "methods.1", "GET",
                "methods.2", "get"))));

        assertThat("Uppercase method", instance.matches(null, null, null, "GET"), is(true));
        assertThat("Original method", instance.matches(null, null, null, "get"), is(true));
        assertThat("Unconfigured case", instance.matches(null, null, null, "Get"), is(false));
    }

    @Test
    public void testConfiguredMethodsDefaultAndLiteralAsterisk() {
        OutboundTarget defaultTarget = OutboundTarget.create(Config.create(ConfigSources.create(Map.of("name", "test"))));
        OutboundTarget literalAsterisk = OutboundTarget.create(Config.create(ConfigSources.create(Map.of(
                "name", "test",
                "methods.0", "*"))));

        assertThat("Unrestricted default", defaultTarget.matches(null, null, null, "Follow"), is(true));
        assertThat("Default without a method", defaultTarget.matches(null, null, null, null), is(true));
        assertThat("Literal asterisk", literalAsterisk.matches(null, null, null, "*"), is(true));
        assertThat("Asterisk does not match all methods", literalAsterisk.matches(null, null, null, "GET"), is(false));
    }

    @Test
    public void testProgrammaticMethodUsesExactCase() {
        OutboundTarget instance = OutboundTarget.builder("test")
                .addMethod("get")
                .build();

        assertThat("Differently cased method", instance.matches(null, null, null, "GET"), is(false));
        assertThat("Exact method", instance.matches(null, null, null, "get"), is(true));
    }

    @Test
    public void testConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("targets_with_default.conf"))
                .build();

        config = config.get("security-provider.outbound")
                .asList(Config.class)
                .get()
                .get(0);

        OutboundTarget instance = OutboundTarget
                .builder("name")
                .addTransport("http")
                .addTransport("https")
                .addHost("localhost")
                .addHost("192.168.1.14")
                .addHost("10.17.17.1")
                .config(config)
                .build();

        assertThat(instance.getConfig().isPresent(), is(true));
        assertThat(instance.getConfig().get(), sameInstance(config));
    }

    @Test
    public void testMatchingGlob() {
        OutboundTarget instance = OutboundTarget
                .builder("name")
                .addTransport("http")
                .addTransport("https")
                .addHost("192.*.1.14")
                .addHost("*.google.com")
                .build();

        assertThat(instance.matches("http", "192.168.1.14", null, null), is(true));
        assertThat(instance.matches("http", "192.12.1.14", null, null), is(true));
        assertThat(instance.matches("http", "192.168.1.15", null, null), is(false));

        assertThat(instance.matches("http", "calendar.google.com", null, null), is(true));
        assertThat(instance.matches("http", "my.calendar.google.com", null, null), is(true));

        assertThat(instance.matches("http", "calendar.google.org", null, null), is(false));
    }
}
