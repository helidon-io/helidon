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

package io.helidon.webserver.observe.metrics;

import java.util.Set;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.Metrics;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestPrometheusRegistryControllingEndpoint {

    @BeforeAll
    static void init() {
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    static void clean() {
        var globalRegistry = io.micrometer.core.instrument.Metrics.globalRegistry;
        Set.copyOf(globalRegistry.getRegistries()).forEach(globalRegistry::remove);
        Metrics.globalRegistry().close();
    }

    @ParameterizedTest
    @MethodSource
    void checkMetricsEndpoint(String configText, int expectedMetricsEndpointStatus) {

        var webServer = WebServer.builder()
                .config(Config.just(configText, MediaTypes.APPLICATION_YAML).get("server"))
                .port(0)
                .addRouting(HttpRouting.builder().get("/greet", (req, resp) -> resp.send("Hi")))
                .build()
                .start();

        try {
            var client = WebClient.builder()
                    .baseUri("http://localhost:" + webServer.port())
                    .build();

            try (HttpClientResponse greetResp = client.get("/greet").request();
                    HttpClientResponse metricsResp = client.get("/observe/metrics").request()) {
                assertThat("Greet response", greetResp.status().code(), is(200));
                assertThat("Metrics response for config " + configText, metricsResp.status().code(), is(expectedMetricsEndpointStatus));
            }
        } finally {
            webServer.stop();
        }
    }

    static Stream<Arguments> checkMetricsEndpoint() {
        return Stream.of(Arguments.arguments("""
                server:
                  features:
                    observe:
                      observers:
                        metrics:
                          registries:
                            otlp:
                              step: PT1S
                            prometheus:
                              enabled: false
                """, 404),
                         Arguments.arguments("""
                server:
                  features:
                    observe:
                      observers:
                        metrics:
                          registries:
                            otlp:
                              step: PT1S
                            prometheus:
                """, 200),
                         Arguments.arguments("""
                server:
                  features:
                    observe:
                      observers:
                        metrics:
                          registries:
                            otlp:
                              step: PT1S
                """, 404));
    }

//    @Test
//    void ensureEndpointPresentWhenPrometheusMentioned() {
//        var configText = """
//                server:
//                  features:
//                    observe:
//                      observers:
//                        metrics:
//                          registries:
//                            otlp:
//                              step: PT1S
//                            prometheus:
//                """;
//
//        var webServer = WebServer.builder()
//                .config(Config.just(configText, MediaTypes.APPLICATION_YAML).get("server"))
//                .port(0)
//                .addRouting(HttpRouting.builder().get("/greet", (req, resp) -> resp.send("Hi")))
//                .build()
//                .start();
//
//        try {
//            var client = WebClient.builder()
//                    .baseUri("http://localhost:" + webServer.port())
//                    .build();
//
//            try (HttpClientResponse greetResp = client.get("/greet").request();
//                    HttpClientResponse metricsResp = client.get("/observe/metrics").request()) {
//                assertThat("Greet response", greetResp.status().code(), is(200));
//                assertThat("Metrics response", metricsResp.status().code(), is(200));
//            }
//        } finally {
//            webServer.stop();
//        }
//    }
//
//    @Test
//    void ensureEndpointAbsentWhenPrometheusOmittedWithOtlpPresent() {
//        var configText = """
//                server:
//                  features:
//                    observe:
//                      observers:
//                        metrics:
//                          registries:
//                            otlp:
//                              step: PT1S
//                """;
//
//        var webServer = WebServer.builder()
//                .config(Config.just(configText, MediaTypes.APPLICATION_YAML).get("server"))
//                .port(0)
//                .addRouting(HttpRouting.builder().get("/greet", (req, resp) -> resp.send("Hi")))
//                .build()
//                .start();
//
//        try {
//            var client = WebClient.builder()
//                    .baseUri("http://localhost:" + webServer.port())
//                    .build();
//
//            try (HttpClientResponse greetResp = client.get("/greet").request();
//                    HttpClientResponse metricsResp = client.get("/observe/metrics").request()) {
//                assertThat("Greet response", greetResp.status().code(), is(200));
//                assertThat("Metrics response", metricsResp.status().code(), is(404));
//            }
//        } finally {
//            webServer.stop();
//        }
//    }
}
