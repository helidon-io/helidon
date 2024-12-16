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
package io.helidon.integrations.micrometer.cdi;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Mixed annotations class.
 */
@Path("mixed")
@RequestScoped
public class MixedAnnotationsResource {

    static final String MESSAGE_COUNTER = "mixedMessageCounter";
    static final String MESSAGE_TIMER = "mixedMessageTimer";
    static final String MESSAGE_RESPONSE = "Hello Mixed World";

    @Counted(value = MESSAGE_COUNTER, extraTags = {"scope", "application"})
    @org.eclipse.microprofile.metrics.annotation.Counted(name = MESSAGE_COUNTER, absolute = true)
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String message() {
        return MESSAGE_RESPONSE;
    }

    @GET
    @Timed(value = MESSAGE_TIMER, extraTags = {"scope", "application"})
    @org.eclipse.microprofile.metrics.annotation.Timed(name = MESSAGE_TIMER, absolute = true)
    @Path("/withArg/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String messageWithArg(@PathParam("name") String input) {
        return "Hello World, " + input;
    }
}
