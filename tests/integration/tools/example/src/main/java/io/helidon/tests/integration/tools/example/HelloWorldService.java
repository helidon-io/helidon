/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.Optional;

import javax.json.Json;
import javax.json.JsonValue;

import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;


/**
 * Sample web service.
 */
public class HelloWorldService implements Service {

    private final DbClient dbClient;

    /**
     * Creates an instance of common web service code for testing application.
     *
     * @param dbClient DbClient instance
     */
    public HelloWorldService(final DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/sendHelloWorld", this::sendHelloWorld)
                .get("/verifyHello", this::verifyHello)
                .get("/personalHelloWorld", this::personalHelloWorld);
    }

    // Returns JSON object with "Hello World!" String.
    private void sendHelloWorld(final ServerRequest request, final ServerResponse response) {
        JsonValue hw = Json.createValue("Hello World!");
        response.send(okStatus(hw));
    }

    // Check whether provided HTTP query parameter "value" contains word "hello".
    private void verifyHello(final ServerRequest request, final ServerResponse response) {
        String value = param(request, "value");
        if (value.toLowerCase().contains("hello")) {
            response.send(okStatus(JsonValue.NULL));
        } else {
            response.send(
                    exceptionStatus(
                            new RemoteTestException(
                                    String.format("Value \"%s\" does not contain string \"hello\"", value))));
        }
    }

    // Returns personalized "Hello" for known nicks or "Hello World!" otherwise.
    private void personalHelloWorld(final ServerRequest request, final ServerResponse response) {
        String nick = param(request, "nick");
        dbClient.execute(
                exec -> exec
                    .createNamedGet("get-name")
                .addParam("nick", nick)
                .execute())
                .thenAccept(maybeDbRow -> {
                    maybeDbRow.ifPresentOrElse(
                            dbRow -> response.send(
                                    okStatus(
                                            Json.createValue(
                                                    String.format("Hello %s!", dbRow.column("name").as(String.class))))),
                            () -> response.send(
                                    okStatus(
                                            Json.createValue(
                                                    "Hello World!"))));
                })
                .exceptionally(t -> {
                    response.send(exceptionStatus(t));
                    return null;
                });
    }

    /*
     * Retrieve HTTP query parameter value from request.
     *
     * @param request HTTP request context
     * @param name query parameter name
     * @return query parameter value
     * @throws RemoteTestException when no parameter with given name exists in request
     */
    private static String param( final ServerRequest request, final String name) {
        Optional<String> maybeParam = request.queryParams().first(name);
        if (maybeParam.isPresent()) {
            return maybeParam.get();
        } else {
            throw new RemoteTestException(String.format("Query parameter %s is missing.", name));
        }
    }

}
