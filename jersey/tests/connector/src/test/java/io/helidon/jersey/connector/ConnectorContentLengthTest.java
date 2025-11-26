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
package io.helidon.jersey.connector;

import java.io.UncheckedIOException;
import java.util.NoSuchElementException;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ConnectorContentLengthTest {
    // must be larger than 8KB used by Jersey for writing
    private static final String LARGE_ENTITY = "A".repeat(128 * 1024);

    private final String baseURI;
    private final Client client;

    ConnectorContentLengthTest(WebServer webServer) {
        baseURI = "http://localhost:" + webServer.port();
        ClientConfig config = new ClientConfig();
        config.connectorProvider(HelidonConnectorProvider.create());       // use Helidon's provider
        client = ClientBuilder.newClient(config);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/largeEntity", ConnectorContentLengthTest::largeEntity);
    }

    static void largeEntity(ServerRequest request, ServerResponse response) {
        try {
            Header header = request.headers().get(HeaderNames.CONTENT_LENGTH);
            request.content().as(String.class);     // consume entity
            response.status(Status.OK_200).send(header.getString());
        } catch (NoSuchElementException e) {
            response.status(Status.BAD_REQUEST_400).send();
        }
    }

    @Test
    public void testLargeEntity() {
        try (Response response = client.target(baseURI)
                .path("largeEntity")
                .request()
                .header("Content-Length", LARGE_ENTITY.length())
                .post(Entity.entity(LARGE_ENTITY, MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(200));
            String entity = response.readEntity(String.class);
            assertThat(entity, is(String.valueOf(LARGE_ENTITY.length())));
        }
    }

    @Test
    public void testLargeEntityBadLength() {
        try (Response response = client.target(baseURI)
                .path("largeEntity")
                .request()
                .header("Content-Length", LARGE_ENTITY.length() + 1)    // incorrect
                .post(Entity.entity(LARGE_ENTITY, MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(200));
            String entity = response.readEntity(String.class);
            assertThat(entity, is(String.valueOf(LARGE_ENTITY.length())));
        } catch (ProcessingException e) {
            assertThat(e.getCause(), instanceOf(UncheckedIOException.class));        // bad length
        }
    }
}
