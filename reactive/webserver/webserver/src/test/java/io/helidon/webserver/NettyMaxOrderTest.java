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

package io.helidon.webserver;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Test default value of {@code io.netty.allocator.maxOrder} property.
 */
public class NettyMaxOrderTest {

    private static WebServer webServer;
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    @BeforeAll
    public static void startServer() throws Exception {
        startServer(0);
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }

    /**
     * Start the Web Server.
     *
     * @param port the port on which to start the server; if less than 1,
     * the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                        .port(port)
                        .enableCompression(true)        // compression
                )
                .build()
                .start()
                .await(TIMEOUT);
    }

    /**
     * Test default value of {@code io.netty.allocator.maxOrder} property.
     * Checks property directly after starting server since we run in same VM.
     */
    @Test
    public void textMaxOrderValue() {
        String value = System.getProperty(NettyInitializer.getMaxOrderProperty());
        assertThat(value, is(NettyInitializer.getMaxOrderValue()));
    }
}
