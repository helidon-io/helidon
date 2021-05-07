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
package io.helidon.security.examples.abac;

import java.time.DayOfWeek;

import javax.json.JsonString;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.SubjectType;
import io.helidon.security.abac.policy.PolicyValidator;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.abac.time.TimeValidator;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;

/**
 * Explicit authorization resource - authorization must be called by programmer.
 */
@Path("/explicit")
@TimeValidator.TimeOfDay(from = "08:15:00", to = "12:00:00")
@TimeValidator.TimeOfDay(from = "12:30:00", to = "17:30:00")
@TimeValidator.DaysOfWeek({DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY})
@ScopeValidator.Scope("calendar_read")
@ScopeValidator.Scope("calendar_edit")
@PolicyValidator.PolicyStatement("${env.time.year >= 2017 && object.owner == subject.principal.id}")
@Authenticated
public class AbacExplicitResource {
    /**
     * A resource method to demonstrate explicit authorization.
     *
     * @param context  security context (injected)
     * @return "fine, sir" string; or a description of authorization failure
     */
    @GET
    @Authorized(explicit = true)
    @AtnProvider.Authentication(value = "user",
                                roles = {"user_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    @AtnProvider.Authentication(value = "service",
                                type = SubjectType.SERVICE,
                                roles = {"service_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    public Response process(@Context SecurityContext context) {
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

    /**
     * A resource method to demonstrate explicit authorization - this should fail, as we do not call authorization.
     *
     * @param context security context (injected)
     * @param object a JSON string
     * @return "fine, sir" string; or a description of authorization failure
     */
    @POST
    @Path("/deny")
    @Authorized(explicit = true)
    @AtnProvider.Authentication(value = "user",
                                roles = {"user_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    @AtnProvider.Authentication(value = "service",
                                type = SubjectType.SERVICE,
                                roles = {"service_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response fail(@Context SecurityContext context, JsonString object) {
        return Response.ok("This should not work").build();
    }

    /**
     * Example resource.
     */
    public static class SomeResource {
        private String id;
        private String owner;
        private String message;

        private SomeResource(String owner) {
            this.id = "id";
            this.owner = owner;
            this.message = "Unit test";
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
