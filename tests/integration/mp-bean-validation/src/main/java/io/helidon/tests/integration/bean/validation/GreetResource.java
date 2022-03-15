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

package io.helidon.tests.integration.bean.validation;

import java.util.Collections;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;



/**
 * A simple JAX-RS resource to greet you.
 * Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 *
 * Test`null` greeting message:
 * curl -X GET http://localhost:8080/greet/null
 */
@Path("/greet")
@ApplicationScoped
public class GreetResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    /**
     * The greeting message provider.
     */
    private final GreetingProvider greetingProvider;

    /**
     * Using constructor injection to get a configuration property.
     * By default this gets the value from META-INF/microprofile-config
     *
     * @param greetingConfig the configured greeting message
     */
    @Inject
    public GreetResource(@Valid GreetingProvider greetingConfig) {
        this.greetingProvider = greetingConfig;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link JsonObject}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        return createResponse("World");
    }

    /**
     * This function will explicitly set greeting to null, call validation, and return validation message.
     *
     * @return {@link String}
     */
    @Path("/null")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String nullMessage() {
        greetingProvider.setGreeting(new GreetingHolder(null));
        return "Nothing";
    }


    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name the name to greet
     * @return {@link JsonObject}
     */
    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getMessage(@PathParam("name") String name) {
        return createResponse(name);
    }


    private JsonObject createResponse(String who) {
        String msg = String.format("%s %s!", greetingProvider.getGreeting(), who);

        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }
}
