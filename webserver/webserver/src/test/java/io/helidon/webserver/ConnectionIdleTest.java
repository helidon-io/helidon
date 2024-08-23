/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests support for idle connection timeouts.
 */
public class ConnectionIdleTest {
    private static final Logger LOGGER = Logger.getLogger(ConnectionIdleTest.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final int IDLE_TIMEOUT = 1000;

    private static WebServer webServer;

    @BeforeAll
    public static void startServer() throws Exception {
        startServer(0);
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown().await(TIMEOUT);
        }
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server
     */
    private static void startServer(int port) {
        webServer = WebServer.builder()
                .host("localhost")
                .port(port)
                .connectionIdleTimeout(IDLE_TIMEOUT / 1000)     // in seconds
                .routing(r -> r.get("/hello", (req, res) -> res.send("Hello World!")))
                .build()
                .start()
                .await(TIMEOUT);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @Test
    public void testIdleConnectionClosed() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(webServer)) {
            // initial request with keep-alive to open connection
            client.request(Http.Method.GET,
                    "/hello",
                    null,
                    List.of("Connection: keep-alive"));
            String res = client.receive();
            assertThat(res, containsString("Hello World!"));

            // wait for connection to time out due to inactivity
            Thread.sleep(2 * IDLE_TIMEOUT);

            // now fail attempting to use connection again
            assertEventuallyThrows(SocketException.class, () -> {
                client.request(Http.Method.GET,
                        "/hello",
                        null);
                client.receive();
                return null;
            }, 5 * IDLE_TIMEOUT);
        }
    }

    private static void assertEventuallyThrows(Class<?> exc, Callable<?> runnable, long millis)
            throws InterruptedException {
        long start = System.currentTimeMillis();
        do {
            try {
                runnable.call();
            } catch (Throwable t) {
                if (t.getClass().equals(exc)) {
                    return;
                }
            }
            Thread.sleep(millis / 3);
        } while (System.currentTimeMillis() - start <= millis);
        fail("Predicate failed after " + millis + " milliseconds");
    }
}
