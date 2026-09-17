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

package io.helidon.security.providers.httpsign;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.helidon.security.providers.httpsign.SignedHeadersConfig.REQUEST_TARGET;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit test for {@link SignedHeadersConfig}.
 */
class CurrentSignedHeadersConfigTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create().get("current");
    }

    @Test
    void testFromConfig() {
        SignedHeadersConfig shc = config.get("http-signatures.sign-headers").as(SignedHeadersConfig::create).get();

        testThem(shc);
    }

    @Test
    void testExactMethodFromConfig() {
        SignedHeadersConfig shc = SignedHeadersConfig.create(Config.just("""
                sign-headers:
                  - always: [date]
                  - method: PATCH
                    always: [date, x-exact]
                """, MediaTypes.APPLICATION_YAML).get("sign-headers"));

        assertThat(shc.headers("patch"), CoreMatchers.not(CoreMatchers.hasItem("x-exact")));
        assertThat(shc.headers("PATCH"), CoreMatchers.hasItem("x-exact"));
    }

    @Test
    void testKnownMethodsFromConfigMatchOriginalAndUppercase() {
        Map.of("get", "GET", "Post", "POST", "query", "QUERY", "Put", "PUT", "delete", "DELETE", "Head", "HEAD",
               "patch", "PATCH", "Options", "OPTIONS", "trace", "TRACE", "Connect", "CONNECT")
                .forEach((configured, uppercase) -> {
                    SignedHeadersConfig shc = SignedHeadersConfig.create(Config.just("""
                            sign-headers:
                              - always: [date]
                              - method: %s
                                always: [x-method]
                                if-present: [authorization]
                            """.formatted(configured), MediaTypes.APPLICATION_YAML).get("sign-headers"));

                    assertThat("Original method " + configured, shc.headers(configured), is(List.of("x-method")));
                    assertThat("Uppercase alias for " + configured, shc.headers(uppercase), is(List.of("x-method")));
                    assertThat("Optional headers for " + configured,
                               shc.headers(uppercase, Map.of("authorization", List.of("signature"))),
                               is(List.of("x-method", "authorization")));
                    assertThat("Default for unconfigured custom method", shc.headers("Follow"), is(List.of("date")));
                });
    }

    @Test
    void testConfiguredNonUppercaseMethodWarnsWithMigrationGuidance() {
        var logger = Logger.getLogger(SignedHeadersConfig.class.getName());
        Level previousLevel = logger.getLevel();
        var handler = mock(Handler.class);
        logger.addHandler(handler);
        try {
            logger.setLevel(Level.ALL);
            SignedHeadersConfig.create(Config.just("""
                    security:
                      http-signatures:
                        sign-headers:
                          - method: PoSt
                            always: [date]
                    """, MediaTypes.APPLICATION_YAML).get("security.http-signatures.sign-headers"));
            SignedHeadersConfig.create(Config.just("""
                    sign-headers:
                      - method: POST
                        always: [date]
                      - method: Follow
                        always: [date]
                    """, MediaTypes.APPLICATION_YAML).get("sign-headers"));
            SignedHeadersConfig.builder()
                    .config("get", SignedHeadersConfig.HeadersConfig.create(List.of("date")))
                    .build();

            ArgumentCaptor<LogRecord> records = ArgumentCaptor.forClass(LogRecord.class);
            verify(handler).publish(records.capture());
            verifyNoMoreInteractions(handler);
            LogRecord warning = records.getValue();
            assertThat(warning.getLevel(), is(Level.WARNING));
            assertThat(new SimpleFormatter().formatMessage(warning),
                       allOf(containsString("security.http-signatures.sign-headers.0.method"),
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
    void testCustomMethodsFromConfigUseExactCase() {
        Map.of("Follow", "FOLLOW", "po\u017ft", "POST", "opt\u0131ons", "OPTIONS")
                .forEach((configured, uppercase) -> {
                    SignedHeadersConfig shc = SignedHeadersConfig.create(Config.just("""
                            sign-headers:
                              - always: [date]
                              - method: %s
                                always: [x-custom]
                            """.formatted(configured), MediaTypes.APPLICATION_YAML).get("sign-headers"));

                    assertThat("Custom method " + configured, shc.headers(configured), is(List.of("x-custom")));
                    assertThat("No uppercase alias for " + configured, shc.headers(uppercase), is(List.of("date")));
                    assertThat("No lowercase custom alias", shc.headers("follow"), is(List.of("date")));
                });
    }

    @Test
    void testExplicitUppercaseConfigTakesPrecedenceInEitherOrder() {
        for (List<String> methods : List.of(List.of("get", "GET"), List.of("GET", "get"))) {
            SignedHeadersConfig shc = SignedHeadersConfig.create(Config.create(ConfigSources.create(Map.of(
                    "sign-headers.0.method", methods.get(0),
                    "sign-headers.0.always.0", "x-" + methods.get(0),
                    "sign-headers.1.method", methods.get(1),
                    "sign-headers.1.always.0", "x-" + methods.get(1)))).get("sign-headers"));

            assertThat("Explicit uppercase in " + methods, shc.headers("GET"), is(List.of("x-GET")));
            assertThat("Exact lowercase in " + methods, shc.headers("get"), is(List.of("x-get")));
            assertThat("Unconfigured case in " + methods, shc.headers("Get"), is(List.of()));
        }
    }

    @Test
    void testLastNonUppercaseConfigSuppliesUppercaseFallback() {
        for (List<String> methods : List.of(List.of("get", "Get"), List.of("Get", "get"))) {
            SignedHeadersConfig shc = SignedHeadersConfig.create(Config.create(ConfigSources.create(Map.of(
                    "sign-headers.0.method", methods.get(0),
                    "sign-headers.0.always.0", "x-first",
                    "sign-headers.1.method", methods.get(1),
                    "sign-headers.1.always.0", "x-last"))).get("sign-headers"));

            assertThat("First exact method in " + methods, shc.headers(methods.get(0)), is(List.of("x-first")));
            assertThat("Last exact method in " + methods, shc.headers(methods.get(1)), is(List.of("x-last")));
            assertThat("Last uppercase fallback in " + methods, shc.headers("GET"), is(List.of("x-last")));
        }
    }

    @Test
    void testDuplicateConfiguredMethodsUseLastEntry() {
        SignedHeadersConfig shc = SignedHeadersConfig.create(Config.just("""
                sign-headers:
                  - method: get
                    always: [x-first]
                  - method: get
                    always: [x-last]
                """, MediaTypes.APPLICATION_YAML).get("sign-headers"));

        assertThat("Last exact method", shc.headers("get"), is(List.of("x-last")));
        assertThat("Last uppercase fallback", shc.headers("GET"), is(List.of("x-last")));
        assertThat("Default remains empty", shc.headers("POST"), is(List.of()));
    }

    @Test
    void testProgrammaticMethodsUseExactCase() {
        SignedHeadersConfig shc = SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig.create(List.of("date")))
                .config("get", SignedHeadersConfig.HeadersConfig.create(List.of("x-lowercase")))
                .config("Follow", SignedHeadersConfig.HeadersConfig.create(List.of("x-custom")))
                .build();

        assertThat("Exact lowercase method", shc.headers("get"), is(List.of("x-lowercase")));
        assertThat("No programmatic uppercase alias", shc.headers("GET"), is(List.of("date")));
        assertThat("Exact custom method", shc.headers("Follow"), is(List.of("x-custom")));
        assertThat("No programmatic custom alias", shc.headers("FOLLOW"), is(List.of("date")));
    }

    @Test
    void testFromBuilder() {
        SignedHeadersConfig shc = SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig.create(List.of("date")))
                .config("GET",
                        SignedHeadersConfig.HeadersConfig
                                .create(List.of("date", REQUEST_TARGET, "host"),
                                        List.of("authorization")))
                .build();

        testThem(shc);
    }

    @Test
    void testBuilderRejectsNullMethodConfig() {
        var headers = SignedHeadersConfig.HeadersConfig.create();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                                   () -> SignedHeadersConfig.builder().config(null, headers)),
                () -> assertThrows(NullPointerException.class,
                                   () -> SignedHeadersConfig.builder().config("GET", null))
        );
    }

    private void testThem(SignedHeadersConfig shc) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        List<String> requiredHeaders = shc.headers("GET", headers);
        // first check we get the mandatory ones even if they are not present in request
        assertThat(requiredHeaders, CoreMatchers.hasItems("date", REQUEST_TARGET, "host"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization")));

        requiredHeaders = shc.headers("get", headers);
        assertThat(requiredHeaders, CoreMatchers.hasItems("date"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization", REQUEST_TARGET, "host")));

        requiredHeaders = shc.headers("POST", headers);
        assertThat(requiredHeaders, CoreMatchers.hasItems("date"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization", REQUEST_TARGET, "host")));

        //now let's add authorization to the request headers
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        requiredHeaders = shc.headers("GET", headers);
        // first check we get the mandatory ones even if they are not present in request
        assertThat(requiredHeaders, CoreMatchers.hasItems("date", REQUEST_TARGET, "host", "authorization"));

        requiredHeaders = shc.headers("POST", headers);
        assertThat(requiredHeaders, CoreMatchers.hasItems("date"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization", REQUEST_TARGET, "host")));
    }
}
