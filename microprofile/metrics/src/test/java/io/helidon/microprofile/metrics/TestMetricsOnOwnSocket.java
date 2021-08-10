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

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest()
// Set up the metrics endpoint on its own socket
@AddConfig(key = "server.sockets.0.name", value = "metrics")
// No port setting, so use any available one
@AddConfig(key = "server.sockets.0.bind-address", value = "0.0.0.0")
@AddConfig(key = "metrics.routing", value = "metrics")
@AddConfig(key = "metrics.key-performance-indicators.extended", value = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestMetricsOnOwnSocket {

    private Invocation metricsInvocation= null;

    @Inject
    private ServerCdiExtension serverCdiExtension;

    @Inject
    private WebTarget webTarget;

    Invocation metricsInvocation() {
        if (metricsInvocation == null) {
            int metricsPort = serverCdiExtension.port("metrics");
            metricsInvocation = ClientBuilder.newClient()
                    .target(String.format("http://localhost:%d/metrics/vendor", metricsPort))
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .buildGet();
        }
        return metricsInvocation;
    }

    @Order(0)
    @Test
    void checkMetricsBeforeRequests() {

        int load = getRequestsLoadCount("Pre-test");
        assertThat("Pre-test load count", load, is(0));

    }

    @Order(1)
    @Test
    void checkMessageFromDefaultRouting() {
        try (Response r = webTarget
                .path("helloworld")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get()) {

            assertThat("Response code getting greeting", r.getStatus(), is(Http.Status.OK_200.code()));
        }
    }

    @Order(2)
    @Test
    void checkMetricsAfterGet() {
        int load = getRequestsLoadCount("Post-test");
        assertThat("Post-test load count", load, is(1));

    }

    private int getRequestsLoadCount(String descr) {
        try (Response r = metricsInvocation().invoke()) {
            assertThat(descr + " metrics sampling response", r.getStatus(), is(Http.Status.OK_200.code()));

            JsonObject metrics = r.readEntity(JsonObject.class);
            assertThat("Check for requests.load", metrics.containsKey("requests.load"), is(true));
            JsonObject load = metrics.getJsonObject("requests.load");
            assertThat("JSON requests.load contains count", load.containsKey("count"), is(true));

            return load.getInt("count");
        }
    }
}
