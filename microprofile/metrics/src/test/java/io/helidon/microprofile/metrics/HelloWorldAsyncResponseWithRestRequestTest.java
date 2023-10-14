/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
@AddConfig(key = "metrics.permit-all", value = "true")
class HelloWorldAsyncResponseWithRestRequestTest {

    @Inject
    WebTarget webTarget;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry baseRegistry;

    @Test
    void checkForAsyncMethodRESTRequestMetric() throws NoSuchMethodException {

        MetricID idForRestRequestTimer = MetricsCdiExtension.restEndpointTimerMetricID(
                HelloWorldResource.class.getMethod("getAsync", AsyncResponse.class));
        Timer restRequestTimerForGetAsyncMethod = baseRegistry.getTimer(idForRestRequestTimer);

        JsonObject restRequest = getRESTRequestJSON();

        // Make sure count is 0 before invoking.
        long getAsyncCount = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                              "count",
                                                                                              false))
                .longValue();
        assertThat("getAsync count value via endpoint before invocation", getAsyncCount, is(0L));
        assertThat("getAsync count value via metric reference before invocation",
                   restRequestTimerForGetAsyncMethod.getCount(),
                   is(0L));

        JsonNumber getAsyncTime = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                                             "elapsedTime",
                                                                                             false));
        assertThat("getAsync elapsedTime value before invocation", getAsyncTime.longValue(), is(0L));

        JsonNumber getAsyncMax = JsonNumber.class.cast(getRESTRequestValueForGetAsyncMethod(restRequest,
                                                                     "max",
                                                                     false));
        assertThat("Max before invocation", getAsyncMax.doubleValue(), is(0.0D));

        // Access the async endpoint.

        Response response = webTarget.path("helloworld/get-async")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get();

        assertThat("Ping of async endpoint status", response.getStatus(), is(200));
        String body = response.readEntity(String.class);
        assertThat("Returned content", body, containsString("AsyncResponse"));

        // With async endpoints, metrics updates can occur after the server sends the response.
        // Retry as needed (including fetching the metrics again) for a little while for the count to change.

        assertThatWithRetry("getAsync count value via metric reference after invocation",
                            () -> restRequestTimerForGetAsyncMethod.getCount(),
                            is(1L));

        // Retrieve metrics again and make sure we see an additional count and added time. Don't bother checking the min and
        // max because we'd have to wait up to a minute for those values to change.

        AtomicReference<JsonObject> nextRestRequest = new AtomicReference<>();

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

    private String getRESTRequestProm() throws IOException {
        Response metricsResponse = webTarget.path("/metrics/base")
                .request(MediaType.TEXT_PLAIN)
                .get();

        assertThat("Status retrieving REST request metrics", metricsResponse.getStatus(), is(200));

        String metrics = metricsResponse.readEntity(String.class);
        StringJoiner sj = new StringJoiner(System.lineSeparator());
        LineNumberReader reader = new LineNumberReader(new StringReader(metrics));
        String line;
        boolean proceed = true;
        while (proceed) {
            line = reader.readLine();
            proceed = (line != null);
            if (proceed) {
                if (line.startsWith("REST_request_seconds_count")) {
                    sj.add(line);
                }
            }
        }
        return sj.toString();
    }

    private double getRESTRequestValueForGetAsyncMethod(String prometheusOutput,
                                                        String valueName,
                                                        boolean nullOK) throws NoSuchMethodException {
        Pattern pattern = Pattern.compile(".*?^" + valueName + "\\{([^}]+)}\\s+(\\S+).*?",
                                          Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(prometheusOutput);

        assertThat("Match for REST request count", matcher.matches(), is(true));
        // Digest the tags to make sure the class and method tags are correct.
        String[] tags = matcher.group(1).split(",");
        boolean foundCorrectClass = false;
        boolean foundCorrectMethod = false;

        String expectedMethodName = HelloWorldResource.class.getMethod("getAsync", AsyncResponse.class).getName()
                + "_" + AsyncResponse.class.getName();

        for (String tag : tags) {
            if (tag.isBlank()) {
                continue;
            }
            String[] parts = tag.split("=");
            foundCorrectClass |= (parts[0].equals("class") && parts[1].equals(HelloWorldResource.class.getName()));
            foundCorrectMethod |= (parts[0].equals("method") && parts[1].equals(expectedMethodName));
        }
        assertThat("Class tag correct", foundCorrectClass, is(true));
        assertThat("Method tag correct", foundCorrectMethod, is(true));

        return Double.parseDouble(matcher.group(2));
    }

    private JsonValue getRESTRequestValueForGetAsyncMethod(JsonObject restRequestJson,
                                                           String valueName,
                                                           boolean nullOK)  {
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
