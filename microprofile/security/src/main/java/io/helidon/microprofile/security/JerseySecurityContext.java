/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import io.helidon.security.Principal;
import io.helidon.security.Subject;

import jakarta.ws.rs.core.SecurityContext;

/**
 * {@link SecurityContext} implementation for integration with security component.
 */
record JerseySecurityContext(io.helidon.security.SecurityContext securityContext,
                             SecurityDefinition methodSecurity,
                             boolean isSecure) implements SecurityContext {

    @Override
    public Principal getUserPrincipal() {
        return securityContext.user().map(Subject::principal)
                              .orElse(io.helidon.security.SecurityContext.ANONYMOUS_PRINCIPAL);
    }

    @Override
    public boolean isUserInRole(String role) {
        return securityContext.isUserInRole(role, methodSecurity.getAuthorizer());
    }

    @Override
    public String getAuthenticationScheme() {
        //todo check if there is a way to get this from authentication provider?
        return null;
    }
}
