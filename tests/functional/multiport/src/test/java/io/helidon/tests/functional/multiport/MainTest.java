/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Main}.
 */
@HelidonTest
class MainTest {
    private static final String BIZ_URI = "/hello";
    private static final String BIZ_EXPECTED = "Hello World";
    private static final String METRICS_URI = "/mymetrics";
    private static final String ENABLED_METRIC = "base/cpu.availableProcessors";
    private static final String DISABLED_METRIC = "base/thread.count";
    private static final String HEALTH_URI = "/myhealth";

    private static Client client;

    private final ServerCdiExtension server;

    @Inject
    MainTest(ServerCdiExtension server) {
        this.server = server;
        client = ClientBuilder.newClient();
    }

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void destroyClass() {
        client.close();
    }

    static Stream<Params> initParams() {
        return Stream.of(
                new Params("@default", true, false, false),
                new Params("health", false, false, true),
                new Params("metrics", false, true, false),
                // when no named routing, serves default routing
                new Params("nothing", true, false, false)
        );
    }

    @MethodSource("initParams")
    @ParameterizedTest
    void testEndpoint(Params params) {
        int port = server.port(params.socketName);
        String base = "http://localhost:" + port;
        WebTarget baseTarget = client.target(base);

        Response response;

        // business URI
        response = baseTarget.path(BIZ_URI)
                .request()
                .get();

        if (params.bizEnabled) {
            assertThat("port " + port + " (" + params.socketName + ") should be serving business logic",
                       response.getStatusInfo().toEnum(), is(Response.Status.OK));

            assertThat(response.readEntity(String.class), is(BIZ_EXPECTED));
        } else {
            assertThat("port " + port + " (" + params.socketName + ") should NOT be serving business logic",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        }

        // enabled metrics URI
        response = baseTarget.path(METRICS_URI)
                .path(ENABLED_METRIC)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        if (params.metricsEnabled) {
            assertThat("port " + port + " (" + params.socketName + ") should be serving metrics",
                       response.getStatusInfo().toEnum(), is(Response.Status.OK));

            response = baseTarget.path(METRICS_URI)
                    .path(DISABLED_METRIC)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertThat("Metric " + DISABLED_METRIC + " should be disabled by configuration",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        } else {
            assertThat("port " + port + " (" + params.socketName + ") should NOT be serving metrics",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        }

        response = baseTarget.path(HEALTH_URI)
                .request()
                .get();

        if (params.healthEnabled) {
            assertThat("port " + port + " (" + params.socketName + ") should be serving health checks",
                       response.getStatusInfo().toEnum(), is(Response.Status.OK));
        } else {
            assertThat("port " + port + " (" + params.socketName + ") should NOT be serving health checks",
                       response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));
        }
    }


    private static class Params {
        private final String socketName;
        private final boolean bizEnabled;
        private final boolean metricsEnabled;
        private final boolean healthEnabled;

        private Params(String socketName, boolean bizEnabled, boolean metricsEnabled, boolean healthEnabled) {
            this.socketName = socketName;
            this.bizEnabled = bizEnabled;
            this.metricsEnabled = metricsEnabled;
            this.healthEnabled = healthEnabled;
        }

        @Override
        public String toString() {
            return "Socket " + socketName + ": "
                    + (bizEnabled ? "serving business logic" : "")
                    + (metricsEnabled ? "serving metrics" : "")
                    + (healthEnabled ? "serving healthchecks" : "");
        }
    }
}