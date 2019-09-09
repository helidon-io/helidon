/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.nativeimage.se1;

import java.util.concurrent.CompletionStage;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * Unit test helper to serve a zipkin server (without the actual zipkin implementation).
 */
public class TracingServer {
    static CompletionStage<WebServer> startServer() {
        WebServer server = WebServer.builder(routing())
                .config(ServerConfiguration.builder()
                                .port(9411)
                                .build())
                .build();

        return server.start();
    }

    private static Routing routing() {
        return Routing.builder().build();
    }
}
