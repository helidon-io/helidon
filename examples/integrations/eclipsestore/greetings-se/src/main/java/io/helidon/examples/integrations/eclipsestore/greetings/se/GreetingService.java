/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.eclipsestore.greetings.se;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.integrations.eclipsestore.core.EmbeddedStorageManagerBuilder;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
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
 * Change greeting:
 * curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}'
 * http://localhost:8080/greet/greeting
 * <p>
 * Get the logs:
 * curl -X GET http://localhost:8080/greet/logs
 * <p>
 * The message is returned as a JSON object
 */

public final class GreetingService implements HttpService {

    /**
     * Greeting reference.
     */
    private final AtomicReference<String> greeting = new AtomicReference<>();

    /**
     * Json factory.
     */
    private static final JsonBuilderFactory JSON =
            Json.createBuilderFactory(Collections.emptyMap());

    /**
     * Eclipse store context.
     */
    private final GreetingServiceEclipseStoreContext context;

    /**
     *  Create greeting service.
     * @param config configuration.
     */
    GreetingService(final Config config) {
        greeting.set(
                config.get("app.greeting").asString().orElse("Ciao"));

        context = new GreetingServiceEclipseStoreContext(
                EmbeddedStorageManagerBuilder.create(
                        config.get("eclipsestore")));
        // we need to initialize the root element first
        // if we do not wait here, we have a race where HTTP method
        // may be invoked before we initialize root
        context.start();
        context.initRootElement();
    }

    @Override
    public void routing(final HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler)
                .get("/logs", this::getLog)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);
    }

    private void getLog(final ServerRequest request,
                        final ServerResponse response) {
        JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
        context.getLogs().forEach((entry) -> arrayBuilder.add(
                JSON.createObjectBuilder()
                        .add("name", entry.name())
                        .add("time", entry.dateTime().toString())));
        response.send(arrayBuilder.build());
    }

    /**
     * Return a worldly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(final ServerRequest request,
                                          final ServerResponse response) {
        sendResponse(response, "World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getMessageHandler(final ServerRequest request,
                                   final ServerResponse response) {
        String name = request.path().pathParameters().get("name");
        sendResponse(response, name);
    }

    private void sendResponse(final ServerResponse response,
                              final String name) {
        String msg = String.format("%s %s!", greeting.get(), name);

        context.addLogEntry(name);

        JsonObject returnObject = JSON.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    private void updateGreetingFromJson(final JsonObject jo,
                                        final ServerResponse response) {
        if (!jo.containsKey("greeting")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "No greeting provided")
                    .build();
            response.status(Status.BAD_REQUEST_400)
                    .send(jsonErrorObject);
            return;
        }

        greeting.set(jo.getString("greeting"));
        response.status(Status.NO_CONTENT_204).send();
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updateGreetingHandler(final ServerRequest request,
                                       final ServerResponse response) {
        JsonObject jsonObject = request.content().as(JsonObject.class);
        updateGreetingFromJson(jsonObject, response);
    }

}
