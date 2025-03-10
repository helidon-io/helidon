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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Default;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Configuration;
import io.helidon.http.Http;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * A simple endpoint to greet you. Examples:
 * <p>
 * Get default greeting message:
 * {@code curl -X GET http://localhost:8080/greet}
 * <p>
 * Get greeting message for Joe:
 * {@code curl -X GET http://localhost:8080/greet/Joe}
 * <p>
 * Change greeting
 * {@code curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting}
 * <p>
 * The message is returned as a JSON object.
 */
@Service.Singleton
@Http.Path("/greet")
class GreetEndpoint {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    /**
     * The config value for the key {@code greeting}.
     */
    private final AtomicReference<String> greeting = new AtomicReference<>();

    @Service.Inject
    GreetEndpoint(@Configuration.Value("app.greeting") @Default.Value("Ciao") String greeting) {
        this.greeting.set(greeting);
    }

    /**
     * Return a worldly greeting message.
     */
    @Http.GET
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    JsonObject getDefaultMessageHandler() {
        return response("World");
    }

    /**
     * Return a greeting message using the name that was provided.
     */
    @Http.GET
    @Http.Path("/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    JsonObject getMessageHandler(@Http.PathParam("name") String name,
                                 @Http.HeaderParam("X-TEST") @Default.Int(42) int header,
                                 @Http.QueryParam("query-param") @Default.Double(12.0) double queryParam) {
        return response(name);
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param greetingMessage the entity
     */
    @Http.PUT
    @Http.Path("/greeting")
    @Http.Status(Status.NO_CONTENT_204_CODE)
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    void updateGreetingHandler(@Http.Entity JsonObject greetingMessage,
                               @Http.HeaderParam("X-TEST") @Default.Value("42") int header) {
        if (!greetingMessage.containsKey("greeting")) {
            // mapped by QuickstartErrorHandler
            throw new QuickstartException(Status.BAD_REQUEST_400, "No greeting provided");
        }

        greeting.set(greetingMessage.getString("greeting"));
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param greetingMessage the entity
     * @return Hello World message
     */
    @Http.POST
    @Http.Path("/greeting")
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    JsonObject updateGreetingHandlerReturningCurrent(@Http.Entity JsonObject greetingMessage,
                                                     @Http.HeaderParam("X-TEST") @Default.Value("42") String header) {
        if (!greetingMessage.containsKey("greeting")) {
            // mapped by QuickstartErrorHandler
            throw new QuickstartException(Status.BAD_REQUEST_400, "No greeting provided");
        }
        JsonObject response = response("World!");
        greeting.set(greetingMessage.getString("greeting"));
        return response;
    }

    private JsonObject response(String name) {
        String msg = String.format("%s %s!", greeting.get(), name);

        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }

}
