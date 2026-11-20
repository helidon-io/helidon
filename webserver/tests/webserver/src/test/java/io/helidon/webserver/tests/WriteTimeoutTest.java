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

package io.helidon.webserver.tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.net.ssl.SSLSocket;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WriteTimeoutTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 15000;
    private static final Duration WRITE_TIMEOUT = Duration.ofMillis(500);
    private static final byte[] RESPONSE_CHUNK = new byte[1024 * 1024];

    @ParameterizedTest(name = "nio={0}, tls={1}, queue={2}, smart={3}")
    @MethodSource("transports")
    void blockedWriteReleasesConnectionAndRequestPermits(boolean nio, boolean tls, int queueLength, boolean smart)
            throws Exception {
        var failure = new CompletableFuture<Throwable>();
        WebServerConfig.Builder builder = serverBuilder(nio, tls, queueLength, smart)
                .maxConnections(1)
                .maxConcurrentRequests(1)
                .routing(routing -> routing
                        .get("/blocked", (req, res) -> {
                            try {
                                var output = res.outputStream();
                                // Reuse a bounded buffer while exceeding both peers' small socket buffers.
                                for (int i = 0; i < 64; i++) {
                                    output.write(RESPONSE_CHUNK);
                                }
                                output.flush();
                                failure.complete(null);
                            } catch (IOException | RuntimeException e) {
                                failure.complete(e);
                                throw e;
                            }
                        })
                        .get("/healthy", (req, res) -> res.send("ok")));
        WebServer server = builder.build().start();
        try {
            try (Socket stalled = connect(server, tls)) {
                request(stalled, "/blocked");
                Throwable thrown = failure.get(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                assertInstanceOf(SocketTimeoutException.class, timeoutCause(thrown),
                                 "the handler must observe the configured socket write deadline");

                // The stalled peer remains open, so recovery requires the server's timeout cleanup.
                try (Socket healthy = connect(server, tls)) {
                    request(healthy, "/healthy");
                    String response = new String(healthy.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                    assertThat("the connection and request permits must be reusable", response,
                               startsWith("HTTP/1.1 200 OK\r\n"));
                    assertThat(response, containsString("\r\n\r\nok"));
                }
            }
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest(name = "nio={0}, tls={1}, queue={2}, smart={3}")
    @MethodSource("transports")
    void successiveWritesHaveIndependentDeadlines(boolean nio, boolean tls, int queueLength, boolean smart) throws Exception {
        WebServer server = serverBuilder(nio, tls, queueLength, smart)
                .routing(routing -> routing.get("/", (req, res) -> {
                    res.contentLength(4);
                    var output = res.outputStream();
                    for (int i = 0; i < 4; i++) {
                        output.write('a');
                        output.flush();
                        if (i < 3) {
                            Thread.sleep(WRITE_TIMEOUT.dividedBy(2));
                        }
                    }
                }))
                .build()
                .start();
        try {
            try (Socket healthy = connect(server, tls)) {
                request(healthy, "/");
                String response = new String(healthy.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                assertThat(response, startsWith("HTTP/1.1 200 OK\r\n"));
                assertThat("response lifetime may exceed the per-write timeout",
                           response.endsWith("\r\n\r\naaaa"), is(true));
            }
        } finally {
            server.stop();
        }
    }

    private static WebServerConfig.Builder serverBuilder(boolean nio, boolean tls, int queueLength, boolean smart) {
        WebServerConfig.Builder builder = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .shutdownHook(false)
                .useNio(nio)
                .writeTimeout(WRITE_TIMEOUT)
                .writeBufferSize(0)
                .writeQueueLength(queueLength)
                .smartAsyncWrites(smart)
                .connectionOptions(options -> options.socketSendBufferSize(1024));
        if (tls) {
            Keys keys = Keys.builder()
                    .keystore(store -> store.passphrase("helidon").keystore(Resource.create("certificate.p12")))
                    .build();
            builder.tls(config -> config.privateKey(keys).privateKeyCertChain(keys));
        }
        return builder;
    }

    private static Socket connect(WebServer server, boolean tls) throws IOException {
        Socket socket = tls
                ? Tls.builder().trustAll(true).build().sslContext().getSocketFactory().createSocket()
                : new Socket();
        try {
            socket.setReceiveBufferSize(1024);
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()), SOCKET_TIMEOUT_MILLIS);
            if (socket instanceof SSLSocket sslSocket) {
                sslSocket.startHandshake();
            }
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    private static void request(Socket socket, String path) throws IOException {
        socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\n"
                                               + "Host: localhost\r\n"
                                               + "Connection: close\r\n\r\n")
                                              .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static Throwable timeoutCause(Throwable failure) {
        if (failure == null) {
            throw new AssertionError("the entire response was accepted while the client was not reading");
        }
        while (!(failure instanceof SocketTimeoutException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private static Stream<Arguments> transports() {
        return Stream.of(true, false)
                .flatMap(nio -> Stream.of(true, false)
                        .flatMap(tls -> Stream.of(Arguments.of(nio, tls, 0, false),
                                                 Arguments.of(nio, tls, 8, false),
                                                 Arguments.of(nio, tls, 8, true))));
    }
}
