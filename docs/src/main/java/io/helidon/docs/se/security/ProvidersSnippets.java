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
import io.helidon.security.providers.oidc.OidcFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.security.SecurityFeature;

@SuppressWarnings("ALL")
class ProvidersSnippets {

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
}
