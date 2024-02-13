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
package io.helidon.docs.includes.security.providers;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.SubjectType;
import io.helidon.security.abac.policy.PolicyValidator.PolicyStatement;
import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.abac.scope.ScopeValidator.Scope;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@SuppressWarnings("ALL")
class AbacSnippets {

    record SomeResource(String user) {
    }

    class Snippet1 {

        // tag::snippet_1[]
        @Authenticated
        @Path("/abac")
        public class AbacResource {
            @GET
            @Authorized(explicit = true)
            @PolicyStatement("${env.time.year >= 2017 && object.owner == subject.principal.id}")
            public Response process(@Context SecurityContext context) {
                // probably looked up from a database
                SomeResource res = new SomeResource("user");
                AuthorizationResponse atzResponse = context.authorize(res);

                if (atzResponse.isPermitted()) {
                    //do the update
                    return Response.ok().entity("fine, sir").build();
                } else {
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity(atzResponse.description().orElse("Access not granted"))
                            .build();
                }
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @RolesAllowed("user")
        @RoleValidator.Roles(value = "service_role", subjectType = SubjectType.SERVICE)
        @Authenticated
        @Path("/abac")
        public class AbacResource {
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @Scope("calendar_read")
        @Scope("calendar_edit")
        @Authenticated
        @Path("/abac")
        public class AbacResource {
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @PolicyStatement("${env.time.year >= 2017}")
        @Authenticated
        @Path("/abac")
        public class AbacResource {
        }
        // end::snippet_4[]
    }
}
