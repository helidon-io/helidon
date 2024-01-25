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
package io.helidon.docs.se;

import java.util.UUID;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.oidc.OidcFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.security.SecurityFeature;

@SuppressWarnings("ALL")
class SecuritySnippets {

    void snippet_1(Config config) {
        // tag::snippet_1[]
        WebServer.builder()
                .addFeature(SecurityFeature.builder()
                                    .config(config.get("security"))
                                    .build())
                .routing(r -> r.addFeature(OidcFeature.create(config)))
                .build();
        // end::snippet_1[]
    }

    void snippet_2(Security security) {
        // tag::snippet_2[]
        SecurityContext context = security.contextBuilder(UUID.randomUUID().toString()) // <1>
                .env(SecurityEnvironment.builder()
                             .method("get")
                             .path("/test")
                             .transport("http")
                             .header("Authorization", "Bearer abcdefgh")
                             .build())
                .build();

        AuthenticationResponse response = context.atnClientBuilder().submit(); // <2>
        if (response.status().isSuccess()) {
            System.out.println(response.user());
            System.out.println(response.service());
        } else {
            System.out.println("Authentication failed: " + response.description());
        }
        // end::snippet_2[]
    }

    void snippet_3(Config config) {
        // tag::snippet_3[]
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()) // <1>
                .build();
        // end::snippet_3[]
    }

    void snippet_4(Config config) {
        // tag::snippet_4[]
        Security security = Security.create(config); // <1>
        // end::snippet_4[]
    }

    void snippet_5(Config config) {
        // tag::snippet_5[]
        Security security1 = Security.builder(config) // <1>
                .addProvider(HttpBasicAuthProvider.builder())
                .build();

        Security security2 = Security.builder() // <2>
                .addProvider(HttpBasicAuthProvider.builder())
                .config(config)
                .build();
        // end::snippet_5[]
    }

    // stub
    void processService1Request(ServerRequest req, ServerResponse res) {
    }

    void snippet_6(Security security) {
        // tag::snippet_6[]
        WebServer.builder()
                .addFeature(SecurityFeature.builder() // <1>
                                    .security(security)
                                    .defaults(SecurityFeature.authenticate())
                                    .build())
                .routing(r -> r
                        .get("/service1", SecurityFeature.rolesAllowed("user"), this::processService1Request)) // <2>
                .build();
        // end::snippet_6[]
    }

    void snippet_7(Config config) {
        // tag::snippet_7[]
        WebServer.builder()
                // This is step 1 - register security instance with web server processing
                // security - instance of security either from config or from a builder
                // securityDefaults - default enforcement for each route that has a security definition
                .addFeature(SecurityFeature.create(builder -> builder.config(config))) // <1>
                .routing(r -> r
                        .get("/service1", this::processService1Request)) // <2>
                .build();
        // end::snippet_7[]
    }
}
