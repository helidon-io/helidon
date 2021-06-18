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

package io.helidon.tests.integration.security.pathparams;

import java.util.Base64;
import java.util.function.Function;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AdminTest {
    private static Server server;
    private static Client client;

    private static Function<String, WebTarget> target;

    @BeforeAll
    static void initClass() {
        server = Server.create()
                .start();

        client = ClientBuilder.newClient();

        int port = server.port();
        String baseUri = "http://localhost:" + port;
        target = p -> client.target(baseUri + p);
    }

    @AfterAll
    static void destroyClass() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testGreetResource() {
        String response = target.apply("/greet").request().get(String.class);
        assertThat(response, is("Hello World"));
    }

    @Test
    void testAdminResource1() {
        Response response = target.apply("/admin").request().get();
        assertThat(response.getStatus(), is(Http.Status.UNAUTHORIZED_401.code()));
    }

    @Test
    void testAdminResource2() {
        Response response = target.apply("/admin;a=b").request().get();
        assertThat(response.getStatus(), is(Http.Status.UNAUTHORIZED_401.code()));
    }

    @Test
    void testAdminResource3() {
        Response response = target.apply("/admin;a=b;c=d").request().get();
        assertThat(response.getStatus(), is(Http.Status.UNAUTHORIZED_401.code()));
    }

    @Test
    void testAdminResource4() {
        Response response = target.apply("/admin;").request().get();
        assertThat(response.getStatus(), is(Http.Status.UNAUTHORIZED_401.code()));
    }

    @Test
    void testAdminResource5() {
        Response response = target.apply("/admin/;").request().get();
        assertThat(response.getStatus(), is(Http.Status.UNAUTHORIZED_401.code()));
    }

    @Test
    void testAdminResource6() {
        Response response = target.apply("/admin/;/").request().get();
        assertThat(response.getStatus(), is(Http.Status.UNAUTHORIZED_401.code()));
    }

    @Test
    void testAdminResourceBasicAuth1() {
        Response response = target.apply("/admin").request()
                .header("Authorization", basic("success"))
                .get();
        assertThat(response.getStatus(), is(Http.Status.OK_200.code()));
    }

    @Test
    void testAdminResourceBasicAuth2() {
        Response response = target.apply("/admin;a=b").request()
                .header("Authorization", basic("success"))
                .get();
        assertThat(response.getStatus(), is(Http.Status.OK_200.code()));
    }

    private String basic(String user) {
        String uap = user + ":password";
        return "basic " + Base64.getEncoder().encodeToString(uap.getBytes());
    }
}
