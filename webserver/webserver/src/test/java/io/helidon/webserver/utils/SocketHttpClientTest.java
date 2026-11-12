/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.utils;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocketHttpClientTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "HTTP/1.1 100 Continue\r\n", "HTTP/1.1 417 Expectation Failed\r\n\r\n"})
    void awaitResponseRejectsPrematureEof(String response) throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             SocketHttpClient client = new SocketHttpClient(server.getLocalPort());
             Socket connection = server.accept()) {
            connection.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            connection.shutdownOutput();

            assertThrows(AssertionError.class, () -> client.awaitResponse("HTTP/1.1 100 Continue\n", "\n\n"));
        }
    }

    @Test
    void awaitResponseReturnsAfterCompleteInformationalResponse() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             SocketHttpClient client = new SocketHttpClient(server.getLocalPort());
             Socket connection = server.accept()) {
            connection.getOutputStream().write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.UTF_8));

            assertThat(client.awaitResponse("HTTP/1.1 100 Continue\n", "\n\n"), sameInstance(client));
        }
    }
}
