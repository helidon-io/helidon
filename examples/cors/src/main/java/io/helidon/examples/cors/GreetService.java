/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.cors;

import java.util.Collections;

import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * A simple service to greet you. Examples:
 * <p>
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 * <p>
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 * <p>
 * Change greeting
 * curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 * <p>
 * The message is returned as a JSON object
 */

public class GreetService implements HttpService {

    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());

    GreetService(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Ciao");
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules .get("/", this::getDefaultMessageHandler)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);
    }

    /**
     * Return a worldly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        sendResponse(response, "World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getMessageHandler(ServerRequest request,
                                   ServerResponse response) {
        String name = request.path().pathParameters().get("name");
        sendResponse(response, name);
    }

    private void sendResponse(ServerResponse response, String name) {
        GreetingMessage msg = new GreetingMessage(String.format("%s %s!", greeting, name));
        response.send(msg.forRest());
    }

    private void updateGreetingFromJson(JsonObject jo, ServerResponse response) {

        if (!jo.containsKey(GreetingMessage.JSON_LABEL)) {
            JsonObject jsonErrorObject = JSON_BF.createObjectBuilder()
                    .add("error", "No greeting provided")
                    .build();
            response.status(Http.Status.BAD_REQUEST_400)
                    .send(jsonErrorObject);
            return;
        }

        greeting = GreetingMessage.fromRest(jo).getMessage();
        response.status(Http.Status.NO_CONTENT_204).send();
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updateGreetingHandler(ServerRequest request,
                                       ServerResponse response) {
        JsonObject jo = request.content().as(JsonObject.class);
        updateGreetingFromJson(jo, response);
    }
}
