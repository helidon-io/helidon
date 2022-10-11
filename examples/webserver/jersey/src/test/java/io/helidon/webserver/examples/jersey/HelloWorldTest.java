/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.jersey;

import java.util.concurrent.TimeUnit;

import io.helidon.webserver.WebServer;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The Jersey Client based example that tests the {@link HelloWorld} resource
 * that gets served by running {@link Main#startServer(int)}
 *
 * @see HelloWorld
 * @see Main
 */
public class HelloWorldTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer(0)
                                       .toCompletableFuture()
                                       .get(10, TimeUnit.SECONDS);
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
    public void testHelloWorld() throws Exception {
        Client client = ClientBuilder.newClient();
        try (Response response = client.target("http://localhost:" + webServer.port())
                                      .path("jersey/hello")
                                      .request()
                                      .get()) {
            assertThat("Unexpected response; status: " + response.getStatus(),
                    response.readEntity(String.class), is("Hello World!"));
        } finally {
            client.close();
        }
    }
}
