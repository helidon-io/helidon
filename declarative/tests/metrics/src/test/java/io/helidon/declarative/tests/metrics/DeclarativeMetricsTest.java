/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.metrics;

import java.math.BigDecimal;

import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeclarativeMetricsTest {

    private final Http1Client client;

    protected DeclarativeMetricsTest(Http1Client client,
                                     ServiceRegistry registry) {
        this.client = client;

        // need to register the gauge
        registry.get(TestEndpoint__GaugeRegistrar.class);
    }

    @Test
    @Order(1)
    void testCounted() {
        var response = client.get("/endpoint/counted").request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("counted"));
    }

    @Test
    @Order(2)
    void testTimed() {
        var response = client.get("/endpoint/timed").request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("timed"));
    }

    @Test
    @Order(3)
    void testInjectedRegistry() {
        var response = client.get("/endpoint/time").request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), startsWith("time: "));
    }

    @Test
    @Order(4)
    void setGauge() {
        var response = client.post("/endpoint/gauge")
                        .submit("42", String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("gauge set"));
    }

    @Test
    void testMetricsObserver() {
        var response = client.get("/observe/metrics")
                .header(HeaderValues.ACCEPT_JSON)
                .request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));
        JsonObject metrics = response.entity();
        JsonObject appMetrics = metrics.objectValue("application").orElse(null);
        assertThat(appMetrics, notNullValue());
        // absolute metric name
        JsonObject timed = appMetrics.objectValue("my-timed-metric").orElse(null);
        assertThat(timed, notNullValue());

        // relative metric name + tags from type and annotation
        BigDecimal counted = appMetrics.numberValue("TestEndpoint.counted;application=MyNiceApp;endpoint=TestEndpoint;location=method")
                .orElse(null);
        assertThat(counted, notNullValue());
        assertThat(counted.intValue(), is(1));

        // relative metric name + tags from type
        BigDecimal gauge = appMetrics.numberValue("TestEndpoint.gaugeValue;application=MyNiceApp;endpoint=TestEndpoint")
                .orElse(null);
        assertThat(gauge, notNullValue());
        assertThat(gauge.intValue(), is(42));
    }
}
