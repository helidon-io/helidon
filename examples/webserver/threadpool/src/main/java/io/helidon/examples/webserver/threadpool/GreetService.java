/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.threadpool;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

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

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final Logger LOGGER = Logger.getLogger(GreetService.class.getName());
    private final ExecutorService myThreadPool;
    private final AtomicReference<String> greeting = new AtomicReference<>();

    GreetService(Config config) {
        greeting.set(config.get("app.greeting").asString().orElse("Ciao"));

        // Build a thread pool using the configuration
        myThreadPool = ThreadPoolSupplier.builder().config(config.get("application-thread-pool")).build().get();
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler)
                .get("/slowly/{name}", this::getMessageSlowlyHandler)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        sendResponse(response, "World");
    }

    private void getMessageHandler(ServerRequest request, ServerResponse response) {
        String name = request.path().pathParameters().value("name");
        sendResponse(response, name);
    }

    private void getMessageSlowlyHandler(ServerRequest request, ServerResponse response) {
        String name = request.path().pathParameters().value("name");

        // One way to pass data to new thread is to use Context
        request.context().register("NAME_PARAM", name + "_from_context");

        int sleepSeconds = request.query().first("sleep").map(Integer::parseInt).orElse(3);

        // Another way, just pass via Runnable.
        myThreadPool.submit(() -> sendResponseSlowly(response, name, sleepSeconds));
    }

    /**
     * Send a response slowly. This simulates blocking business logic.
     *
     * @param response     server response
     * @param name         name to greet
     * @param sleepSeconds artificial delay to simulate blocking business logic
     */
    private void sendResponseSlowly(ServerResponse response, String name, int sleepSeconds) {

        // Fetch NAME_PARAM from Context
        String nameFromContext = Contexts.context()
                .flatMap(ctx -> ctx.get("NAME_PARAM", String.class))
                .orElseThrow(() -> new IllegalStateException("No NAME_PARAM in current context"));

        LOGGER.info("Name from method parameter: " + name + ". Name from context: " + nameFromContext);

        // Simulate blocking business logic
        try {
            Thread.sleep(sleepSeconds * 1000L);
        } catch (InterruptedException ignored) {
        }

        sendResponse(response, name);
    }

    private void sendResponse(ServerResponse response, String name) {
        LOGGER.info("Response sent by thread " + Thread.currentThread());

        String msg = String.format("%s %s!", greeting.get(), name);

        JsonObject returnObject = JSON.createObjectBuilder()
                .add("message", msg)
                .add("thread", Thread.currentThread().toString())
                .build();
        response.send(returnObject);
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

    private void updateGreetingHandler(ServerRequest request, ServerResponse response) {
        JsonObject jsonObject = request.content().as(JsonObject.class);
        updateGreetingFromJson(jsonObject, response);
    }
}
