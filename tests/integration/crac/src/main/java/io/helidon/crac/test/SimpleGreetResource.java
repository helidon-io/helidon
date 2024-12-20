/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.crac.test;

import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import jakarta.ws.rs.PathParam;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/simple-greet
 *
 * The message is returned as a JSON object.
 */
@Path("/simple-greet")
public class SimpleGreetResource {

    private static final String PERSONALIZED_GETS_COUNTER_NAME = "personalizedGets";
    private static final String PERSONALIZED_GETS_COUNTER_DESCRIPTION = "Counts personalized GET operations";
    private static final String GETS_TIMER_NAME = "allGets";
    private static final String GETS_TIMER_DESCRIPTION = "Tracks all GET operations";
    private final String message;

    @Inject
    public SimpleGreetResource(@ConfigProperty(name = "app.greeting") String message) {
        this.message = message;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link Message}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Message getDefaultMessage() {
        String msg = String.format("%s %s!", message, "World");
        Message message = new Message();
        message.setMessage(msg);
        return message;
    }


    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = PERSONALIZED_GETS_COUNTER_NAME,
             absolute = true,
             description = PERSONALIZED_GETS_COUNTER_DESCRIPTION)
    @Timed(name = GETS_TIMER_NAME,
           description = GETS_TIMER_DESCRIPTION,
           unit = MetricUnits.SECONDS,
           absolute = true)
    public Message getMessage(@PathParam("name") String name) {
        String message = String.format("Hello %s", name);
        return new Message(message);
    }

}
