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

//tag::importsStart[]
import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.config.Config;
//end::importsStart[]
//tag::importsHelidonMetrics[]
import io.helidon.metrics.RegistryFactory;
//end::importsHelidonMetrics[]
//tag::importsWebServer[]
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
// end::importsWebServer[]
// tag::importsMPMetrics[]
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
// end::importsMPMetrics[]

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
    private String greeting = CONFIG.get("greeting").asString("Ciao"); // <2>
    // end::greetingDef[]

    /**
     * Create metric registry.
     */
    // tag::metricsRegistration[]
    private final MetricRegistry registry = RegistryFactory.getRegistryFactory().get()
            .getRegistry(MetricRegistry.Type.APPLICATION); // <1>
    // end::metricsRegistration[]

    /**
     * Counter for all requests to this service.
     */
    // tag::counterRegistration[]
    private final Counter greetCounter = registry.counter("accessctr"); // <2>
    // end::counterRegistration[]

    /**
     * A service registers itself by updating the routing rules.
     * @param rules the routing rules.
     */
    // tag::updateStart[]
    @Override
    public final void update(final Routing.Rules rules) { // <1>
        rules
    // end::updateStart[]
    // tag::updateForCounter[]
            .any(this::counterFilter) // <1>
    // end::updateForCounter[]
    // tag::updateGetsAndPuts[]
            .get("/", this::getDefaultMessage) //<2>
            .get("/{name}", this::getMessage) //<3>
            .put("/greeting/{greeting}", this::updateGreeting); //<4>
    }
    // end::updateGetsAndPuts[]

    /**
     * Return a worldly greeting message.
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

    /**
     * Checks the health of the greeting service.
     * @return a String reporting any problems; null if all is well
     */
    // tag::checkHealth[]
    String checkHealth() {
        if (greeting == null || greeting.trim().length() == 0) { //<1>
           return "greeting is not set or is empty";
        }
        return null;
    }
    // end::checkHealth[]

    /**
     * Increments a simple counter.
     * Calls request.next() to ensure other handlers are called.
     * @param request
     * @param response
     */
    // tag::counterFilter[]
    private void counterFilter(final ServerRequest request,
                               final ServerResponse response) {
        displayThread(); // <1>
        greetCounter.inc(); // <2>
        request.next(); // <3>
    }
    // end::counterFilter[]

    // tag::displayThread[]
    private void displayThread() {
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        System.out.println("Method=" + methodName + " " + "Thread=" + Thread.currentThread().getName());
    }
    // end::displayThread[]
}
