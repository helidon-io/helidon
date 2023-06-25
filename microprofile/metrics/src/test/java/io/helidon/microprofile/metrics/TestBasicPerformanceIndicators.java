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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.http.Http;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@HelidonTest
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
                .accept(MediaType.TEXT_PLAIN)
                .get();

        assertThat("Metrics /metrics/vendor URL HTTP status", response.getStatus(), is(Http.Status.OK_200.code()));

        String vendorMetrics = response.readEntity(String.class);

        Pattern pattern = Pattern.compile(".*?^requests_count\\S+\\s+(\\S+).*?", Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = pattern.matcher(vendorMetrics);

        assertThat("Vendor metric requests count pattern match", matcher.matches(), is(true));
        assertThat("Vendor metric requests count value", Double.parseDouble(matcher.group(1)), greaterThan(0.0D));

        // This test runs with extended KPI metrics disabled. Make sure the count and meter are still updated.
//        int count = vendorMetrics.getInt("requests.count");
//        assertThat("requests.count", count, is(greaterThan(0)));
//
//        JsonObject meter = vendorMetrics.getJsonObject("requests.meter");
//        int meterCount = meter.getInt("count");
//        assertThat("requests.meter count", meterCount, is(greaterThan(0)));
//
//        double meterRate = meter.getJsonNumber("meanRate").doubleValue();
//        assertThat("requests.meter meanRate", meterRate, is(greaterThan(0.0)));
    }
}
