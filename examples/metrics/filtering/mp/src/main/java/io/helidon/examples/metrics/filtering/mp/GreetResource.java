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

package io.helidon.examples.metrics.filtering.mp;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * A simple JAX-RS resource to greet you with filtered metrics support.
 */
@Path("/greet")
@RequestScoped
public class GreetResource {

    static final String TIMER_FOR_GETS = "timerForGets";
    static final String COUNTER_FOR_PERSONALIZED_GREETINGS = "counterForPersonalizedGreetings";

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
     * @return {@link jakarta.json.JsonObject}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed(name = TIMER_FOR_GETS, absolute = true)
    public GreetingMessage getDefaultMessage() {
        return createResponse("World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name the name to greet
     * @return {@link jakarta.json.JsonObject}
     */
    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed(name = TIMER_FOR_GETS, absolute = true)
    public GreetingMessage getMessage(@PathParam("name") String name) {
        return createResponse(name);
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param message JSON containing the new greeting
     * @return {@link Response}
     */
    @Path("/greeting")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = COUNTER_FOR_PERSONALIZED_GREETINGS, absolute = true)
    public Response updateGreeting(GreetingMessage message) {
        if (message.getMessage() == null) {
            GreetingMessage entity = new GreetingMessage("No greeting provided");
            return Response.status(Response.Status.BAD_REQUEST).entity(entity).build();
        }

        greetingProvider.setMessage(message.getMessage());
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    private GreetingMessage createResponse(String who) {
        String msg = String.format("%s %s!", greetingProvider.getMessage(), who);

        return new GreetingMessage(msg);
    }
}
