/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.MatcherWithRetry;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
class TestMetricsConfigPropagation {

    private final Http1Client client;
    private final WebServer server;

    TestMetricsConfigPropagation(WebServer server, Http1Client client) {
        this.client = client;
        this.server = server;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.get("/greet",
                    (request, response) -> response.send("Hello, World!"))
                .get("/greet/{name}",
                     (request, response) -> response.send("Hello, "
                    + request.path().segments().get(1).value() + "!"));
    }


    @Test
    void checkExtendedKpiMetrics() {
        try (Http1ClientResponse response = client.get("/observe/metrics")
                .accept(MediaTypes.APPLICATION_JSON)
                .request()) {
            assertThat("Metrics endpoint", response.status().code(), is(200));
            JsonObject metricsResponse = response.as(JsonObject.class);
            JsonObject vendorMeters = metricsResponse.getJsonObject("vendor");
            assertThat("Vendor meters", vendorMeters, notNullValue());

            // Make sure that the extended KPI metrics were turned on as directed by the configuration.
            assertThat("Metrics KPI load",
                       vendorMeters.getJsonNumber("requests.load").intValue(),
                       greaterThan(0));

            // Make sure that requests.count is absent because of the filtering in the config.
            assertThat("Metrics KPI requests.count", vendorMeters.get("requests.count"), nullValue());
        }
    }

    @Test
    void checkAutoMetrics() {
        try (Http1ClientResponse response = client.get("/greet/Joe")
                .accept(MediaTypes.TEXT_PLAIN)
                .request()) {
            assertThat("Greet endpoint", response.status().code(), is(200));
            /*
            Helidon registers and updates the timer asynchronously, so tolerate some delay.
             */
            var meterRegistry = Services.get(MeterRegistry.class);
            AtomicReference<List<Timer>> timersRef = new AtomicReference<>();
            MatcherWithRetry.assertThatWithRetry("Automatic timers",
                                                 () -> {
                timersRef.set(meterRegistry.meters(meter -> meter.id().name()
                                                             .equals(AutoMetricsFilter.TIMER_NAME))
                                      .stream()
                                      .filter(m -> m instanceof Timer)
                                      .map(m -> (Timer) m)
                                      .toList());
                return timersRef.get();
                                                 },
                                                 hasSize(greaterThan(0)));

            Timer timer = timersRef.get().getFirst();
            assertThat("Greet timer update count", timer.count(), is(1L));
            assertThat("Greet timer total time", timer.totalTime(TimeUnit.NANOSECONDS),
                       greaterThan(0D));
            assertThat("Greet timer name", timer.id().name(), is(AutoMetricsFilter.TIMER_NAME));

            Map<String, String> tags = timer.id().tagsMap();
            assertThat("Greet timer HTTP method", tags, allOf(
                    hasEntry(AutoMetricsFilter.HTTP_METHOD, "GET"),
                    hasEntry(AutoMetricsFilter.HTTP_SCHEME, "HTTP"),
                    hasEntry(AutoMetricsFilter.ERROR_TYPE, ""),
                    hasEntry(AutoMetricsFilter.STATUS_CODE, "200"),
                    hasEntry(AutoMetricsFilter.HTTP_ROUTE, "/greet/{name}"),
                    hasEntry(AutoMetricsFilter.NETWORK_PROTOCOL_NAME, "HTTP"),
                    hasEntry(AutoMetricsFilter.NETWORK_PROTOCOL_VERSION, "1.1"),
                    hasEntry(AutoMetricsFilter.SERVER_ADDRESS, "localhost"),
                    hasEntry(AutoMetricsFilter.SERVER_PORT, Integer.toString(server.port()))));
        }
    }
}
