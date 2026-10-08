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

package io.helidon.webserver.observe.telemetry.metrics;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class OpenTelemetryMetricsReroutingTest {

    @Test
    void includedPathReroutedToExcludedEndpointIsNotMeasured() throws Exception {
        checkRequest("/included-alias", "excluded", null);
    }

    @Test
    void excludedPathReroutedToIncludedEndpointIsMeasured() throws Exception {
        checkRequest("/excluded-alias", "included", "/included");
    }

    @Test
    void multipleReroutesUseFinalEndpoint() throws Exception {
        checkRequest("/multiple-alias", "included", "/included");
    }

    @Test
    void ordinaryIncludedRequestIsMeasured() throws Exception {
        checkRequest("/included", "included", "/included");
    }

    @Test
    void ordinaryExcludedRequestIsNotMeasured() throws Exception {
        checkRequest("/excluded", "excluded", null);
    }

    @Test
    void reroutedErrorResponseIsMeasured() throws Exception {
        checkRequest("/excluded-error-alias", "error", "/error");
    }

    private static void checkRequest(String path, String expectedEntity, String expectedRoute) throws Exception {
        List<Attributes> measurements = new CopyOnWriteArrayList<>();
        var completed = new CountDownLatch(1);
        var histogram = mock(DoubleHistogram.class);
        doAnswer(invocation -> {
            measurements.add(invocation.getArgument(1));
            return null;
        }).when(histogram).record(anyDouble(), any(Attributes.class));
        var config = AutoHttpMetricsConfig.builder()
                .addPaths(List.of(AutoHttpMetricsPathConfig.builder().path("/excluded*").enabled(false).build()))
                .build();
        var metricsFilter = OpenTelemetryMetricsHttpSemanticConventions.MetricsRecordingFilter.create(histogram, config);
        var serverConfig = Config.just("""
                features:
                  observe:
                    observers-discover-services: false
                """, MediaTypes.APPLICATION_YAML);
        var server = WebServer.builder()
                .config(serverConfig)
                .port(0)
                .routing(routing -> routing
                        .addFilter((chain, request, response) -> {
                            try {
                                metricsFilter.filter(chain, request, response);
                            } finally {
                                completed.countDown();
                            }
                        })
                        .get("/included-alias", (_, response) -> response.reroute("/excluded"))
                        .get("/excluded-alias", (_, response) -> response.reroute("/included"))
                        .get("/multiple-alias", (_, response) -> response.reroute("/excluded-alias"))
                        .get("/excluded-error-alias", (_, response) -> response.reroute("/error"))
                        .get("/included", (_, response) -> response.send("included"))
                        .get("/excluded", (_, response) -> response.send("excluded"))
                        .get("/error", (_, _) -> {
                            throw new IllegalArgumentException("error");
                        })
                        .error(IllegalArgumentException.class, (_, response, _) -> response.status(500).send("error")))
                .build()
                .start();
        var client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri("http://localhost:" + server.port())
                .build();
        try {
            try (var response = client.get(path).request()) {
                assertThat(response.status().code(), is(expectedEntity.equals("error") ? 500 : 200));
                assertThat(response.as(String.class), is(expectedEntity));
            }
            assertThat("Metrics filter completed", completed.await(10, TimeUnit.SECONDS), is(true));
            assertThat("Measurements for " + path, measurements, hasSize(expectedRoute == null ? 0 : 1));
            if (expectedRoute != null) {
                var attributes = measurements.getFirst();
                assertThat(attributes.get(AttributeKey.stringKey(OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE)),
                           is(expectedRoute));
                assertThat(attributes.get(AttributeKey.stringKey(OpenTelemetryMetricsHttpSemanticConventions.HTTP_METHOD)),
                           is("GET"));
                assertThat(attributes.get(AttributeKey.longKey(OpenTelemetryMetricsHttpSemanticConventions.STATUS_CODE)),
                           is(expectedEntity.equals("error") ? 500L : 200L));
            }
        } finally {
            client.closeResource();
            server.stop();
        }
    }
}
