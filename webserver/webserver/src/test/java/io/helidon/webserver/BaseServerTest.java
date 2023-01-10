/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.webclient.WebClient;

import org.junit.jupiter.api.AfterAll;

class BaseServerTest {
    private static final Logger LOGGER = Logger.getLogger(BaseServerTest.class.getName());
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static WebServer webServer;
    private static WebClient webClient;

    @AfterAll
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }

    protected static WebServer webServer() {
        return webServer;
    }

    protected static WebClient webClient() {
        return webClient;
    }

    protected static void startServer(int port, Routing routing) throws Exception {
        startServer(port, routing, Config.empty());
    }

    protected static void startServer(int port, Routing routing, Config serverConfig) {
        WebServer.Builder builder = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                        .port(port)
                )
                .addRouting(routing)
                .config(serverConfig);
        webServer = builder.build()
                .start()
                .await(TIMEOUT);
        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .validateHeaders(false)
                .keepAlive(true)
                .build();

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }


}
