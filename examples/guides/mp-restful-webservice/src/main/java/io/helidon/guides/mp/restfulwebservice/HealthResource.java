/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.guides.mp.restfulwebservice;

// tag::javaxImports[]
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
// end::javaxImports[]

/**
 * Implements the liveness and readiness endpoints to support service
 * health checking.
 */
// tag::classDecl[]
@Path("/")
@RequestScoped
public class HealthResource {
// end::classDecl[]

    private static final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(null);

    // tag::greetingDecl[]
    @Inject // <1>
    private GreetingMessage greeting; // <2>
    // end::greetingDecl[]

    /**
     * Reports the liveness of the greeting resource.
     *
     * @return JAX-RS Response indicating OK or an error with an explanation
     */
    // tag::aliveMethod[]
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/alive") // <1>
    @GET // <2>
    public Response alive() {
        Response response;

        String greetResourceError = checkHealth(greeting.getMessage()); // <3>
        if (greetResourceError == null) {  // <4>
            response = Response.ok().build();
        } else {
            JsonObject returnObject = jsonFactory.createObjectBuilder()
                    .add("error", greetResourceError)
                    .build();
            response = Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(returnObject).build();
        }
        return response;
    }
    // end::aliveMethod[]

    /**
     * Implements a very simple readiness check.
     * @return response (200)
     */
    // tag::readyMethod[]
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/ready")
    @GET
    public Response ready() {
        return Response.ok().build();
    }
    // end::readyMethod[]

    // tag::checkHealthMethod[]
    private String checkHealth(String greeting) {
        if (greeting == null || greeting.trim().length() == 0) {
            return "greeting is not set or is empty";
        }
        return null;
    }
    // end::checkHealthMethod[]
}
