/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.examples.quickstart.se;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonException;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * A simple service to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 *
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 *
 * Change greeting
 * curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 *
 * The message is returned as a JSON object
 */

public class GreetService implements Service {

    /**
     * The config value for the key {@code greeting}.
     */
    private final AtomicReference<String> greeting = new AtomicReference<>();

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static final Logger LOGGER = Logger.getLogger(GreetService.class.getName());

    GreetService(Config config) {
        greeting.set(config.get("app.greeting").asString().orElse("Ciao"));
    }

    /**
     * A service registers itself by updating the routing rules.
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules
            .get("/", this::getDefaultMessageHandler)
            .get("/{name}", this::getMessageHandler)
            .put("/greeting", this::updateGreetingHandler);
    }

    /**
     * Return a wordly greeting message.
     * @param request the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request,
                                   ServerResponse response) {
        sendResponse(response, "World");
    }

    /**
     * Return a greeting message using the name that was provided.
     * @param request the server request
     * @param response the server response
     */
    private void getMessageHandler(ServerRequest request,
                            ServerResponse response) {
        String name = request.path().param("name");
        sendResponse(response, name);
    }

    private void sendResponse(ServerResponse response, String name) {
        String msg = String.format("%s %s!", greeting.get(), name);

        JsonObject returnObject = JSON.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    private static <T> T processErrors(Throwable ex, ServerRequest request, ServerResponse response) {

         if (ex.getCause() instanceof JsonException){

            LOGGER.log(Level.FINE, "Invalid JSON", ex);
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                .add("error", "Invalid JSON")
                .build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
        }  else {

            LOGGER.log(Level.FINE, "Internal error", ex);
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                .add("error", "Internal error")
                .build();
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(jsonErrorObject);
        }

        return null;
    }

    private void updateGreetingFromJson(JsonObject jo, ServerResponse response) {

        if (!jo.containsKey("greeting")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "No greeting provided")
                    .build();
            response.status(Http.Status.BAD_REQUEST_400)
                    .send(jsonErrorObject);
            return;
        }

        greeting.set(jo.getString("greeting"));
        response.status(Http.Status.NO_CONTENT_204).send();
    }

    /**
     * Set the greeting to use in future messages.
     * @param request the server request
     * @param response the server response
     */
    private void updateGreetingHandler(ServerRequest request,
                                       ServerResponse response) {
        request.content().as(JsonObject.class)
            .thenAccept(jo -> updateGreetingFromJson(jo, response))
            .exceptionally(ex -> processErrors(ex, request, response));
    }

}
