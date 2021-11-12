/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.mp.security.client;

import java.security.Principal;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.Uri;

/**
 * Client resource.
 */
@Path("/client")
public class ClientResource {
    @Uri("http://localhost:9998/server")
    private WebTarget target;

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject authenticated(@Context SecurityContext context) {
        // need to make sure we have the expected user
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();

        jsonBuilder.add("authenticated", context.isAuthenticated());
        jsonBuilder.add("name",
                        context.userPrincipal().map(Principal::getName).orElse(SecurityContext.ANONYMOUS_PRINCIPAL.getName()));
        jsonBuilder.add("admin", context.isUserInRole("admin"));
        jsonBuilder.add("user", context.isUserInRole("user"));

        // now let's call the server and add result information to response
        JsonObject serverResponse = target.request().get(JsonObject.class);

        jsonBuilder.add("server.authenticated", serverResponse.getBoolean("authenticated"));
        jsonBuilder.add("server.name", serverResponse.getString("name"));
        jsonBuilder.add("server.admin", serverResponse.getBoolean("admin"));
        jsonBuilder.add("server.user", serverResponse.getBoolean("user"));

        return jsonBuilder.build();
    }
}
