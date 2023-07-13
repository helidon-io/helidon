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
package io.helidon.tests.integration.tools.example;

import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;


/**
 * Sample web service.
 */
public class HelloWorldService implements HttpService {

    private final DbClient dbClient;

    /**
     * Creates an instance of common web service code for testing application.
     *
     * @param dbClient DbClient instance
     */
    public HelloWorldService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/sendHelloWorld", this::sendHelloWorld)
                .get("/verifyHello", this::verifyHello)
                .get("/personalHelloWorld", this::personalHelloWorld);
    }

    // Returns JSON object with "Hello World!" String.
    private void sendHelloWorld(ServerRequest request, ServerResponse response) {
        JsonValue hw = Json.createValue("Hello World!");
        response.send(okStatus(hw));
    }

    // Check whether provided HTTP query parameter "value" contains word "hello".
    private void verifyHello(ServerRequest request, ServerResponse response) {
        String value = param(request, "value");
        if (value.toLowerCase().contains("hello")) {
            response.send(okStatus(JsonValue.NULL));
        } else {
            throw new RemoteTestException("Value \"%s\" does not contain string \"hello\"", value);
        }
    }

    // Returns personalized "Hello" for known nicks or "Hello World!" otherwise.
    private void personalHelloWorld(ServerRequest request, ServerResponse response) {
        String nick = param(request, "nick");
        String message = dbClient.execute()
                .createNamedGet("get-name")
                .addParam("nick", nick)
                .execute()
                .map(row -> String.format("Hello %s!", row.column("name").as(String.class)))
                .orElse("Hello World!");
        response.send(okStatus(Json.createValue(message)));
    }

    // Retrieve HTTP query parameter value from request.
    private static String param(ServerRequest request, String name) {
        return request.query()
                .first(name)
                .orElseThrow(() -> new RemoteTestException("Query parameter %s is missing.", name));
    }
}
