/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.guides.se.restfulwebservice;

//tag::imports[]
import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
// end::imports[]

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
 * curl -X PUT http://localhost:8080/greet/greeting/Hola
 *
 * The message is returned as a JSON object
 */

public class GreetService implements Service {

    /**
     * This gets config from application.yaml on classpath
     * and uses "app" section.
     */
    // tag::CONFIG[]
    private static final Config CONFIG = Config.create().get("app"); // <1>
    // end::CONFIG[]

    /**
     * The config value for the key {@code greeting}.
     */
    // tag::greetingDef[]
    private static String greeting = CONFIG.get("greeting").asString("Ciao"); // <2>
    // end::greetingDef[]

    /**
     * A service registers itself by updating the routine rules.
     * @param rules the routing rules.
     */
    // tag::update[]
    @Override
    public final void update(final Routing.Rules rules) { // <1>
        rules
            .get("/", this::getDefaultMessage) //<2>
            .get("/{name}", this::getMessage) //<3>
            .put("/greeting/{greeting}", this::updateGreeting); //<4>
    }
    // end::update[]

    /**
     * Return a wordly greeting message.
     * @param request the server request
     * @param response the server response
     */
    // tag::getDefaultMessage[]
    private void getDefaultMessage(final ServerRequest request,
                                   final ServerResponse response) {
        String msg = String.format("%s %s!", greeting, "World"); // <1>

        JsonObject returnObject = Json.createObjectBuilder()
                .add("message", msg) // <2>
                .build();
        response.send(returnObject); // <3>
    }
    //end::getDefaultMessage[]

    /**
     * Return a greeting message using the name that was provided.
     * @param request the server request
     * @param response the server response
     */
    // tag::getMessage[]
    private void getMessage(final ServerRequest request,
                            final ServerResponse response) {
        String name = request.path().param("name"); // <1>
        String msg = String.format("%s %s!", greeting, name);

        JsonObject returnObject = Json.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }
    // end::getMessage[]

    /**
     * Set the greeting to use in future messages.
     * @param request the server request
     * @param response the server response
     */
    // tag::updateGreeting[]
    private void updateGreeting(final ServerRequest request,
                                final ServerResponse response) {
        greeting = request.path().param("greeting"); // <1>

        JsonObject returnObject = Json.createObjectBuilder() // <2>
                .add("greeting", greeting)
                .build();
        response.send(returnObject);
    }
    // end::updateGreeting[]
}
