/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY;
import static io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddConfig(key =
        "metrics."
                + KEY_PERFORMANCE_INDICATORS_CONFIG_KEY
                + "." + KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY,
        value = "true")
@AddConfig(key = "server.executor-service.core-pool-size", value = "1")
@AddConfig(key = "server.executor-service.max-pool-size", value = "1")
@AddBean(HelloWorldApp.class)
public class TestExtendedKPIMetrics {

    @Inject
    WebTarget webTarget;


    @Test
    void checkNonZeroDeferredTime() throws ExecutionException, InterruptedException {
        // Run two requests concurrently, with the server configured for one thread, to force one request to be deferred.
        Future<Response> response1Future = webTarget
                .path("helloworld")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .buildGet()
                .submit();

        Future<Response> response2Future = webTarget
                .path("helloworld")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .buildGet()
                .submit();

        // Now wait for both requests to finish.
        try (Response response1 = response1Future.get(); Response response2 = response2Future.get()) {
            assertThat("Access to GET response 1", response1.getStatus(), is(Http.Status.OK_200.code()));
            assertThat("Access to GET response 2", response2.getStatus(), is(Http.Status.OK_200.code()));
        }

        try (Response metricsResponse = webTarget
                .path("metrics/vendor")
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get()) {

            assertThat("Metrics /metrics/vendor URL HTTP status", metricsResponse.getStatus(), is(Http.Status.OK_200.code()));

            JsonObject vendorMetrics = metricsResponse.readEntity(JsonObject.class);

            assertThat("Extended KPI metric requests.deferred present", vendorMetrics.containsKey("requests.deferred"),
                    is(true));

            JsonObject requestsDeferred = vendorMetrics.getJsonObject("requests.deferred");
            assertThat("requests.deferred", requestsDeferred, is(notNullValue()));

            int deferredCount = requestsDeferred.getInt("count");
            assertThat("Extended KPI metric requests.deferred->count value", deferredCount, is(greaterThan(0)));
        }
    }
}
