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
package io.helidon.docs.about;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@SuppressWarnings("ALL")
class IntroductionSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        WebServer.builder()
                .addRouting(HttpRouting.builder()
                                    .get("/greet", (req, res)
                                            -> res.send("Hello World!")))
                .build()
                .start();
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @Path("hello")
        public class HelloWorld {
            @GET
            public String hello() {
                return "Hello World";
            }
        }
        // end::snippet_2[]
    }
}
