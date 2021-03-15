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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    static Stream<Params> initParams() {
        final String PUBLIC_PATH = "/hello";
        final String PRIVATE_PATH = "/private/hello";
        final String HEALTH_PATH = "/health";
        final String METRICS_PATH = "/health";

        return List.of(
                new Params(publicPort, PUBLIC_PATH, Http.Status.OK_200),
                new Params(publicPort, PRIVATE_PATH, Http.Status.NOT_FOUND_404),
                new Params(publicPort, HEALTH_PATH, Http.Status.NOT_FOUND_404),
                new Params(publicPort, METRICS_PATH, Http.Status.NOT_FOUND_404),

                new Params(privatePort, PUBLIC_PATH, Http.Status.NOT_FOUND_404),
                new Params(privatePort, PRIVATE_PATH, Http.Status.OK_200),
                new Params(privatePort, HEALTH_PATH, Http.Status.NOT_FOUND_404),
                new Params(privatePort, METRICS_PATH, Http.Status.NOT_FOUND_404),

                new Params(adminPort, PUBLIC_PATH, Http.Status.NOT_FOUND_404),
                new Params(adminPort, PRIVATE_PATH, Http.Status.NOT_FOUND_404),
                new Params(adminPort, HEALTH_PATH, Http.Status.OK_200),
                new Params(adminPort, METRICS_PATH, Http.Status.OK_200)
        ).stream();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void portAccessTest(Params params) throws Exception {
        // Verifies we can access endpoints only on the proper port
        webClient.get()
                .uri("http://localhost:" + params.port)
                .path(params.path)
                .request()
                .thenAccept(response -> Assertions.assertEquals(params.httpStatus, response.status()))
                .toCompletableFuture()
                .get();
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
                .uri("http://localhost:" + privatePort)
                .path("/private/hello")
                .request(String.class)
                .thenAccept(s -> Assertions.assertEquals("Private Hello!!", s))
                .toCompletableFuture()
                .get();
    }

    private static class Params {
        int port;
        String path;
        Http.Status httpStatus;

        private Params(int port, String path, Http.Status httpStatus) {
            this.port = port;
            this.path = path;
            this.httpStatus = httpStatus;
        }

        @Override
        public String toString() {
            return port + ":" + path + " should return "  + httpStatus;
        }
    }

}
