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
package io.helidon.docs.mp.cors;

import io.helidon.microprofile.cors.CrossOrigin;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@SuppressWarnings("ALL")
class CorsSnippets {

    // tag::snippet_1[]
    @Path("/greet")
    public class GreetResource { // <1>

        @GET
        public JsonObject getDefaultMessage() { // <2>
            return Json.createObjectBuilder()
                    .add("message", "Hello")
                    .build();
        }

        @Path("/greeting")
        @PUT
        public Response updateGreeting(JsonObject jsonObject) { // <3>
            return Response.ok().build();
        }

        @OPTIONS
        @CrossOrigin()
        public void optionsForRetrievingUnnamedGreeting() { // <4>
        }

        @OPTIONS
        @Path("/greeting")
        @CrossOrigin(value = {"http://foo.com", "http://there.com"},
                     allowMethods = {HttpMethod.PUT})
        public void optionsForUpdatingGreeting() { // <5>
        }
    }
    // end::snippet_1[]

}
