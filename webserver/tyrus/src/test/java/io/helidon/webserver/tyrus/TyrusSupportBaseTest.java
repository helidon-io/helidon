/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.tyrus;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.websocket.server.ServerEndpointConfig;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;

/**
 * The TyrusSupportBaseTest.
 */
public class TyrusSupportBaseTest {

    private static WebServer webServer;

    @AfterAll
    public static void stopServer() {
        webServer.shutdown();
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


        if (!testing) {
            // in case we're running as main an not in test, run on a fixed port
            builder.port(8080);
        }

        // Register each of the endpoints
        TyrusSupport.Builder tyrusSupportBuilder = TyrusSupport.builder();
        for (Object o : endpoints) {
            if (o instanceof ServerEndpointConfig) {
                tyrusSupportBuilder.register((ServerEndpointConfig) o);
            } else if (o instanceof Class<?>) {
                tyrusSupportBuilder.register((Class<?>) o);
            } else {
                throw new IllegalArgumentException("Illegal argument " + o.toString());
            }
        }

        webServer = builder
                .routing(Routing.builder().register(
                        "/tyrus", tyrusSupportBuilder.build()))
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
