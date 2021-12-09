/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi.other;

import java.util.Set;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

public class TestApp2 extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(Test2Resources.class);
    }

    @Path("/testapp2")
    public static class Test2Resources {

        @Path("/go2")
        @GET
        @Operation(summary = "Test 2 for returning a fixed string",
                description = "Provides a single, fixed string as the response")
        @APIResponse(description = "Simple text string",
                content = @Content(mediaType = "text/plain")
        )
        @Produces(MediaType.TEXT_PLAIN)
        public Response go2() {
            return Response.ok("Test").build();
        }
    }
}