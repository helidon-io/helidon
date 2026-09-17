/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.config.Config;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class PathsConfigMethodTest {
    private static final Map<String, Method> STANDARD_METHODS = Map.of(
            "get", Method.GET,
            "post", Method.POST,
            "query", Method.QUERY,
            "put", Method.PUT,
            "delete", Method.DELETE,
            "head", Method.HEAD,
            "patch", Method.PATCH,
            "options", Method.OPTIONS,
            "trace", Method.TRACE,
            "connect", Method.CONNECT);

    @Test
    void configuredLowercaseStandardMethodsRetainExactAndUppercaseMatching() {
        STANDARD_METHODS.forEach((configuredMethod, standardMethod) -> {
            var config = PathsConfig.create(Config.just("""
                    path: /greet
                    methods: [%s]
                    """.formatted(configuredMethod), MediaTypes.APPLICATION_YAML));
            var predicate = Method.predicate(config.methods());

            assertThat(configuredMethod + " exact match", predicate.test(Method.create(configuredMethod)), is(true));
            assertThat(configuredMethod + " standard alias", predicate.test(standardMethod), is(true));
            assertThat(configuredMethod + " unrelated method", predicate.test(Method.create("Follow")), is(false));
        });
    }

    @Test
    void configuredMixedCaseMethodDoesNotMatchEveryCaseVariant() {
        var config = PathsConfig.create(Config.just("""
                path: /greet
                methods: [PoSt]
                """, MediaTypes.APPLICATION_YAML));
        var predicate = Method.predicate(config.methods());

        assertThat("Configured spelling", predicate.test(Method.create("PoSt")), is(true));
        assertThat("Standard alias", predicate.test(Method.POST), is(true));
        assertThat("Unconfigured lowercase spelling", predicate.test(Method.create("post")), is(false));
    }

    @Test
    void configuredLowercaseMethodDoesNotMatchMixedCase() {
        var config = PathsConfig.create(Config.just("""
                path: /greet
                methods: [get]
                """, MediaTypes.APPLICATION_YAML));

        assertThat(Method.predicate(config.methods()).test(Method.create("Get")), is(false));
    }

    @Test
    void configuredCustomMethodUsesExactMatching() {
        var config = PathsConfig.create(Config.just("""
                path: /greet
                methods: [Follow]
                """, MediaTypes.APPLICATION_YAML));
        var predicate = Method.predicate(config.methods());

        assertThat("Configured spelling", predicate.test(Method.create("Follow")), is(true));
        assertThat("Uppercase custom method", predicate.test(Method.create("FOLLOW")), is(false));
        assertThat("Lowercase custom method", predicate.test(Method.create("follow")), is(false));
    }

    @Test
    void configuredUppercaseMethodsUseExactMatching() {
        STANDARD_METHODS.forEach((lowercaseMethod, standardMethod) -> {
            var config = PathsConfig.create(Config.just("""
                    path: /greet
                    methods: [%s]
                    """.formatted(standardMethod.text()), MediaTypes.APPLICATION_YAML));
            var predicate = Method.predicate(config.methods());

            assertThat(standardMethod + " exact match", predicate.test(standardMethod), is(true));
            assertThat(standardMethod + " lowercase spelling", predicate.test(Method.create(lowercaseMethod)), is(false));
        });
    }

    @Test
    void programmaticMethodUsesExactMatching() {
        var config = PathsConfig.builder()
                .path("/greet")
                .addMethod(Method.create("get"))
                .handler(SecurityHandler.create())
                .build();

        assertExactLowercaseMatch(config);
    }

    @Test
    void programmaticMethodsReplaceConfiguredAliases() {
        var config = PathsConfig.builder()
                .config(Config.just("""
                        path: /greet
                        methods: [get]
                        """, MediaTypes.APPLICATION_YAML))
                .methods(List.of(Method.create("get")))
                .build();

        assertExactLowercaseMatch(config);
    }

    @Test
    void configuredNonUppercaseStandardMethodWarnsAtConstruction() {
        var logger = Logger.getLogger(PathsConfig.class.getName());
        try (var handler = InMemoryLoggingHandler.create(logger)) {
            PathsConfig.create(Config.just("""
                    protected-path:
                      path: /greet
                      methods: [PoSt]
                    """, MediaTypes.APPLICATION_YAML).get("protected-path"));

            assertThat(handler.logRecords(), hasSize(1));
            var warning = handler.logRecords().getFirst();
            assertThat(warning.getLevel(), is(Level.WARNING));
            assertThat(new SimpleFormatter().formatMessage(warning), allOf(containsString("protected-path.methods"),
                                                                         containsString("PoSt"),
                                                                         containsString("POST"),
                                                                         containsString("future major")));
        }
    }

    @Test
    void exactMethodsDoNotWarn() {
        var logger = Logger.getLogger(PathsConfig.class.getName());
        try (var handler = InMemoryLoggingHandler.create(logger)) {
            PathsConfig.create(Config.just("""
                    path: /greet
                    methods: [POST, Follow]
                    """, MediaTypes.APPLICATION_YAML));
            PathsConfig.builder()
                    .path("/greet")
                    .addMethod(Method.create("get"))
                    .handler(SecurityHandler.create())
                    .build();

            assertThat(handler.logRecords(), hasSize(0));
        }
    }

    private static void assertExactLowercaseMatch(PathsConfig config) {
        var predicate = Method.predicate(config.methods());

        assertThat("Different-case method matching", predicate.test(Method.GET), is(false));
        assertThat("Exact method matching", predicate.test(Method.create("get")), is(true));
    }
}
