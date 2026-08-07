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
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.testing.junit5.MatcherWithRetry;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.MetricsObserverConfig;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.spi.ServerFeature;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@RoutingTest
class LegacyOpenTelemetrySemanticConventionsTest {
    private static final AtomicReference<Attributes> RECORDED_ATTRIBUTES = new AtomicReference<>();

    private final Http1Client client;

    LegacyOpenTelemetrySemanticConventionsTest(Http1Client client) {
        this.client = client;
    }

    @SetUpFeatures(false)
    static List<ServerFeature> features() {
        return List.of();
    }

    @SetUpRoute
    @SuppressWarnings("removal")
    static void setupRouting(HttpRouting.Builder routing) {
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        doAnswer(invocation -> {
            RECORDED_ATTRIBUTES.set(invocation.getArgument(1));
            return null;
        }).when(histogram).record(anyDouble(), any(Attributes.class));

        MetricsObserverConfig config = MetricsObserverConfig.builder()
                .autoHttpMetrics(AutoHttpMetricsConfig.builder().useUpdatedHttpMetrics(false).build())
                .buildPrototype();

        routing.addFilter(OpenTelemetryMetricsHttpSemanticConventions.MetricsRecordingFilter.create(
                                  histogram, config.autoHttpMetrics().orElseThrow()))
                .get("/greet/{name}",
                     (req, res) -> res.send("Hello, " + req.path().pathParameters().get("name")));
    }

    @Test
    void legacyMetricsUseWebServerMatchingPattern() {
        RECORDED_ATTRIBUTES.set(null);
        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.status().code(), is(200));
        }

        Attributes attributes = MatcherWithRetry.assertThatWithRetry("Recorded legacy attributes",
                                                                      RECORDED_ATTRIBUTES::get,
                                                                      notNullValue());
        assertThat(attributes.get(AttributeKey.stringKey(OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE)),
                   is("/greet/{name}"));
    }
}
