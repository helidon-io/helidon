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
package io.helidon.docs.mp;

import java.util.Optional;
import java.util.Set;

import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.auth.LoginConfig;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@SuppressWarnings("ALL")
class JwtSnippets {

    // tag::snippet_1[]
    @LoginConfig(authMethod = "MP-JWT")
    @ApplicationScoped
    public class ProtectedApplication extends Application {
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Path("/hello")
    public class HelloResource {

        @GET
        @Produces(TEXT_PLAIN)
        public String hello(@Context SecurityContext context) {
            Optional<Principal> userPrincipal = context.userPrincipal();
            return "Hello, " + userPrincipal.get().getName() + "!";
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @LoginConfig(authMethod = "MP-JWT")
    @ApplicationScoped
    public class HelloApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(HelloResource.class);
        }
    }
    // end::snippet_3[]

}
