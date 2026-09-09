/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.annotation;

import io.helidon.security.AuditEvent.AuditSeverity;
import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Audited;
import io.helidon.security.annotations.Authenticated;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Implementation secured with a class-level annotation.
 */
@RequestScoped
@Authenticated
@RolesAllowed("admin")
@Audited(value = "implementation-resource",
         messageFormat = "Implementation resource request",
         okSeverity = AuditSeverity.WARN,
         errorSeverity = AuditSeverity.AUDIT_FAILURE)
public class InterfaceClassSecurityResourceImpl implements InterfaceClassSecurityResource {

    @Inject
    private SecurityContext securityContext;

    @Override
    @GET
    public String get() {
        String definitionType = InterfaceClassSecurityResource.class.getName();
        long definitionCount = securityContext.endpointConfig()
                .securityLevels()
                .stream()
                .filter(level -> definitionType.equals(level.typeName().name()))
                .count();
        return "definition=" + definitionCount;
    }

    @Override
    public String classOverridesMethod() {
        return "implementation class with definition method metadata";
    }

    @Override
    @GET
    @Path("open")
    @PermitAll
    public String open() {
        return "implementation method permit all";
    }
}
