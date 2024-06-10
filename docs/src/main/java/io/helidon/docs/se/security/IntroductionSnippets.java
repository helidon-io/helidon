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

import java.util.UUID;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

@SuppressWarnings("ALL")
class IntroductionSnippets {

    void snippet_1(Security security) {
        // tag::snippet_1[]
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
        // end::snippet_1[]
    }

    void snippet_2(Config config) {
        // tag::snippet_2[]
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()) // <1>
                .build();
        // end::snippet_2[]
    }

    void snippet_3(Config config) {
        // tag::snippet_3[]
        Security security = Security.create(config); // <1>
        // end::snippet_3[]
    }

    void snippet_4(Config config) {
        // tag::snippet_4[]
        Security security1 = Security.builder(config) // <1>
                .addProvider(HttpBasicAuthProvider.builder())
                .build();

        Security security2 = Security.builder() // <2>
                .addProvider(HttpBasicAuthProvider.builder())
                .config(config)
                .build();
        // end::snippet_4[]
    }
}
