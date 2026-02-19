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
import io.helidon.config.ConfigSources;
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
                              - path: "/greet"
                                methods: ["GET","HEAD"]
                              - path: "/greet/{name}"
                                methods: ["GET","OPTIONS"]
                              - path: "/stuff"
                                methods: ["HEAD","OPTIONS"]
                                enabled: false
                              - path: "/hi"
                                enabled: false
                """;
        var config = AutoHttpMetricsConfig.create(Config.just(ConfigSources.create(configText, MediaTypes.APPLICATION_X_YAML))
                                             .get("server.features.observe.observers.metrics.auto"));

        assertThat("GET /greet", config.isMeasured(Method.GET, UriPath.create("/greet")), is(true));
        assertThat("PUT /greet", config.isMeasured(Method.PUT, UriPath.create("/greet")), is(false));

        assertThat("GET /greet/Joe",  config.isMeasured(Method.GET, UriPath.create("/greet/Joe")), is(true));
        assertThat("GET /other", config.isMeasured(Method.GET, UriPath.create("/other")), is(true));

        assertThat("GET /hi", config.isMeasured(Method.GET, UriPath.create("/hi")), is(false));

        assertThat("GET /metrics", config.isMeasured(Method.GET, UriPath.create("/metrics")), is(false));

        assertThat("GET /stuff", config.isMeasured(Method.GET, UriPath.create("/stuff")), is(true));
        assertThat("HEAD /stuff", config.isMeasured(Method.HEAD, UriPath.create("/stuff")), is(false));

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
        var config = AutoHttpMetricsConfig.create(Config.just(ConfigSources.create(configText, MediaTypes.APPLICATION_X_YAML))
                                                          .get("server.features.observe.observers.metrics.auto"));

        assertThat("GET /greet", config.isMeasured(Method.GET, UriPath.create("/greet")), is(true));
        assertThat("PUT /greet", config.isMeasured(Method.PUT, UriPath.create("/greet")), is(true));

        assertThat("GET /greet/Joe",  config.isMeasured(Method.GET, UriPath.create("/greet/Joe")), is(true));
        assertThat("GET /other", config.isMeasured(Method.GET, UriPath.create("/other")), is(true));

        assertThat("GET /hi", config.isMeasured(Method.GET, UriPath.create("/hi")), is(true));

        assertThat("GET /metrics", config.isMeasured(Method.GET, UriPath.create("/metrics")), is(false));

    }
}
