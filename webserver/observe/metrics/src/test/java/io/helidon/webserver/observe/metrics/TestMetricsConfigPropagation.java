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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.json.JsonObject;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
class TestMetricsConfigPropagation {

    private static final String EARLY_APPLICATION_COUNTER = "early.application.counter";
    private static Counter earlyApplicationCounter;

    private final Http1Client client;

    TestMetricsConfigPropagation(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void registerApplicationMetricBeforeObserveFeature(WebServerConfig.Builder ignored) {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = Services.get(MeterRegistry.class);
        earlyApplicationCounter = meterRegistry.getOrCreate(metricsFactory.counterBuilder(EARLY_APPLICATION_COUNTER));
        earlyApplicationCounter.increment();
    }

    @Test
    void checkApplicationMetricRegisteredBeforeObserveFeature() {
        earlyApplicationCounter.increment();
        assertThat("Original counter remains active", earlyApplicationCounter.count(), is(2L));

        try (Http1ClientResponse response = client.get("/observe/metrics")
                .accept(MediaTypes.APPLICATION_JSON)
                .request()) {
            assertThat("Metrics endpoint", response.status().code(), is(200));
            JsonObject metricsResponse = response.as(JsonObject.class);
            JsonObject applicationMeters = metricsResponse.objectValue("application").orElse(null);
            assertThat("Application meters", applicationMeters, notNullValue());
            assertThat("Early application counter",
                       applicationMeters.numberValue(EARLY_APPLICATION_COUNTER).orElseThrow().intValue(),
                       is(2));
        }
    }

    @Test
    void checkExtendedKpiMetrics() {
        try (Http1ClientResponse response = client.get("/observe/metrics")
                .accept(MediaTypes.APPLICATION_JSON)
                .request()) {
            assertThat("Metrics endpoint", response.status().code(), is(200));
            JsonObject metricsResponse = response.as(JsonObject.class);
            JsonObject vendorMeters = metricsResponse.objectValue("vendor").orElseThrow();
            assertThat("Vendor meters", vendorMeters, notNullValue());

            // Make sure that the extended KPI metrics were turned on as directed by the configuration.
            assertThat("Metrics KPI load",
                       vendorMeters.numberValue("requests.load").orElseThrow().intValue(),
                       greaterThan(0));

            // Make sure that requests.count is absent because of the filtering in the config.
            assertThat("Metrics KPI requests.count", vendorMeters.value("requests.count").orElse(null), nullValue());
        }
    }

    @Test
    void checkScopeSelectionWithConfiguredTagName() {
        try (Http1ClientResponse response = client.get("/observe/metrics")
                .queryParam("scope", "vendor")
                .accept(MediaTypes.TEXT_PLAIN)
                .request()) {
            assertThat("Metrics endpoint", response.status().code(), is(200));
            assertThat("Metrics response", response.as(String.class), containsString("requests_load"));
        }
    }
}
