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

package io.helidon.webserver.examples.multiport;

import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class MainTest {

    private final Http1Client client;
    private final int publicPort;
    private final int privatePort;
    private final int adminPort;

    public MainTest(WebServer server) {
        client = Http1Client.builder().build();
        publicPort = server.port();
        privatePort = server.port("private");
        adminPort = server.port("admin");
    }

    int port(Params params) {
        return switch (params.socket) {
            case PUBLIC -> publicPort;
            case ADMIN -> adminPort;
            case PRIVATE -> privatePort;
        };
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        // Use test configuration so we can have ports allocated dynamically
        Config config = Config.just(ConfigSources.classpath("application-test.yaml"));
        server.config(config.get("server"))
                .update(Main::setup);
    }

    static Stream<Params> initParams() {
        final String PUBLIC_PATH = "/hello";
        final String PRIVATE_PATH = "/private/hello";
        final String HEALTH_PATH = "/health";
        final String METRICS_PATH = "/health";

        return Stream.of(
                new Params(Socket.PUBLIC, PUBLIC_PATH, Status.OK_200),
                new Params(Socket.PUBLIC, PRIVATE_PATH, Status.NOT_FOUND_404),
                new Params(Socket.PUBLIC, HEALTH_PATH, Status.NOT_FOUND_404),
                new Params(Socket.PUBLIC, METRICS_PATH, Status.NOT_FOUND_404),

                new Params(Socket.PRIVATE, PUBLIC_PATH, Status.NOT_FOUND_404),
                new Params(Socket.PRIVATE, PRIVATE_PATH, Status.OK_200),
                new Params(Socket.PRIVATE, HEALTH_PATH, Status.NOT_FOUND_404),
                new Params(Socket.PRIVATE, METRICS_PATH, Status.NOT_FOUND_404),

                new Params(Socket.ADMIN, PUBLIC_PATH, Status.NOT_FOUND_404),
                new Params(Socket.ADMIN, PRIVATE_PATH, Status.NOT_FOUND_404),
                new Params(Socket.ADMIN, HEALTH_PATH, Status.OK_200),
                new Params(Socket.ADMIN, METRICS_PATH, Status.OK_200));
    }

    @MethodSource("initParams")
    @ParameterizedTest
    public void portAccessTest(Params params) {
        // Verifies we can access endpoints only on the proper port
        client.get()
              .uri("http://localhost:" + port(params))
              .path(params.path)
              .request()
              .close();
    }

    @Test
    public void portTest() {
        try (Http1ClientResponse response = client.get()
                                                  .uri("http://localhost:" + publicPort)
                                                  .path("/hello")
                                                  .request()) {
            assertThat(response.as(String.class), is("Public Hello!!"));
        }

        try (Http1ClientResponse response = client.get()
                                                  .uri("http://localhost:" + privatePort)
                                                  .path("/private/hello")
                                                  .request()) {
            assertThat(response.as(String.class), is("Private Hello!!"));
        }
    }

    private record Params(Socket socket, String path, Status httpStatus) {

        @Override
        public String toString() {
            return path + " @" + socket + " should return " + httpStatus;
        }
    }

    private enum Socket {
        PUBLIC,
        ADMIN,
        PRIVATE
    }

}
