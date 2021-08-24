/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.examples.signatures;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webclient.WebClient;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Common code for both examples (builder and config based).
 */
final class SignatureExampleUtil {
    private static final WebClient CLIENT = WebClient.builder()
            .addService(WebClientSecurity.create())
            .build();

    private static final int START_TIMEOUT_SECONDS = 10;

    private static final AtomicInteger SERVER_2_PORT = new AtomicInteger();

    private SignatureExampleUtil() {
    }

    static void server2Port(int port) {
        SERVER_2_PORT.set(port);
    }

    static int server2Port() {
        return SERVER_2_PORT.get();
    }

    static WebServer startServer(WebServer server) {
        return server.start()
                .await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Start a web server.
     *
     * @param routing routing to configre
     * @return started web server instance
     */
    public static WebServer startServer(Routing routing, int port) {
        return WebServer.builder(routing)
                .port(port)
                .build()
                .start()
                .peek(it -> System.out.printf("Started server on localhost:%d%n", it.port()))
                .await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
