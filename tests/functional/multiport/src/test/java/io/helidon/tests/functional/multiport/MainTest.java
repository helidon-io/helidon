/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.functional.multiport;

import java.util.List;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Main}.
 */
class MainTest {
    private static final String BIZ_URI = "/hello";
    private static final String BIZ_EXPECTED = "Hello World";
    private static final String METRICS_URI = "/mymetrics";
    private static final String ENABLED_METRIC = "base/cpu.availableProcessors";
    private static final String DISABLED_METRIC = "base/thread.count";
    private static final String HEALTH_URI = "/myhealth";
    private static Client client;

    @BeforeAll
    static void initClass() {
        Main.main(new String[0]);
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void destroyClass() {
        Main.server().stop();
    }

    static Stream<Params> initParams() {
        return List.of(
                new Params(7001, true, false, false),
                new Params(8001, false, false, true),
                new Params(8002, false, true, false),
                // when no named routing, serves default routing
                new Params(8003, true, false, false)
        ).stream();
    }

    @MethodSource("initParams")
    @ParameterizedTest
    void testEndpoint(Params params) {
        String base = "http://localhost:" + params.port;
        WebTarget baseTarget = client.target(base);

        Response response;

        // business URI
        response = baseTarget.path(BIZ_URI)
                .request()
                .get();

        if (params.bizEnabled) {
            assertThat("port " + params.port + " should be serving business logic",
                       response.getStatusInfo().toEnum(), is(Response.Status.OK));

            assertThat(response.readEntity(String.class), is(BIZ_EXPECTED));
        } else {
            assertThat("port " + params.port + " should NOT be serving business logic",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        }

        // enabled metrics URI
        response = baseTarget.path(METRICS_URI)
                .path(ENABLED_METRIC)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        if (params.metricsEnabled) {
            assertThat("port " + params.port + " should be serving metrics",
                       response.getStatusInfo().toEnum(), is(Response.Status.OK));

            response = baseTarget.path(METRICS_URI)
                    .path(DISABLED_METRIC)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertThat("Metric " + DISABLED_METRIC + " should be disabled by configuration",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        } else {
            assertThat("port " + params.port + " should NOT be serving metrics",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        }

        response = baseTarget.path(HEALTH_URI)
                .request()
                .get();

        if (params.healthEnabled) {
            assertThat("port " + params.port + " should be serving health checks",
                       response.getStatusInfo().toEnum(), is(Response.Status.OK));
        } else {
            assertThat("port " + params.port + " should NOT be serving health checks",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        }
    }


    private static class Params {
        int port;
        boolean bizEnabled;
        boolean metricsEnabled;
        boolean healthEnabled;

        private Params(int port, boolean bizEnabled, boolean metricsEnabled, boolean healthEnabled) {
            this.port = port;
            this.bizEnabled = bizEnabled;
            this.metricsEnabled = metricsEnabled;
            this.healthEnabled = healthEnabled;
        }

        @Override
        public String toString() {
            return "Port " + port + ": "
                    + (bizEnabled ? "serving business logic" : "")
                    + (metricsEnabled ? "serving metrics" : "")
                    + (healthEnabled ? "serving healthchecks" : "");
        }
    }
}