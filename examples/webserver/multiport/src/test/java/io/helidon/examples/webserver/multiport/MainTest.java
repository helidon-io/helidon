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

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;
    private static int publicPort;
    private static int privatePort;
    private static int adminPort;

    @BeforeAll
    public static void startTheServer() {
        // Use test configuration so we can have ports allocated dynamically
        Config config = Config.builder().addSource(ConfigSources.classpath("application-test.yaml")).build();
        Single<WebServer> w = Main.startServer(config);
        webServer = w.await();
        webClient = WebClient.builder().build();

        publicPort = webServer.port();
        privatePort = webServer.port("private");
        adminPort = webServer.port("admin");
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
                .uri("http://localhost:" + publicPort)
                .path("/hello")
                .request(String.class)
                .thenAccept(s -> Assertions.assertEquals("Public Hello!!", s))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + publicPort)
                .path("/private/hello")
                .request()
                .thenAccept(response -> Assertions.assertEquals(404, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + publicPort)
                .path("/health")
                .request()
                .thenAccept(response -> Assertions.assertEquals(404, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + publicPort)
                .path("/metrics")
                .request()
                .thenAccept(response -> Assertions.assertEquals(404, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + privatePort)
                .path("/private/hello")
                .request(String.class)
                .thenAccept(s -> Assertions.assertEquals("Private Hello!!", s))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + adminPort)
                .path("/health")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();

        webClient.get()
                .uri("http://localhost:" + adminPort)
                .path("/metrics")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();
    }

}