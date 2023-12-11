/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddConfig(key = "metrics.scoping.tag-enabled", value = "true")
@AddConfig(key = "metrics.scoping.tag-name", value = "mp-config")
@AddConfig(key = "metrics.permit-all", value = "true")
class TestBasicPerformanceIndicators {

    @Inject
    WebTarget webTarget;

    @Test
    void checkMetricsVendorURL() {
        doCheckMetricsVendorURL(webTarget);
    }

    static void doCheckMetricsVendorURL(WebTarget webTarget) {
        Response response = webTarget
                .path("metrics/vendor")
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get();

        assertThat("Metrics /metrics/vendor URL HTTP status", response.getStatus(), is(Status.OK_200.code()));

        JsonObject vendorMetrics = response.readEntity(JsonObject.class);

        assertThat("Vendor metric requests.count present", vendorMetrics.containsKey("requests.count"), is(true));

        // This test runs with isExtended KPI metrics disabled. Make sure the count and meter are still updated.
        int count = vendorMetrics.getInt("requests.count");
        assertThat("requests.count", count, is(greaterThan(0)));
    }
}
