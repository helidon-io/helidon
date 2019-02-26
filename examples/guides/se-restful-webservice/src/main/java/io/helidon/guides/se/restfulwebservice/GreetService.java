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

package io.helidon.guides.se.restfulwebservice;

import java.util.Collections;

//tag::importsStart[]
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
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
// tag::importsHealth[]
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
// end::importsHealth[]
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
 * curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 *
 * The message is returned as a JSON object
 */

//tag::classDef[]
public class GreetService implements Service {
//end::classDef[]
    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    /**
     * Create metric registry.
     */
    // tag::metricsRegistration[]
    private final MetricRegistry registry = RegistryFactory.getInstance()
            .getRegistry(MetricRegistry.Type.APPLICATION); // <1>
    // end::metricsRegistration[]

    /**
     * Counter for all requests to this service.
     */
    // tag::counterRegistration[]
    private final Counter greetCounter = registry.counter("accessctr"); // <2>
    // end::counterRegistration[]

    //tag::ctor[]
    GreetService(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Ciao"); //<1>
    }
    //end::ctor[]

    /**
     * A service registers itself by updating the routine rules.
     * @param rules the routing rules.
     */
    // tag::update[]
    @Override
    public void update(Routing.Rules rules) {
        rules
    // tag::updateForCounter[]
            .any(this::counterFilter) // <1>
    // end::updateForCounter[]
            .get("/", this::getDefaultMessageHandler) //<1>
            .get("/{name}", this::getMessageHandler) //<2>
            .put("/greeting", this::updateGreetingHandler); //<3>
    }
    // end::update[]

    /**
     * Return a worldly greeting message.
     * @param request the server request
     * @param response the server response
     */
    // tag::getDefaultMessage[]
    private void getDefaultMessageHandler(ServerRequest request,
                                   ServerResponse response) {
        sendResponse(response, "World"); //<1>
    }
    //end::getDefaultMessage[]

    /**
     * Return a greeting message using the name that was provided.
     * @param request the server request
     * @param response the server response
     */
    // tag::getMessage[]
    private void getMessageHandler(ServerRequest request,
                            ServerResponse response) {
        String name = request.path().param("name"); //<1>
        sendResponse(response, name); //<2>
    }
    // end::getMessage[]

    private void sendResponse(ServerResponse response, String name) {
        String msg = String.format("%s %s!", greeting, name);

        JsonObject returnObject = JSON.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    // tag::updateGreetingFromJson[]
    private void updateGreetingFromJson(JsonObject jo, ServerResponse response) {

        if (!jo.containsKey("greeting")) { // <1>
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "No greeting provided")
                    .build();
            response.status(Http.Status.BAD_REQUEST_400)
                    .send(jsonErrorObject);
            return;
        }

        greeting = jo.getString("greeting"); // <2>

        response.status(Http.Status.NO_CONTENT_204) // <3>
                .send();
    }
    // end::updateGreetingFromJson[]

    /**
     * Set the greeting to use in future messages.
     * @param request the server request
     * @param response the server response
     */
    // tag::updateGreeting[]
    private void updateGreetingHandler(ServerRequest request,
                                       ServerResponse response) {
        request.content().as(JsonObject.class) // <1>
                .thenAccept(jo -> updateGreetingFromJson(jo, response));
    }
    // end::updateGreeting[]

    // tag::checkAlive[]
    HealthCheckResponse checkAlive() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name("greetingAlive"); //<1>
        if (greeting == null || greeting.trim().length() == 0) { //<2>
            builder.down() //<3>
                   .withData("greeting", "not set or is empty");
        } else {
            builder.up(); //<4>
        }
        return builder.build(); //<5>
    }
    // end::checkAlive[]

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
