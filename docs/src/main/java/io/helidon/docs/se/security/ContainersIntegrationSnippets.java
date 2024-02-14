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
package io.helidon.docs.se.security;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.security.SecurityFeature;

@SuppressWarnings("ALL")
class ContainersIntegrationSnippets {

    // stub
    void processService1Request(ServerRequest req, ServerResponse res) {
    }

    void snippet_1(Security security) {
        // tag::snippet_1[]
        WebServer.builder()
                .addFeature(SecurityFeature.builder() // <1>
                                    .security(security)
                                    .defaults(SecurityFeature.authenticate())
                                    .build())
                .routing(r -> r
                        .get("/service1", SecurityFeature.rolesAllowed("user"), this::processService1Request)) // <2>
                .build();
        // end::snippet_1[]
    }

    void snippet_2(Config config) {
        // tag::snippet_2[]
        WebServer.builder()
                // This is step 1 - register security instance with web server processing
                // security - instance of security either from config or from a builder
                // securityDefaults - default enforcement for each route that has a security definition
                .addFeature(SecurityFeature.create(builder -> builder.config(config))) // <1>
                .routing(r -> r
                        .get("/service1", this::processService1Request)) // <2>
                .build();
        // end::snippet_2[]
    }
}
