/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.pico;

import io.helidon.common.http.Entity;
import io.helidon.common.http.GET;
import io.helidon.common.http.HeaderParam;
import io.helidon.common.http.Http;
import io.helidon.common.http.POST;
import io.helidon.common.http.Path;
import io.helidon.common.http.PathParam;
import io.helidon.common.http.QueryParam;

import jakarta.inject.Singleton;

@Singleton
@Path("/greet")
class GreetEndpoint {
    private String greeting = "Hello";

    @GET
    String greet() {
        return greeting + " World!";
    }

    @GET
    @Path("/{name}")
    String greetNamed(@PathParam("name") String name,
                      @HeaderParam(Http.Header.HOST_STRING) String hostHeader,
                      @QueryParam("required") String requiredQuery,
                      @QueryParam(value = "optional", defaultValue = "defaultValue") String optionalQuery) {
        return greeting + " " + name + "! Requested host: " + hostHeader + ", required query: " + requiredQuery
                + ", optionalQuery: " + optionalQuery;
    }

    @POST
    void post(@Entity String message) {
        greeting = message;
    }
}
