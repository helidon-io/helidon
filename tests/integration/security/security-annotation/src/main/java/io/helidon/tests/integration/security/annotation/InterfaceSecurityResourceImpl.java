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

import io.helidon.security.annotations.Authenticated;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;

/**
 * Implementation of an interface-defined JAX-RS resource.
 */
@RequestScoped
public class InterfaceSecurityResourceImpl extends InterfaceSecurityResource.RoleSecuredImplementationBase
        implements InterfaceSecurityResource {

    @Override
    public String interfaceAuthenticated() {
        return "interface authenticated";
    }

    @Override
    @Authenticated
    public String implementationAuthenticated() {
        return "implementation authenticated";
    }

    @Override
    @RolesAllowed("admin")
    public String implementationRoles() {
        return "implementation roles";
    }

    @Override
    @RolesAllowed("admin")
    public String implementationAnalyzerRequired() {
        return "implementation analyzer required";
    }

    @Override
    public String implementationAnalyzerForbidden() {
        return "implementation analyzer forbidden";
    }
}
