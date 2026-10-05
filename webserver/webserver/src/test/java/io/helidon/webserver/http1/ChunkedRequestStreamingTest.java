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

package io.helidon.webserver.http1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ChunkedRequestStreamingTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deliversHugeChunkPrefixBeforeClientSendsRemainder(boolean useNio) throws Exception {
        var delivered = new CountDownLatch(1);
        var prefix = new AtomicReference<String>();
        var server = WebServer.builder()
                .port(0)
                .useNio(useNio)
                .routing(rules -> rules.post("/", (req, res) -> {
                    prefix.set(new String(req.content().inputStream().readNBytes(6), StandardCharsets.US_ASCII));
                    delivered.countDown();
                    res.send("prefix received");
                }))
                .build()
                .start();
        try (var socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(bytes(requestHeaders() + "7fffffff\r\nprefix"));
            socket.getOutputStream().flush();
            assertThat("Request handler must receive the prefix before the client sends the rest of the chunk",
                       delivered.await(5, TimeUnit.SECONDS), is(true));
            assertThat(prefix.get(), is("prefix"));
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsInvalidChunkTerminator() throws Exception {
        var server = WebServer.builder()
                .port(0)
                .routing(rules -> rules.post("/", (req, res) -> res.send(req.content().as(String.class))))
                .build()
                .start();
        try (var socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(bytes(requestHeaders() + "1\r\naXX0\r\n\r\n"));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertThat(response, containsString("HTTP/1.1 400 Bad Request"));
        } finally {
            server.stop();
        }
    }

    @Test
    void decodesMultipleChunksAndTerminatingChunk() throws Exception {
        var server = WebServer.builder()
                .port(0)
                .routing(rules -> rules.post("/", (req, res) -> res.send(req.content().as(String.class))))
                .build()
                .start();
        try (var socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(bytes(requestHeaders() + "3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n"));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertThat(response, containsString("HTTP/1.1 200 OK"));
            assertThat(response, containsString("abcde"));
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsChunkExceedingPayloadLimitBeforeWaitingForItsBytes() throws Exception {
        var server = WebServer.builder()
                .port(0)
                .maxPayloadSize(3)
                .routing(rules -> rules.post("/", (req, res) -> {
                    req.content().inputStream().read();
                    res.send("unexpected payload");
                }))
                .build()
                .start();
        try (var socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(bytes(requestHeaders() + "7fffffff\r\n"));
            socket.getOutputStream().flush();
            var response = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(response.readLine(), containsString("HTTP/1.1 413"));
        } finally {
            server.stop();
        }
    }

    private static String requestHeaders() {
        return "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
