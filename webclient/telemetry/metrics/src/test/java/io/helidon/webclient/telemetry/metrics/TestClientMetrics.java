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

package io.helidon.webclient.telemetry.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Method;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Timer;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@ServerTest
class TestClientMetrics {

    private final WebServer server;
    private final MeterRegistry meterRegistry;

    TestClientMetrics(WebServer server, MeterRegistry meterRegistry) {
        this.server = server;
        this.meterRegistry = meterRegistry;
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder builder) {
        builder.get("/greet", (req, resp) -> resp.send("Hello, World!"));
    }

    @Test
    void testClientMetrics() {
        var client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .addService(WebClientTelemetryMetrics.create())
                .build();

        var response = client.get("/greet")
                .accept(MediaTypes.TEXT_PLAIN)
                .request(String.class);

        assertThat("Response status", response.status().code(), is(200));

        List<Timer> timers = meterRegistry.meters(meter -> meter.id().name().equals(WebClientTelemetryMetrics.REQUEST_DURATION))
                .stream()
                .filter(m -> m instanceof Timer)
                .map(m -> (Timer) m)
                .toList();

        assertThat("Timers", timers, hasSize(equalTo(1)));

        Timer goodTimer = timers.getFirst();

        assertThat("Timer count", goodTimer.count(), is(greaterThan(0L)));
        assertThat("Timer duration", goodTimer.totalTime(TimeUnit.NANOSECONDS), is(greaterThan(0D)));
        var tags = goodTimer.id().tagsMap();

        assertThat("Timer tags", tags, allOf(
                hasEntry(WebClientTelemetryMetrics.HTTP_REQUEST_METHOD, Method.GET_NAME),
                hasEntry(WebClientTelemetryMetrics.SERVER_ADDRESS, "localhost"),
                hasEntry(WebClientTelemetryMetrics.SERVER_PORT, Integer.toString(server.port())),
                hasEntry(WebClientTelemetryMetrics.ERROR_TYPE, ""),
                hasEntry(WebClientTelemetryMetrics.HTTP_RESPONSE_STATUS_CODE, "200"),
                hasEntry(WebClientTelemetryMetrics.URL_SCHEME, "http")));

        var response404 = client.get("/missing")
                .accept(MediaTypes.TEXT_PLAIN)
                .request(String.class);

        assertThat("Expected failed response", response404.status().code(), is(404));

        timers = new ArrayList<>(meterRegistry.meters(meter -> meter.id().name().equals(WebClientTelemetryMetrics.REQUEST_DURATION))
                .stream()
                .filter(m -> m instanceof Timer)
                .map(m -> (Timer) m)
                .toList());
        timers.remove(goodTimer);

        assertThat("Bad timer count", timers, hasSize(1));

        var badTimer = timers.getFirst();
        assertThat("Timer count", badTimer.count(), is(greaterThan(0L)));
        assertThat("Bad timer duration",  badTimer.totalTime(TimeUnit.NANOSECONDS), is(greaterThan(0D)));

        tags = badTimer.id().tagsMap();
        assertThat("Bad timer tags", tags, allOf(
                hasEntry(WebClientTelemetryMetrics.HTTP_REQUEST_METHOD, Method.GET_NAME),
                hasEntry(WebClientTelemetryMetrics.SERVER_ADDRESS, "localhost"),
                hasEntry(WebClientTelemetryMetrics.SERVER_PORT, Integer.toString(server.port())),
                hasEntry(WebClientTelemetryMetrics.ERROR_TYPE, "404"),
                hasEntry(WebClientTelemetryMetrics.HTTP_RESPONSE_STATUS_CODE, "404"),
                hasEntry(WebClientTelemetryMetrics.URL_SCHEME, "http")));

    }
}
