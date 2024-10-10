/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.packaging.inject;

import io.helidon.service.inject.api.Configuration;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * A simple service to greet you.
 */
@Injection.Singleton
@Service.ExternalContracts(HttpFeature.class)
class GreetFeature implements HttpFeature {

    /**
     * The config value for the key {@code greeting}.
     */
    private final String greeting;

    GreetFeature(@Configuration.Value("app.greeting:Ciao") String greetingValue) {
        this.greeting = greetingValue;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.register("/ws", this::routing);
    }

    void routing(HttpRules rules) {
        rules
                .get("/greet", this::getDefaultMessageHandler)
                .get("/greet/{name}", this::getMessageHandler);

    }

    /**
     * Return a worldly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request,
                                          ServerResponse response) {
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
        String msg = String.format("%s %s!", greeting, name);

        response.send(msg);
    }
}
