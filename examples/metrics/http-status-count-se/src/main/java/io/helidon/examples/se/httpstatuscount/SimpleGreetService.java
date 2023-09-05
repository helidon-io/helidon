/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.se.httpstatuscount;

import java.util.Collections;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
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
 * curl -X GET http://localhost:8080/simple-greet
 * <p>
 * The message is returned as a JSON object
 */
public class SimpleGreetService implements HttpService {

    private static final Logger LOGGER = Logger.getLogger(SimpleGreetService.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final MeterRegistry registry = Metrics.globalRegistry();
    private final Counter accessCtr = registry.getOrCreate(Counter.builder("accessctr"));

    private final String greeting;

    SimpleGreetService(Config config) {
        greeting = config.get("app.greeting").asString().orElse("Ciao");
    }


    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler);
        rules.get("/greet-count", this::countAccess, this::getDefaultMessageHandler);
    }

    /**
     * Return a worldly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        String msg = String.format("%s %s!", greeting, "World");
        LOGGER.info("Greeting message is " + msg);
        JsonObject returnObject = JSON.createObjectBuilder()
                                      .add("message", msg)
                                      .build();
        response.send(returnObject);
    }


    private void countAccess(ServerRequest request, ServerResponse response) {
        accessCtr.increment();
        response.next();
    }
}
