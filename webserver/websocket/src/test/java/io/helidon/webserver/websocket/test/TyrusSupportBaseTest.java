/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.webserver.websocket.test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.websocket.WebSocketRouting;

import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.AfterAll;

/**
 * The TyrusSupportBaseTest.
 */
public class TyrusSupportBaseTest {

    private static WebServer webServer;

    @AfterAll
    public static void stopServer() {
        webServer.shutdown().await(Duration.ofSeconds(10));
        webServer = null;
    }

    WebServer webServer() {
        return webServer;
    }

    synchronized static WebServer webServer(boolean testing, Object... endpoints)
            throws InterruptedException, TimeoutException, ExecutionException {
        if (webServer != null) {
            return webServer;
        }

        WebServer.Builder builder = WebServer.builder().host("localhost");



//            builder.port(8070);


        // Register each of the endpoints
        WebSocketRouting.Builder tyrusSupportBuilder = WebSocketRouting.builder();
        for (Object o : endpoints) {
            if (o instanceof ServerEndpointConfig serverEndpointConfig) {
                tyrusSupportBuilder.endpoint("/tyrus", serverEndpointConfig);
            } else if (o instanceof Class<?> clazz) {
                tyrusSupportBuilder.endpoint("/tyrus", clazz);
            } else {
                throw new IllegalArgumentException("Illegal argument " + o.toString());
            }
        }

        webServer = builder
                .addRouting(tyrusSupportBuilder.build())
                .build();

        webServer.start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        if (!testing) {
            System.out.println("WebServer Tyrus application started.");
            System.out.println("Hit CTRL+C to stop.");
            Thread.currentThread().join();
        }

        return webServer;
    }
}
