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
package io.helidon.docs.se.openapi;

import io.helidon.config.Config;
import io.helidon.integrations.openapi.ui.OpenApiUi;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

@SuppressWarnings("ALL")
class OpenApiSnippets {

    // stub
    class Main {
        static void routing(HttpRouting.Builder routing) {
        }
    }

    void snippet_1(Config config) {
        // tag::snippet_1[]
        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addFeature(OpenApiFeature.create(config.get("openapi"))) // <1>
                .routing(Main::routing)
                .build()
                .start();
        // end::snippet_1[]
    }
}
