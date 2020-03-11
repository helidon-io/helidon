/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.gh1487;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.Server;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class PolicyStatementTest {
    private static WebTarget adminTarget;

    private static WebTarget noAtnTarget;
    private static WebTarget userTarget;
    private static Server server;

    @BeforeAll
    static void initClass() {
        server = Server.builder().build();

        HttpAuthenticationFeature adminAuth = HttpAuthenticationFeature.basicBuilder()
                .credentials("jack", "password")
                .build();

        HttpAuthenticationFeature userAuth = HttpAuthenticationFeature.basicBuilder()
                .credentials("jill", "password")
                .build();

        Client adminClient = ClientBuilder.newBuilder()
                .register(adminAuth)
                .build();

        Client userClient = ClientBuilder.newBuilder()
                .register(userAuth)
                .build();

        Client noAtnClient = ClientBuilder.newClient();

        server.start();

        int port = server.port();
        String uri = "http://localhost:" + port;

        adminTarget = adminClient.target(uri);
        userTarget = userClient.target(uri);
        noAtnTarget = noAtnClient.target(uri);

    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testWrongPolicy() {
        Response response = adminTarget.path("wrong-syntax")
                .request()
                .get();

        assertThat(response.getStatus(), is(500));

        response = userTarget.path("wrong-syntax")
                .request()
                .get();

        assertThat(response.getStatus(), is(500));

        response = noAtnTarget.path("wrong-syntax")
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
    }

    @Test
    void testGoodPolicy() {
        Response response = adminTarget.path("admin")
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        response = userTarget.path("admin")
                .request()
                .get();

        assertThat(response.getStatus(), is(403));

        response = noAtnTarget.path("admin")
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
    }
}