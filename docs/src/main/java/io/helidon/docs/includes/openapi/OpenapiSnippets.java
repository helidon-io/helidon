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
package io.helidon.docs.includes.openapi;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@SuppressWarnings("ALL")
class OpenapiSnippets {

    // stub
    class GreetingMessage {
    }

    class Snippet1 {

        // tag::snippet_1[]
        @GET
        @Operation(summary = "Returns a generic greeting", // <1>
                   description = "Greets the user generically")
        @APIResponse(description = "Simple JSON containing the greeting", // <2>
                     content = @Content(mediaType = "application/json",
                                        schema = @Schema(implementation = GreetingMessage.class)))
        @Produces(MediaType.APPLICATION_JSON)
        public JsonObject getDefaultMessage() {
            return Json.createObjectBuilder()
                    .add("message", "Hello World!")
                    .build();
        }
        // end::snippet_1[]
    }

}
