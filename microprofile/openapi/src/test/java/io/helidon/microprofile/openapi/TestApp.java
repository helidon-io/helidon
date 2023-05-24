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
package io.helidon.microprofile.openapi;

import java.util.Set;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Test JAX-RS app for MP OpenAPI testing.
 */

public class TestApp extends Application {

    static final String GO_SUMMARY = "Returns a fixed string";
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(TestResources.class);
    }

    @Path("/testapp")
    public static class TestResources {

        @Path("/go")
        @GET
        @Operation(summary = GO_SUMMARY,
            description = "Provides a single, fixed string as the response")
        @APIResponse(description = "Simple text string",
            content = @Content(mediaType = "text/plain")
                )
        @Produces(MediaType.TEXT_PLAIN)
        public Response go() {
            return Response.ok("Test").build();
        }

        @Path("/send")
        @PUT
        @Operation(summary = "Sends a simple string",
               description = "Permits the client to send a string to the server"
              )
        @RequestBody(
            name = "message",
            description = "Conveys the simple string message",
            content = @Content(
                        mediaType = "text/plain"
                        )
            )
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        public Response send(String message) {
            // Just discard the payload string for the test except for echoing
            // it back as the response.
            return Response.ok(message).build();
        }
    }
}
