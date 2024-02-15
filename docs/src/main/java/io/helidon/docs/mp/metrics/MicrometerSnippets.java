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
package io.helidon.docs.mp.metrics;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@SuppressWarnings("ALL")
class MicrometerSnippets {

    // stub
    static JsonObject createResponse(String str) {
        return null;
    }

    // tag::snippet_1[]
    private static final String PERSONALIZED_GETS_COUNTER_NAME = "personalizedGets";
    private static final String PERSONALIZED_GETS_COUNTER_DESCRIPTION = "Counts personalized GET operations";
    private static final String GETS_TIMER_NAME = "allGets";
    private static final String GETS_TIMER_DESCRIPTION = "Tracks all GET operations";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed(value = GETS_TIMER_NAME, description = GETS_TIMER_DESCRIPTION, histogram = true) // <1>
    public JsonObject getDefaultMessage() {
        return createResponse("World");
    }

    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(value = PERSONALIZED_GETS_COUNTER_NAME, description = PERSONALIZED_GETS_COUNTER_DESCRIPTION) // <2>
    @Timed(value = GETS_TIMER_NAME, description = GETS_TIMER_DESCRIPTION, histogram = true) // <1>
    public JsonObject getMessage(@PathParam("name") String name) {
        return createResponse(name);
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Inject
    private MeterRegistry registry;
    // end::snippet_2[]

}
