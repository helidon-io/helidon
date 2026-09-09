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
import io.helidon.security.annotations.Audited;
import io.helidon.security.annotations.Authenticated;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Resource contract used to verify security annotations on implementation methods.
 */
@Path("/interface-security")
@Audited(value = "interface-resource",
         messageFormat = "Interface resource request",
         okSeverity = AuditSeverity.INFO,
         errorSeverity = AuditSeverity.ERROR)
public interface InterfaceSecurityResource {

    /**
     * Endpoint secured on the interface method.
     *
     * @return endpoint response
     */
    @GET
    @Path("/interface-authenticated")
    @Authenticated
    String interfaceAuthenticated();

    /**
     * Endpoint secured on the implementation method.
     *
     * @return endpoint response
     */
    @GET
    @Path("/implementation-authenticated")
    String implementationAuthenticated();

    /**
     * Endpoint with role security on the implementation method.
     *
     * @return endpoint response
     */
    @GET
    @Path("/implementation-roles")
    String implementationRoles();

    /**
     * Endpoint secured by the class which declares its inherited implementation method.
     *
     * @return endpoint response
     */
    @GET
    @Path("/inherited-implementation-roles")
    String inheritedImplementationRoles();

    /**
     * Endpoint whose implementation analyzer requires authorization after the definition analyzer forbids it.
     *
     * @return endpoint response
     */
    @GET
    @Path("/implementation-analyzer-required")
    String implementationAnalyzerRequired();

    /**
     * Endpoint whose implementation analyzer forbids authorization after the definition analyzer requires it.
     *
     * @return endpoint response
     */
    @GET
    @Path("/implementation-analyzer-forbidden")
    String implementationAnalyzerForbidden();

    /**
     * Base handler whose inherited authentication disablement precedes the selected interface method and must not be
     * reapplied at the concrete handling-class level.
     */
    @Authenticated(false)
    abstract class AuthenticationDisabledBase {
    }

    /**
     * Base implementation owning a role-secured endpoint method inherited by the resource handler.
     */
    @RolesAllowed("admin")
    abstract class RoleSecuredImplementationBase extends AuthenticationDisabledBase
            implements InterfaceSecurityResource {

        @Override
        public String inheritedImplementationRoles() {
            return "inherited implementation roles";
        }
    }
}
