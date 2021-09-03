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

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import io.helidon.security.SubjectType;
import io.helidon.security.abac.policy.PolicyValidator;
import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.abac.time.TimeValidator;
import io.helidon.security.annotations.Authenticated;

/**
 * Annotation only resource.
 */
@Path("/attributes")
@TimeValidator.TimeOfDay(from = "08:15:00", to = "12:00:00")
@TimeValidator.TimeOfDay(from = "12:30:00", to = "17:30:00")
@TimeValidator.DaysOfWeek({DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY})
@ScopeValidator.Scope("calendar_read")
@ScopeValidator.Scope("calendar_edit")
@RoleValidator.Roles("user_role")
@RoleValidator.Roles(value = "service_role", subjectType = SubjectType.SERVICE)
@PolicyValidator.PolicyStatement("${env.time.year >= 2017}")
@Authenticated
public class AbacResource {
    /**
     * A resource method to demonstrate if access was successful or not.
     *
     * @return "hello"
     */
    @GET
    @AtnProvider.Authentication(value = "user",
                                roles = {"user_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    @AtnProvider.Authentication(value = "service",
                                type = SubjectType.SERVICE,
                                roles = {"service_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    public String process() {
        return "hello";
    }

    /**
     * A resource method to demonstrate if access was successful or not.
     *
     * @return "hello"
     */
    @GET
    @Path("/deny")
    @PolicyValidator.PolicyStatement("${env.time.year < 2017}")
    @AtnProvider.Authentication(value = "user", scopes = {"calendar_read"})
    @AtnProvider.Authentication(value = "service",
                                type = SubjectType.SERVICE,
                                roles = {"service_role"},
                                scopes = {"calendar_read", "calendar_edit"})
    public String deny() {
        return "hello";
    }
}
