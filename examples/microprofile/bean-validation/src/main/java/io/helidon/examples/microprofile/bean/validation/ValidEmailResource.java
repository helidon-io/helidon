/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.microprofile.bean.validation;

import java.util.Collections;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.validation.constraints.Email;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * A simple JAX-RS resource to validate email.
 * Examples:
 *
 * Get valid response:
 * curl -X GET http://localhost:8080/valid/e@mail.com
 *
 * Test failed response:
 * curl -X GET http://localhost:8080/valid/email
 */
@Path("/valid")
@ApplicationScoped
public class ValidEmailResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param email the name to validate
     * @return {@link JsonObject}
     */
    @Path("/{email}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getMessage(@PathParam("email") @Email String email) {
        return createResponse(email);
    }


    private JsonObject createResponse(String who) {
        String msg = String.format("%s %s!", "Valid", who);

        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }
}
