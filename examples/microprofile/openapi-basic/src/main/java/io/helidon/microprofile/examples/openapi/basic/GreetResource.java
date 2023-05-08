/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.examples.openapi.basic;

import java.util.Collections;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * A simple JAX-RS resource with OpenAPI annotations to greet you. Examples:
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
 * Get OpenAPI document for the endpoints
 * curl -X GET http://localhost:8080/openapi
 *
 * Note that the output will include not only the annotated endpoints from this
 * class but also an endpoint added by the {@link SimpleAPIModelReader}.
 *
 * The message is returned as a JSON object.
 */
@Path("/greet")
@RequestScoped
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
    public GreetResource(GreetingProvider greetingConfig) {
        this.greetingProvider = greetingConfig;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link JsonObject}
     */
    @GET
    @Operation(summary = "Returns a generic greeting",
            description = "Greets the user generically")
    @APIResponse(description = "Simple JSON containing the greeting",
            content = @Content(mediaType = "application/json",
                               schema = @Schema(implementation = GreetingMessage.class)))
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        return createResponse("World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name the name to greet
     * @return {@link JsonObject}
     */
    @Path("/{name}")
    @GET
    @Operation(summary = "Returns a personalized greeting")
    @APIResponse(description = "Simple JSON containing the greeting",
            content = @Content(mediaType = "application/json",
                               schema = @Schema(implementation = GreetingMessage.class)))
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getMessage(@PathParam("name") String name) {
        return createResponse(name);
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param jsonObject JSON containing the new greeting
     * @return {@link Response}
     */
    @Path("/greeting")
    @PUT
    @Operation(summary = "Set the greeting prefix",
               description = "Permits the client to set the prefix part of the greeting (\"Hello\")")
    @RequestBody(
        name = "greeting",
        description = "Conveys the new greeting prefix to use in building greetings",
        content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = GreetingUpdateMessage.class),
                    examples = @ExampleObject(
                        name = "greeting",
                        summary = "Example greeting message to update",
                        value = "{\"greeting\": \"New greeting message\"}")))
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateGreeting(JsonObject jsonObject) {

        if (!jsonObject.containsKey("greeting")) {
            JsonObject entity = JSON.createObjectBuilder()
                    .add("error", "No greeting provided")
                    .build();
            return Response.status(Response.Status.BAD_REQUEST).entity(entity).build();
        }

        String newGreeting = jsonObject.getString("greeting");

        greetingProvider.setMessage(newGreeting);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    private JsonObject createResponse(String who) {
        String msg = String.format("%s %s!", greetingProvider.getMessage(), who);

        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }

    /**
     * POJO defining the greeting message content exchanged with clients.
     */
    public static class GreetingMessage {

        private String message;

        /**
         * Gets the message value.
         *
         * @return message value
         */
        public String getMessage() {
            return message;
        }

        /**
         * Sets the message value.
         *
         * @param message message value to set
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * POJO defining the greeting to use in future messages.
     */
    public static class GreetingUpdateMessage {

        private String greeting;

        /**
         * Gets the greeting value.
         *
         * @return greeting value
         */
        public String getGreeting() {
            return greeting;
        }

        /**
         * Sets the greeting value.
         *
         * @param greeting greeting value to set
         */
        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }
    }

}
