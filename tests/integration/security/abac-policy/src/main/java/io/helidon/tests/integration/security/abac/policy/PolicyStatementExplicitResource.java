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

package io.helidon.tests.integration.security.abac.policy;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.abac.policy.PolicyValidator;
import io.helidon.security.annotations.Authorized;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * A resource with explicit authorization.
 */
@Path("/explicit")
public class PolicyStatementExplicitResource {

    /**
     * Policy statement and explicit authorization is set via annotations and not overridden.
     *
     * @param context security context to perform an explicit authorization
     * @return result of the authorization
     */
    @GET
    @Path("annotation")
    @Authorized(explicit = true)
    @PolicyValidator.PolicyStatement("${env.time.year >= 2017}")
    public Response annotation(@Context SecurityContext context) {
        AuthorizationResponse atzResponse = context.authorize();

        if (atzResponse.isPermitted()) {
            return Response.ok().entity("passed").build();
        } else {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(atzResponse.description().orElse("Access not granted"))
                    .build();
        }
    }

    /**
     * Policy statement and explicit authorization is set via configuration.
     *
     * @param context security context to perform an explicit authorization
     * @return result of the authorization
     */
    @GET
    @Path("configuration")
    @Authorized
    @PolicyValidator.PolicyStatement("${env.time.year < 2017}")
    public Response configuration(@Context SecurityContext context) {
        AuthorizationResponse atzResponse = context.authorize();

        if (atzResponse.isPermitted()) {
            return Response.ok().entity("passed").build();
        } else {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(atzResponse.description().orElse("Access not granted"))
                    .build();
        }
    }

}
