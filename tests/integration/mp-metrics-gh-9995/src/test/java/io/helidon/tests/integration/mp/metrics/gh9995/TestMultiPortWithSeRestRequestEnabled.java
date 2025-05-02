/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.mp.metrics.gh9995;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@HelidonTest
@AddConfig(key = "server.sockets.0.port", value = "0")      // Force ports to be dynamically allocated
@AddConfig(key = "server.sockets.1.port", value = "0")
class TestMultiPortWithSeRestRequestEnabled {

    // For non-public endpoints (admin).
    private static Client client;

    // For public endpoint(s).
    private final WebTarget publicWebTarget;
    private final ServerCdiExtension serverCdiExtension;

    @BeforeAll
    static void beforeAll() {
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void afterAll() {
        client.close();
    }

    @Inject
    TestMultiPortWithSeRestRequestEnabled(WebTarget webTarget, ServerCdiExtension serverCdiExtension) {
        this.publicWebTarget = webTarget;
        this.serverCdiExtension = serverCdiExtension;
    }

    @Test
    void testPublicEndpoint() {
        Response response = publicWebTarget.path("/greet").request(MediaType.TEXT_PLAIN).get();

        // Without the fix, accessing this resource fails in the server.
        assertThat("Greet response status", response.getStatus(), equalTo(200));

    }

    @Test
    void testSeFilteredMpRequest() {
        int privatePort = serverCdiExtension.port("private");
        WebTarget privateTarget = client.target("http://localhost:" + privatePort);
        Response response = privateTarget.path("/private/hello").request(MediaType.TEXT_PLAIN).get();
        assertThat("Private response status", response.getStatus(), equalTo(200));
    }
}
