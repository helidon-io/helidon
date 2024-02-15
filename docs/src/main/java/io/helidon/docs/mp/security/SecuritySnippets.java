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
package io.helidon.docs.mp.security;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Context;

@SuppressWarnings("ALL")
class SecuritySnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @GET
        @io.helidon.security.annotations.Authenticated
        @io.helidon.security.annotations.Authorized
        // you can also use io.helidon.security.abac.role.RoleValidator.Roles
        @RolesAllowed("admin")
        public String adminResource(@Context io.helidon.security.SecurityContext securityContext) {
            return "you are " + securityContext.userName();
        }
        // end::snippet_1[]
    }

}
