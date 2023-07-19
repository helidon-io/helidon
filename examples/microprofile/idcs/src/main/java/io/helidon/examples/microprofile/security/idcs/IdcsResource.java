/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.microprofile.security.idcs;

import io.helidon.security.SecurityContext;
import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.annotations.Authenticated;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource.
 */
@Path("/")
public class IdcsResource {
    /**
     * A protected resource (authentication required).
     *
     * @param context security context that will contain user's subject once login is completed
     * @return user's subject as a string
     */
    @GET
    @Path("/login")
    @Authenticated
    public String login(@Context SecurityContext context) {
        return context.user().toString();
    }

    /**
     * A login flow example to use from a HTML frontend. A login button would call this method with a
     * query parameter with redirect to the first page.
     *
     * @param context    security context that will contain user's subject once login is completed
     * @param redirectTo target URI (relative) to redirect to
     * @return redirect response
     */
    @GET
    @Path("/login2")
    @Authenticated
    public Response login(@Context SecurityContext context, @QueryParam("target") String redirectTo) {
        return Response
                .status(Response.Status.TEMPORARY_REDIRECT)
                .header("Location", redirectTo)
                .build();
    }

    /**
     * Authenticated and authorized endpoint that requires two scopes (these must be configure in your IDCS application).
     *
     * @param context security context
     * @return current user's subject as a string, should now contain the scopes required
     */
    @GET
    @Path("/scopes")
    @Authenticated
    // Scopes defined in IDCS in my scope audience (see application.yaml)
    @ScopeValidator.Scope("first_scope")
    @ScopeValidator.Scope("second_scope")
    // A group defined in my IDCS domain
    @RoleValidator.Roles("my_admins")
    public String scopes(@Context SecurityContext context) {
        return context.user().toString();
    }
}
