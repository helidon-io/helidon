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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link Main}.
 */
class MainTest {
    // Port names
    private static final String PUBLIC_PORT = "default";
    private static final String HEALTH_PORT = "health";
    private static final String METRICS_PORT = "metrics";
    private static final String PRIVATE_PORT = "private";
    private static final String NOTHING_PORT = "nothing";

    private static final String BIZ_URI = "/hello";
    private static final String BIZ_EXPECTED = "Hello World";
    private static final String METRICS_URI = "/mymetrics";
    private static final String ENABLED_METRIC = "base/cpu.availableProcessors";
    private static final String DISABLED_METRIC = "base/thread.count";
    private static final String HEALTH_URI = "/myhealth";
    private static final String PRIVATE_URI = "/private/hello";
    private static final String PRIVATE_EXPECTED = "Private Hello World!!";
    private static final String URI_BASE = "http://localhost:";

    private static Client client;
    private static WebTarget publicTarget;
    private static WebTarget privateTarget;
    private static WebTarget healthTarget;
    private static WebTarget metricsTarget;
    private static WebTarget nothingTarget;

    @BeforeAll
    static void initClass() {
        Main.main(new String[0]);
        Server server = Main.server();
        client = ClientBuilder.newClient();

        int publicPort = server.port();
        int privatePort = server.port(PRIVATE_PORT);
        int healthPort = server.port(HEALTH_PORT);
        int metricsPort = server.port(METRICS_PORT);
        int nothingPort = server.port(NOTHING_PORT);

        publicTarget = client.target(URI_BASE + publicPort);
        privateTarget = client.target(URI_BASE + privatePort);
        healthTarget = client.target(URI_BASE + healthPort);
        metricsTarget = client.target(URI_BASE + metricsPort);
        nothingTarget = client.target(URI_BASE + nothingPort);
    }

    @AfterAll
    static void destroyClass() {
        client.close();
        Main.server().stop();
    }

    static Stream<Params> initParams() {
        return CollectionsHelper.listOf(
                // public port
                new Params(PUBLIC_PORT, BIZ_URI, Status.OK, BIZ_EXPECTED),
                new Params(PUBLIC_PORT, HEALTH_URI, Status.NOT_FOUND),
                new Params(PUBLIC_PORT, METRICS_URI, Status.NOT_FOUND),
                new Params(PUBLIC_PORT, PRIVATE_URI, Status.NOT_FOUND),
                // nothing port
                // when no named routing, serves default routing
                new Params(NOTHING_PORT, BIZ_URI, Status.OK, BIZ_EXPECTED),
                new Params(NOTHING_PORT, HEALTH_URI, Status.NOT_FOUND),
                new Params(NOTHING_PORT, METRICS_URI, Status.NOT_FOUND),
                new Params(NOTHING_PORT, PRIVATE_URI, Status.NOT_FOUND),
                // private port
                new Params(PRIVATE_PORT, BIZ_URI, Status.NOT_FOUND),
                new Params(PRIVATE_PORT, HEALTH_URI, Status.NOT_FOUND),
                new Params(PRIVATE_PORT, METRICS_URI, Status.NOT_FOUND),
                new Params(PRIVATE_PORT, PRIVATE_URI, Status.OK, PRIVATE_EXPECTED),
                // metrics port
                new Params(METRICS_PORT, BIZ_URI, Status.NOT_FOUND),
                new Params(METRICS_PORT, HEALTH_URI, Status.NOT_FOUND),
                new Params(METRICS_PORT, METRICS_URI, Status.OK),
                new Params(METRICS_PORT, PRIVATE_URI, Status.NOT_FOUND),
                new Params(METRICS_PORT, METRICS_URI + "/" + ENABLED_METRIC, Status.OK),
                new Params(METRICS_PORT, METRICS_URI + "/" + DISABLED_METRIC, Status.NOT_FOUND),
                // health port
                new Params(HEALTH_PORT, BIZ_URI, Status.NOT_FOUND),
                new Params(HEALTH_PORT, HEALTH_URI, Status.OK),
                new Params(HEALTH_PORT, METRICS_URI, Status.NOT_FOUND),
                new Params(HEALTH_PORT, PRIVATE_URI, Status.NOT_FOUND)
        ).stream();
    }

    @MethodSource("initParams")
    @ParameterizedTest
    void testEndpoint(Params params) {
        WebTarget webTarget;

        switch (params.portName) {
        case PUBLIC_PORT:
            webTarget = publicTarget;
            break;
        case PRIVATE_PORT:
            webTarget = privateTarget;
            break;
        case HEALTH_PORT:
            webTarget = healthTarget;
            break;
        case METRICS_PORT:
            webTarget = metricsTarget;
            break;
        case NOTHING_PORT:
            webTarget = nothingTarget;
            break;
        default:
            fail("Wrong port name defined: " + params.portName);
            return;
        }

        Response response = webTarget.path(params.path)
                .request()
                .get();

        assertThat("Port " + params.portName + " returned wrong status for " + params.path,
                   response.getStatusInfo().toEnum(),
                   is(params.expectedStatus));

        if (params.expectedString != null) {
            assertThat("Port " + params.portName + " returned wrong entity for " + params.path,
                       response.readEntity(String.class),
                       is(params.expectedString));
        }
    }

    private static class Params {
        private final String portName;
        private final String path;
        private final Status expectedStatus;
        private final String expectedString;

        private Params(String portName, String path, Status expectedStatus, String expectedString) {
            this.portName = portName;
            this.path = path;
            this.expectedStatus = expectedStatus;
            this.expectedString = expectedString;
        }

        private Params(String portName, String path, Status expectedStatus) {
            this.portName = portName;
            this.path = path;
            this.expectedStatus = expectedStatus;
            this.expectedString = null;
        }

        @Override
        public String toString() {
            return portName + ":" + path + " should return " + expectedStatus + (
                    (expectedString == null)
                            ? ""
                            : " (" + expectedString + ")");
        }
    }
}