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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.uri.UriPath;
import io.helidon.config.Config;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestAutoMetricsConfig {

    @Test
    void testAutoMetricsConfig() {
        String configText = """
                server:
                  features:
                    observe:
                      observers:
                        metrics:
                          auto-http-metrics:
                            paths:
                              - path: "/greet/{name}"
                                methods: ["GET","OPTIONS"]
                              - path: "/greet"
                                methods: ["GET","HEAD"]
                              - path: "/stuff"
                                methods: ["HEAD","OPTIONS"]
                                enabled: false
                              - path: "/hi"
                                enabled: false
                              - path: "/another/{name}"
                                methods: ["PUT","POST","OPTIONS"]
                                enabled: false
                              - path: "/another/{name}"
                """;
        var configFromText = Config.just(configText, MediaTypes.APPLICATION_YAML)
                .get("server.features.observe.observers.metrics.auto-http-metrics");
        var config = AutoHttpMetricsConfig.create(configFromText);

        assertThat("GET /greet", config.isMeasured(Method.GET, UriPath.create("/greet")), is(true));
        assertThat("PUT /greet", config.isMeasured(Method.PUT, UriPath.create("/greet")), is(true));

        assertThat("GET /greet/Joe", config.isMeasured(Method.GET, UriPath.create("/greet/Joe")), is(true));

        assertThat("GET /hi", config.isMeasured(Method.GET, UriPath.create("/hi")), is(false));

        assertThat("GET /metrics", config.isMeasured(Method.GET, UriPath.create("/metrics")), is(false));

        assertThat("GET /stuff", config.isMeasured(Method.GET, UriPath.create("/stuff")), is(true));
        assertThat("HEAD /stuff", config.isMeasured(Method.HEAD, UriPath.create("/stuff")), is(false));

        assertThat("PUT /another/Joe", config.isMeasured(Method.PUT, UriPath.create("/another/Joe")), is(false));
        assertThat("GET /another/Joe", config.isMeasured(Method.GET, UriPath.create("/another/Joe")), is(true));

        assertThat("GET /undeclared", config.isMeasured(Method.GET, UriPath.create("/unknown")), is(true));

        assertThat("GET /observe/metrics",
                   config.isMeasured(Method.GET, UriPath.create("/observe/metrics")),
                   is(false));

        assertThat("GET /metrics", config.isMeasured(Method.GET, UriPath.create("/metrics")), is(false));

        var withCatchAll = AutoHttpMetricsConfig.builder()
                .config(configFromText)
                .addPath(AutoHttpMetricsPathConfig.builder()
                                 .path("/*")
                                 .enabled(false)
                                 .build())
                .build();

        assertThat("PUT /greet with catchall",
                   withCatchAll.isMeasured(Method.PUT, UriPath.create("/greet")),
                   is(false));
        assertThat("GET /undeclared with catchall",
                   withCatchAll.isMeasured(Method.GET, UriPath.create("/undeclared")),
                   is(false));
        assertThat("GET /another/Joe",
                   withCatchAll.isMeasured(Method.GET, UriPath.create("/another/Joe")),
                   is(true));

    }

    @Test
    void testWithNoAutoConfig() {
        String configText = """
                server:
                  features:
                    observe:
                      observers:
                        metrics:
                """;
        var config = AutoHttpMetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                          .get("server.features.observe.observers.metrics.auto-http-metrics"));

        assertThat("GET /greet", config.isMeasured(Method.GET, UriPath.create("/greet")), is(true));
        assertThat("PUT /greet", config.isMeasured(Method.PUT, UriPath.create("/greet")), is(true));

        assertThat("GET /greet/Joe", config.isMeasured(Method.GET, UriPath.create("/greet/Joe")), is(true));
        assertThat("GET /other", config.isMeasured(Method.GET, UriPath.create("/other")), is(true));

        assertThat("GET /hi", config.isMeasured(Method.GET, UriPath.create("/hi")), is(true));

        assertThat("GET /metrics", config.isMeasured(Method.GET, UriPath.create("/metrics")), is(false));

    }
}
