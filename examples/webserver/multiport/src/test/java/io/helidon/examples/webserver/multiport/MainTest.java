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

package io.helidon.examples.webserver.multiport;

import java.util.concurrent.TimeUnit;

import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;
    private static final int PUBLIC_PORT = 8080;
    private static final int PRIVATE_PORT = 8081;
    private static final int ADMIN_PORT = 8082;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }

        webClient = WebClient.builder()
                .build();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void portTest() throws Exception {
        webClient.get()
                .uri("http://localhost:" + PUBLIC_PORT)
                .path("/hello")
                .request(String.class)
                .thenAccept(s -> Assertions.assertEquals("Public Hello!!", s))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + PUBLIC_PORT)
                .path("/private/hello")
                .request()
                .thenAccept(response -> Assertions.assertEquals(404, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + PUBLIC_PORT)
                .path("/health")
                .request()
                .thenAccept(response -> Assertions.assertEquals(404, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + PUBLIC_PORT)
                .path("/metrics")
                .request()
                .thenAccept(response -> Assertions.assertEquals(404, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + PRIVATE_PORT)
                .path("/private/hello")
                .request(String.class)
                .thenAccept(s -> Assertions.assertEquals("Private Hello!!", s))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + ADMIN_PORT)
                .path("/health")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + ADMIN_PORT)
                .path("/metrics")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();
    }

}