/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.webclient.WebClient;
import org.junit.jupiter.api.AfterAll;

class BaseServerTest {
    private static final Logger LOGGER = Logger.getLogger(BaseServerTest.class.getName());

    private static WebServer webServer;
    private static WebClient webClient;

    @AfterAll
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    protected static WebServer webServer() {
        return webServer;
    }

    protected static WebClient webClient() {
        return webClient;
    }

    protected static void startServer(int port, Routing routing) throws Exception {
        webServer = WebServer.builder()
                .port(port)
                .routing(routing)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .validateHeaders(false)
                .keepAlive(true)
                .build();

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }
}
