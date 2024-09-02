/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
class TestMetricsConfigPropagation {

    private final Http1Client client;

    TestMetricsConfigPropagation(Http1Client client) {
        this.client = client;
    }

    @Test
    void checkExtendedKpiMetrics() throws InterruptedException {
        var response = client.get("/observe/metrics")
                .accept(MediaTypes.APPLICATION_JSON)
                .request(JsonObject.class);

        JsonObject metricsResponse = response.entity();
        JsonObject vendorMeters = metricsResponse.getJsonObject("vendor");
        assertThat("Vendor meters", vendorMeters, notNullValue());

        // Make sure that the extended KPI metrics were turned on as directed by the configuration.
        assertThat("Metrics KPI load",
                   vendorMeters.getJsonNumber("requests.load"),
                   notNullValue());
        assertThat("Metrics KPI load",
                   vendorMeters.getJsonNumber("requests.load").intValue(),
                   greaterThan(0));

        // Make sure that requests.count is absent because of the filtering in the config.
        assertThat("Metrics KPI requests.count", vendorMeters.get("requests.count"), nullValue());
    }
}
