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
import java.util.concurrent.TimeUnit;

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")

public class HelloWorldAsyncResponseWithRestRequestTest {

    @Inject
    WebTarget webTarget;

    @Test
    void checkForAsyncMethodRESTRequestMetric() throws NoSuchMethodException {

        JsonObject restRequest = getRESTRequestJSON();

        // Make sure count is 0 before invoking.
        JsonNumber getAsyncCount = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                              "count",
                                                                                              false));
        assertThat("getAsync count value before invocation", getAsyncCount.longValue(), is(0L));

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

        restRequest = getRESTRequestJSON();

        try {
            getAsyncCount = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                       "count",
                                                                                       false));
            assertThat("getAsync count value after invocation", getAsyncCount.longValue(), is(1L));

            getAsyncTime = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                      "elapsedTime",
                                                                                      false));
            assertThat("getAsync elapsedTime value after invocation", getAsyncTime.longValue(), is(greaterThan(0L)));
        } catch (Exception e) {
            throw new RuntimeException("Dump of REST.request metrics due to following assertion failure\n"
                                               + restRequest, e);
        }
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
                                                           boolean nullOK) throws NoSuchMethodException {
        JsonValue getAsyncValue = null;

        for (Map.Entry<String, JsonValue> entry : restRequestJson.entrySet()) {
            // Conceivably there could be multiple tags in the metric ID besides the class and method ones, so do not assume
            // the key value in the JSON object has only the class and method tags and only in that order.
            if (entry.getKey().startsWith(valueName + ";")
                    && entry.getKey().contains("class=" + HelloWorldResource.class.getName())
                    && entry.getKey().contains(
                    "method="
                            + HelloWorldResource.class.getMethod("getAsync", AsyncResponse.class).getName()
                            + "_" + AsyncResponse.class.getName())) {
                getAsyncValue = entry.getValue();
                break;
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
