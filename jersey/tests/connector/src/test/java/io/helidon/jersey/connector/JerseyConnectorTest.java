/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.util.Arrays;

import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;

/**
 * Tests integration of Jakarta REST client with the Helidon connector that uses
 * WebClient to execute HTTP requests.
 */
@ServerTest
class JerseyConnectorTest {

    private final String baseURI;
    private final Client client;

    JerseyConnectorTest(WebServer webServer) {
        baseURI = "http://localhost:" + webServer.port();
        ClientConfig config = new ClientConfig();
        config.connectorProvider(new HelidonConnectorProvider());       // use Helidon's provider
        client = ClientBuilder.newClient(config);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/basic/get", JerseyConnectorTest::basicGet)
             .post("/basic/post", JerseyConnectorTest::basicPost)
             .get("/basic/getquery", JerseyConnectorTest::basicGetQuery)
             .get("/basic/headers", JerseyConnectorTest::basicHeaders);
    }

    private WebTarget target(String uri) {
        return client.target(baseURI).path(uri);
    }

    static void basicGet(ServerRequest request, ServerResponse response) {
        response.status(Status.OK_200).send("ok");
    }

    static void basicPost(ServerRequest request, ServerResponse response) {
        String entity = request.content().as(String.class);
        response.status(Status.OK_200).send(entity + entity);
    }

    static void basicGetQuery(ServerRequest request, ServerResponse response) {
        String first = request.query().get("first");
        String second = request.query().get("second");
        response.status(Status.OK_200).send(first + second);
    }

    static void basicHeaders(ServerRequest request, ServerResponse response) {
        request.headers()
                .stream()
                .filter(h -> h.name().startsWith("X-TEST"))
                .forEach(response::header);
        response.status(Status.OK_200).send("ok");
    }

    @Test
    public void testBasicGet() {
        try (Response response = target("basic").path("get").request().get()) {
            assertThat(response.getStatus(), is(200));
            assertThat(response.readEntity(String.class), is("ok"));
        }
    }

    @Test
    public void testBasicPost() {
        try (Response response = target("basic").path("post").request()
                .buildPost(Entity.entity("ok", MediaType.TEXT_PLAIN_TYPE)).invoke()) {
            assertThat(response.getStatus(), is(200));
            assertThat(response.readEntity(String.class), is("okok"));
        }
    }

    @Test
    public void queryGetTest() {
        try (Response response = target("basic").path("getquery")
                .queryParam("first", "hello")
                .queryParam("second", "world")
                .request().get()) {
            assertThat(response.getStatus(), is(200));
            assertThat(response.readEntity(String.class), is("helloworld"));
        }
    }

    @Test
    public void testHeaders() {
        String[][] headers = new String[][]{{"X-TEST-ONE", "ONE"}, {"X-TEST-TWO", "TWO"}, {"X-TEST-THREE", "THREE"}};
        MultivaluedHashMap<String, Object> map = new MultivaluedHashMap<>();
        Arrays.stream(headers).forEach(a -> map.add(a[0], a[1]));
        try (Response response = target("basic").path("headers").request().headers(map).get()) {
            assertThat(response.getStatus(), is(200));
            assertThat(response.readEntity(String.class), is("ok"));
            for (int i = 0; i != headers.length; i++) {
                assertThat(response.getHeaders(), hasKey(headers[i][0]));
                assertThat(response.getStringHeaders().getFirst(headers[i][0]), is(headers[i][1]));
            }
        }
    }
}
