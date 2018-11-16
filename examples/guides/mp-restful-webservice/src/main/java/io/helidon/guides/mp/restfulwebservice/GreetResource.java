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

package io.helidon.guides.mp.restfulwebservice;

// tag::javaxImports[]
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
// end::javaxImports[]

/**
 * A simple JAX-RS resource to greet you. Examples:
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
 * The message is returned as a JSON object.
 */
// tag::classDecl[]
@Path("/greet")
@RequestScoped
public class GreetResource {
// end::classDecl[]
    /**
     * The greeting message provider.
     */
    // tag::greetingMessageDecl[]
    private final GreetingMessage greeting;
    // end::greetingMessageDecl[]

    /**
     * Using constructor injection to get a configuration property.
     * By default this gets the value from META-INF/microprofile-config
     *
     * @param greetingConfig the configured greeting message
     */
    // tag::ctor[]
    @Inject
    public GreetResource(GreetingMessage greetingConfig) { // <1>
        greeting = greetingConfig;
    }
    // end::ctor[]

    /**
     * Return a wordly greeting message.
     *
     * @return {@link JsonObject}
     */
    // tag::getDefaultMessage[]
    @SuppressWarnings("checkstyle:designforextension")
    @GET // <1>
    @Produces(MediaType.APPLICATION_JSON) // <2>
    public JsonObject getDefaultMessage() {
        return createResponse("World");
    }
    // end::getDefaultMessage[]

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name the name to greet
     * @return {@link JsonObject}
     */
    // tag::getMessageWithName[]
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/{name}") // <1>
    @GET // <2>
    @Produces(MediaType.APPLICATION_JSON) // <3>
    public JsonObject getMessage(@PathParam("name") String name) { // <4>
        return createResponse(name);
    }
    // end::getMessageWithName[]

    /**
     * Set the greeting to use in future messages.
     *
     * @param newGreeting the new greeting message
     * @return {@link JsonObject}
     */
    // tag::setGreeting[]
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/greeting/{greeting}") // <1>
    @PUT // <2>
    @Consumes(MediaType.APPLICATION_JSON) // <3>
    @Produces(MediaType.APPLICATION_JSON) // <3>
    public JsonObject updateGreeting(@PathParam("greeting") String newGreeting) { // <4>
        greeting.setMessage(newGreeting);

        return Json.createObjectBuilder()
                .add("greeting", newGreeting)
                .build();
    }
    // end::setGreeting[]

    // tag::createResponse[]
    private JsonObject createResponse(String who) { // <1>
        String msg = String.format("%s %s!", greeting.getMessage(), who); // <2>

        return Json.createObjectBuilder() // <3>
                .add("message", msg)
                .build();
    }
    // end::createResponse[]
}
