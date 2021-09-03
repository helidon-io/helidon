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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

/**
 * Server resource.
 */
@Path("/server")
public class ServerResource {
    @Context
    private SecurityContext context;

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject authenticated() {
        // need to make sure we have the expected user
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();

        jsonBuilder.add("authenticated", context.isAuthenticated());
        jsonBuilder.add("name", context.userPrincipal().map(Principal::getName).orElse(SecurityContext.ANONYMOUS_PRINCIPAL.getName()));
        jsonBuilder.add("admin", context.isUserInRole("admin"));
        jsonBuilder.add("user", context.isUserInRole("user"));

        return jsonBuilder.build();
    }
}
