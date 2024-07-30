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

import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.http.HttpRouting;
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

    void snippet_1(HttpRouting.Builder routing) {
        // tag::snippet_1[]
        CorsSupport corsSupport = CorsSupport.builder()  // <1>
                .addCrossOrigin(CrossOriginConfig.builder() // <2>
                                        .allowOrigins("http://foo.com", "http://there.com") // <3>
                                        .allowMethods("PUT", "DELETE") // <4>
                                        .build()) // <5>
                .addCrossOrigin(CrossOriginConfig.create()) // <6>
                .build(); // <7>
        routing.register("/greet", corsSupport, new GreetService()); // <8>
        // end::snippet_1[]
    }

    void snippet_2(HttpRouting.Builder routing, Config config) {
        // tag::snippet_2[]
        CorsSupport corsSupport = CorsSupport.builder()
                .update(builder -> {
                    config.get("my-cors").ifExists(builder::mappedConfig); // <1>
                    config.get("restrictive-cors").ifExists(builder::config); // <2>
                    builder.addCrossOrigin(CrossOriginConfig.create()); // <3>
                }).build();

        routing.register("/greet", corsSupport, new GreetService()); // <4>
        // end::snippet_2[]
    }
}
