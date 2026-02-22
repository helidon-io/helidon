/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import io.helidon.http.Method;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.cors.CorsFeature;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

@SuppressWarnings("ALL")
class CorsSnippets {

    // stub
    class GreetService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
        }
    }

    void snippet_3() {
        // tag::snippet_3[]
        CorsFeature corsFeature = CorsFeature.builder() // <1>
                .addPath(path -> path // <2>
                        .pathPattern("/greet/*") // <3>
                        .addAllowOrigin("http://foo.bar") // <4>
                        .addAllowMethod(Method.PUT) // <5>
                )
                .build(); // <6>

        WebServer.builder()
                .port(8080)
                .addFeature(corsFeature) // <7>
                .build();
        // end::snippet_3[]
    }
}
