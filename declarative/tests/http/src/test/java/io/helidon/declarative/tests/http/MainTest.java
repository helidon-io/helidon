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

package io.helidon.declarative.tests.http;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webclient.api.RestClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class MainTest {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private final Http1Client client;
    private final ServiceRegistry registry;
    private final URI serverUri;

    protected MainTest(Http1Client client,
                       ServiceRegistry registry,
                       URI serverUri) {
        this.client = client;
        this.registry = registry;
        this.serverUri = serverUri;
    }

    @Test
    void testRootRoute() {
        var response = client.get("/greet").request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));
        JsonObject json = response.entity();
        assertThat(json.getString("message"), is("Hello World!"));
    }

    @Test
    void testHealthObserver() {
        var response = client.get("/observe/health").request(String.class);
        assertThat(response.status(), is(Status.NO_CONTENT_204));
    }

    @Test
    void testDeadlockHealthCheck() {
        var response = client.get("/observe/health/live/deadlock").request(String.class);
        assertThat(response.status(), is(Status.NO_CONTENT_204));
    }

    @Test
    void testMetricsObserver() {
        var response = client.get("/observe/metrics").request(String.class);
        assertThat(response.status(), is(Status.OK_200));
    }

    @Test
    void testErrorHandler() {
        JsonObject badEntity = JSON.createObjectBuilder().build();

        var response = client.put("/greet/greeting").submit(badEntity, JsonObject.class);
        assertThat(response.status(), is(Status.BAD_REQUEST_400));
        JsonObject entity = response.entity();
        assertThat(entity.getString("error"), is("No greeting provided"));
    }

    @Test
    void testTypedClient() {
        GreetEndpointClient typedClient = registry.get(Lookup.builder()
                                                               .addContract(GreetEndpointClient.class)
                                                               .addQualifier(Qualifier.create(RestClient.Client.class))
                                                               .build());

        String message = typedClient.getDefaultMessageHandlerPlain();
        assertThat(message, is("Hello World!"));

        JsonObject jsonMessage = typedClient.getDefaultMessageHandler();
        assertThat(jsonMessage.getString("message"), is("Hello World!"));

        message = typedClient.failingFallback(serverUri.getAuthority());
        assertThat(message, is("Fallback " + serverUri.getAuthority()));

        message = typedClient.retriable();
        assertThat(message, is("Success"));

        HttpException exception = assertThrows(HttpException.class, typedClient::breaker);
        assertThat(exception.status(), is(Status.FORBIDDEN_403));

        message = typedClient.timeout(Optional.empty());
        assertThat(message, is("Success"));

        exception = assertThrows(HttpException.class, () -> typedClient.timeout(Optional.of(2)));
        assertThat(exception.status(), is(Status.SERVICE_UNAVAILABLE_503));

        jsonMessage = typedClient.getMessageHandler("test");
        assertThat(jsonMessage.getString("message"), is("Hello test!"));

        JsonObject newGreeting = JSON.createObjectBuilder()
                .add("greeting", "Ahoj")
                .build();
        typedClient.updateGreetingHandler(newGreeting);

        newGreeting = JSON.createObjectBuilder()
                .add("greeting", "Hello")
                .build();
        jsonMessage = typedClient.updateGreetingHandlerReturningCurrent(newGreeting);
        assertThat(jsonMessage.getString("message"), is("Ahoj World!"));
    }
}
