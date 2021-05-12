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

package io.helidon.examples.microprofile.multiport;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Main}.
 */
@HelidonTest
@AddConfig(key = "server.sockets.0.port", value = "0")      // Force ports to be dynamically allocated
@AddConfig(key = "server.sockets.1.port", value = "0")
class MainTest {

    // Used for the default (public) port which HelidonTest will configure for us
    @Inject
    private WebTarget publicWebTarget;

    // Used for other (private, admin) ports
    private static Client client;

    // Port names
    private static final String ADMIN_PORT = "admin";
    private static final String PRIVATE_PORT = "private";
    private static final String PUBLIC_PORT = "default";

    private static final String BASE_URL = "http://localhost:";

    // Needed to get values of dynamically allocated admin and private ports
    @Inject
    private ServerCdiExtension serverCdiExtension;

    static Stream<Params> initParams() {
        final String PUBLIC_PATH = "/hello";
        final String PRIVATE_PATH = "/private/hello";
        final String HEALTH_PATH = "/health";
        final String METRICS_PATH = "/health";

        return List.of(
                new Params(PUBLIC_PORT, PUBLIC_PATH, Status.OK),
                new Params(PUBLIC_PORT, PRIVATE_PATH, Status.NOT_FOUND),
                new Params(PUBLIC_PORT, HEALTH_PATH, Status.NOT_FOUND),
                new Params(PUBLIC_PORT, METRICS_PATH, Status.NOT_FOUND),

                new Params(PRIVATE_PORT, PUBLIC_PATH, Status.NOT_FOUND),
                new Params(PRIVATE_PORT, PRIVATE_PATH, Status.OK),
                new Params(PRIVATE_PORT, HEALTH_PATH, Status.NOT_FOUND),
                new Params(PRIVATE_PORT, METRICS_PATH, Status.NOT_FOUND),

                new Params(ADMIN_PORT, PUBLIC_PATH, Status.NOT_FOUND),
                new Params(ADMIN_PORT, PRIVATE_PATH, Status.NOT_FOUND),
                new Params(ADMIN_PORT, HEALTH_PATH, Status.OK),
                new Params(ADMIN_PORT, METRICS_PATH, Status.OK)
        ).stream();
    }

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void testPortAccess(Params params) {

        WebTarget webTarget = publicWebTarget;
        if (!PUBLIC_PORT.equals(params.portName)) {
            webTarget = client.target(BASE_URL + serverCdiExtension.port(params.portName));
        }

        Response response = webTarget.path(params.path)
                .request()
                .get();
        assertThat(webTarget.getUri() + " returned incorrect HTTP status",
                response.getStatusInfo().toEnum(), is(params.httpStatus));
    }

    @Test
    void testEndpoints() {
        Response response;

        // Probe PUBLIC port
        response = publicWebTarget.path("/hello")
                .request()
                .get();
        assertThat("default port should be serving public resource",
                response.getStatusInfo().toEnum(), is(Response.Status.OK));
        assertThat("default port should return public data",
                response.readEntity(String.class), is("Public Hello World!!"));

        // Probe PRIVATE port
        int privatePort = serverCdiExtension.port(PRIVATE_PORT);
        WebTarget baseTarget = client.target(BASE_URL + privatePort);
        response = baseTarget.path("/private/hello")
                .request()
                .get();
        assertThat("port " + privatePort  + " should be serving private resource",
                response.getStatusInfo().toEnum(), is(Response.Status.OK));
        assertThat("port " + privatePort  + " should return private data",
                response.readEntity(String.class), is("Private Hello World!!"));
    }

    private static class Params {
        String portName;
        String path;
        Response.Status httpStatus;

        private Params(String portName, String path, Response.Status httpStatus) {
            this.portName = portName;
            this.path = path;
            this.httpStatus = httpStatus;
        }

        @Override
        public String toString() {
            return portName + ":" + path + " should return "  + httpStatus;
        }
    }
}
