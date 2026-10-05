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

package io.helidon.webclient.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DuplexExchangeTest {
    private static final int BUFFER_SIZE = 8192;
    private static final int ENTITY_SIZE = 16 * 1024 * 1024;
    private static final HeaderName CONNECTION_HEADER = HeaderNames.create("X-Connection");

    @ParameterizedTest(name = "Read timeout={0} ms")
    @ValueSource(longs = {0, 10_000})
    @Timeout(15)
    void reusesConnectionForDelayedUpload(long readTimeoutMillis) throws InterruptedException {
        int entitySize = 128 * 1024;
        var prefixReceived = new CountDownLatch(1);
        var server = WebServer.builder()
                .host("localhost")
                .port(0)
                .useNio(false)
                .writeQueueLength(2)
                .routing(rules -> rules
                        .get("/connection", (req, res) -> res.send(req.socketId()))
                        .post("/drain", (req, res) -> {
                            res.header(CONNECTION_HEADER, req.socketId());
                            try (var input = req.content().inputStream()) {
                                long received = input.readNBytes(BUFFER_SIZE).length;
                                prefixReceived.countDown();
                                received += input.transferTo(OutputStream.nullOutputStream());
                                res.send(Long.toString(received));
                            }
                        }))
                .build()
                .start();
        var client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .readTimeout(Duration.ofMillis(readTimeoutMillis))
                .build();
        try {
            String socketId;
            try (var response = client.get("/connection").request()) {
                socketId = response.as(String.class);
            }
            // Leave the cached connection idle across multiple socket-monitor polling intervals.
            Thread.sleep(Duration.ofMillis(350));
            client.post("/drain")
                    .sendExpectContinue(false)
                    .header(HeaderNames.CONTENT_LENGTH, Integer.toString(entitySize))
                    .exchange(output -> {
                        try (output) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            output.write(buffer);
                            output.flush();
                            try {
                                assertThat("Server received the upload prefix",
                                           prefixReceived.await(5, TimeUnit.SECONDS), is(true));
                                // The server cannot respond until the producer supplies the remaining bytes.
                                Thread.sleep(Duration.ofMillis(250));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IOException("Interrupted during delayed upload", e);
                            }
                            for (int offset = BUFFER_SIZE; offset < entitySize; offset += BUFFER_SIZE) {
                                output.write(buffer);
                            }
                        }
                    }, response -> {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat("The idle connection was reused",
                                   response.headers().get(CONNECTION_HEADER).get(), is(socketId));
                        assertThat("The entire delayed upload was consumed",
                                   response.as(String.class), is(Integer.toString(entitySize)));
                    });
        } finally {
            client.closeResource();
            server.stop();
        }
    }

    @ParameterizedTest(name = "TLS={0}, NIO={1}, content-length={2}, write queue={3}")
    @CsvSource({
            "false, false, false, 0", "false, false, false, 2",
            "false, false, true,  0", "false, false, true,  2",
            "false, true,  false, 0", "false, true,  false, 2",
            "false, true,  true,  0", "false, true,  true,  2",
            "true,  false, false, 0", "true,  false, false, 2",
            "true,  false, true,  0", "true,  false, true,  2",
            "true,  true,  false, 0", "true,  true,  false, 2",
            "true,  true,  true,  0", "true,  true,  true,  2"
    })
    @Timeout(30)
    void streamsEchoBeforeUploadCompletes(boolean tls, boolean nio, boolean fixedLength, int writeQueueLength) {
        var prefixReceived = new CountDownLatch(1);
        var uploadedChecksum = new AtomicLong();
        var receivedChecksum = new AtomicLong();
        var socketId = new AtomicReference<String>();
        var serverBuilder = WebServer.builder()
                .host("localhost")
                .port(0)
                .useNio(nio)
                .writeQueueLength(writeQueueLength)
                .routing(rules -> rules
                        .get("/connection", (req, res) -> res.send(req.socketId()))
                        .post("/echo", (req, res) -> {
                            res.header(CONNECTION_HEADER, req.socketId());
                            if (fixedLength) {
                                res.contentLength(ENTITY_SIZE);
                            }
                            // One server request thread reads and writes in bounded pieces.
                            try (var input = req.content().inputStream(); var output = res.outputStream()) {
                                byte[] prefix = input.readNBytes(BUFFER_SIZE);
                                output.write(prefix);
                                output.flush();
                                input.transferTo(output);
                            }
                        }));
        var clientBuilder = Http1Client.builder()
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .readTimeout(Duration.ofSeconds(10));
        if (tls) {
            var keys = Keys.builder()
                    .keystore(store -> store.keystore(resource -> resource.resourcePath("server.p12"))
                            .passphrase("password"))
                    .build();
            serverBuilder.tls(Tls.builder().privateKey(keys).privateKeyCertChain(keys).build());
            clientBuilder.tls(Tls.builder()
                                      .trust(trust -> trust.keystore(store -> store
                                              .keystore(resource -> resource.resourcePath("client.p12"))
                                              .passphrase("password")
                                              .trustStore(true)))
                                      .build());
        }
        var server = serverBuilder.build().start();
        var client = clientBuilder.baseUri((tls ? "https" : "http") + "://localhost:" + server.port()).build();
        try {
            var request = client.post("/echo").sendExpectContinue(true);
            if (fixedLength) {
                request.header(HeaderNames.CONTENT_LENGTH, Integer.toString(ENTITY_SIZE));
            }
            request.exchange(output -> {
                try (output) {
                    var checksum = new CRC32();
                    byte[] buffer = new byte[BUFFER_SIZE];
                    for (int offset = 0; offset < ENTITY_SIZE; offset += buffer.length) {
                        for (int index = 0; index < buffer.length; index++) {
                            buffer[index] = (byte) (index + offset / buffer.length);
                        }
                        output.write(buffer);
                        checksum.update(buffer);
                        if (offset == 0) {
                            output.flush();
                            try {
                                assertThat("The response must be consumed before the rest of the upload is permitted",
                                           prefixReceived.await(5, TimeUnit.SECONDS), is(true));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IOException("Interrupted waiting for echo prefix", e);
                            }
                        }
                    }
                    uploadedChecksum.set(checksum.getValue());
                }
            }, response -> {
                assertThat(response.status(), is(Status.OK_200));
                socketId.set(response.headers().get(CONNECTION_HEADER).get());
                var checksum = new CRC32();
                try (var input = response.inputStream()) {
                    byte[] buffer = input.readNBytes(BUFFER_SIZE);
                    assertThat("Echo prefix length", buffer.length, is(BUFFER_SIZE));
                    checksum.update(buffer);
                    prefixReceived.countDown();
                    long received = buffer.length;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checksum.update(buffer, 0, count);
                        received += count;
                    }
                    assertThat("Streamed response length", received, is((long) ENTITY_SIZE));
                }
                receivedChecksum.set(checksum.getValue());
            });
            assertThat("Streamed response checksum", receivedChecksum.get(), is(uploadedChecksum.get()));
            try (var response = client.get("/connection").request()) {
                assertThat("Connection can be reused only after upload and response both complete",
                           response.as(String.class), is(socketId.get()));
            }
        } finally {
            client.closeResource();
            server.stop();
        }
    }
}
