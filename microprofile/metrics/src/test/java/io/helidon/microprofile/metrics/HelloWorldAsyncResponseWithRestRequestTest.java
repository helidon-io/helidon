/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")

public class HelloWorldAsyncResponseWithRestRequestTest {

    @Inject
    WebTarget webTarget;

    @Test
    void checkForAsyncMethodRESTRequestMetric() throws NoSuchMethodException {

        JsonObject restRequest = getRESTRequestJSON();

        // Make sure count is 0 before invoking.
        long getAsyncCount = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                        "count",
                                                                                        false))
                .longValue();
        assertThat("getAsync count value before invocation", getAsyncCount, is(0L));

        JsonNumber getAsyncTime = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                             "elapsedTime",
                                                                                             false));
        assertThat("getAsync elapsedTime value before invocation", getAsyncTime.longValue(), is(0L));

        JsonValue getAsyncMin = getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                     "minTimeDuration",
                                                                     true);
        assertThat("Min before invocation", getAsyncMin.getValueType(), is(JsonValue.ValueType.NULL));
        JsonValue getAsyncMax = getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                     "maxTimeDuration",
                                                                     true);
        assertThat("Max before invocation", getAsyncMax.getValueType(), is(JsonValue.ValueType.NULL));

        // Access the async endpoint.

        Response response = webTarget.path("helloworld/get-async")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get();

        assertThat("Ping of async endpoint status", response.getStatus(), is(200));
        String body = response.readEntity(String.class);
        assertThat("Returned content", body, containsString("AsyncResponse"));

        // Retrieve metrics again and make sure we see an additional count and added time. Don't bother checking the min and
        // max because we'd have to wait up to a minute for those values to change.

        AtomicReference<JsonObject> nextRestRequest = new AtomicReference<>();

        // With async endpoints, metrics updates can occur after the server sends the response.
        // Retry as needed (including fetching the metrics again) for a little while for the count to change.
        assertThatWithRetry("getAsync count value after invocation",
                            () -> JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(
                                            // Set the reference using fresh metrics and get that newly-set JSON object, then
                                            // extract the 'count' field cast as a number.
                                            nextRestRequest.updateAndGet(old -> getRESTRequestJSON()),
                                            "count",
                                            false))
                                    .longValue(),
                            is(1L));

        // Reuse (no need to update the atomic reference again) the freshly-fetched metrics JSON.
         getAsyncTime = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(nextRestRequest.get(),
                                                                                  "elapsedTime",
                                                                                  false));
        assertThat("getAsync elapsedTime value after invocation", getAsyncTime.longValue(), is(greaterThan(0L)));
    }

    private JsonObject getRESTRequestJSON() {
        Response metricsResponse = webTarget.path("/metrics/base")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        assertThat("Status retrieving REST request metrics", metricsResponse.getStatus(), is(200));

        JsonObject metrics = metricsResponse.readEntity(JsonObject.class);

        assertThat("Top-level REST.request JSON value", metrics, hasKey("REST.request"));
        JsonValue restRequestValue = metrics.get("REST.request");

        assertThat("REST.request JSON value type",
                   restRequestValue.getValueType(),
                   is(JsonValue.ValueType.OBJECT));

        return restRequestValue.asJsonObject();
    }

    private JsonValue getRESTRequestValueForGetAsyncMethod(JsonObject restRequestJson,
                                                           String valueName,
                                                           boolean nullOK) {
        JsonValue getAsyncValue = null;

        for (Map.Entry<String, JsonValue> entry : restRequestJson.entrySet()) {
            // Conceivably there could be multiple tags in the metric ID besides the class and method ones, so do not assume
            // the key value in the JSON object has only the class and method tags and only in that order.
            try {
                if (entry.getKey().startsWith(valueName + ";")
                        && entry.getKey().contains("class=" + HelloWorldResource.class.getName())
                        && entry.getKey().contains(
                        "method="
                                + HelloWorldResource.class.getMethod("getAsync", AsyncResponse.class).getName()
                                + "_" + AsyncResponse.class.getName())) {
                    getAsyncValue = entry.getValue();
                    break;
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        if (!nullOK) {
            assertThat(valueName + " for REST.request simple timer for getAsync method", getAsyncValue, notNullValue());
            assertThat(valueName + " value is a number", getAsyncValue.getValueType(), is(JsonValue.ValueType.NUMBER));
            return getAsyncValue;
        }
        return (getAsyncValue instanceof JsonNumber) ? (JsonNumber) getAsyncValue : JsonValue.NULL;
    }
}
