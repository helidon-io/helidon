/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    @Inject
    private ServerCdiExtension serverCdiExtension;

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
    }

    @Test
    void testEndpoints() {
        String base = "http://localhost:";
        Response response;
        WebTarget baseTarget;

        final int privatePort = serverCdiExtension.port("private");
        final int adminPort = serverCdiExtension.port("admin");

        // Probe PUBLIC port
        response = publicWebTarget.path("/hello")
                .request()
                .get();
        assertThat("default port should be serving public resource",
                response.getStatusInfo().toEnum(), is(Response.Status.OK));
        assertThat("default port should return public data",
                response.readEntity(String.class), is("Public Hello World!!"));

        response = publicWebTarget.path("/private/hello")
                .request()
                .get();
        assertThat("default port should NOT be serving private resource",
                response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));

        response = publicWebTarget.path("/health")
                .request()
                .get();
        assertThat("default port should NOT be serving health",
                response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));

        response = publicWebTarget.path("/metrics")
                .request()
                .get();
        assertThat("default port should NOT be serving metrics",
                response.getStatusInfo().toEnum(), is(Response.Status.NOT_FOUND));

        // Probe PRIVATE port
        baseTarget = client.target(base + privatePort);
        response = baseTarget.path("/private/hello")
                .request()
                .get();
        assertThat("port " + privatePort  + " should be serving private resource",
                response.getStatusInfo().toEnum(), is(Response.Status.OK));
        assertThat("port " + privatePort  + " should return private data",
                response.readEntity(String.class), is("Private Hello World!!"));

        // Probe ADMIN port
        baseTarget = client.target(base + adminPort);
        response = baseTarget.path("/health")
                .request()
                .get();
        assertThat("port " + adminPort  + " should be serving health",
                response.getStatusInfo().toEnum(), is(Response.Status.OK));

        response = baseTarget.path("/metrics")
                .request()
                .get();
        assertThat("port " + adminPort  + " should be serving metrics",
                response.getStatusInfo().toEnum(), is(Response.Status.OK));
    }
}