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

package io.helidon.webclient.http1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class Http1LegacyEntityWriterTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String CONTEXT_MARKER = "legacy-writer-context";
    private static final String CONTENT_TYPE = "application/x-legacy";

    @ParameterizedTest(name = "expectContinue={0}, size={1}")
    @MethodSource("directUploads")
    void writerHeadersAndBodyReachServer(boolean expectContinue, int size) throws Exception {
        byte[] payload = payload(size);
        LegacyBody body = new LegacyBody(payload);
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                String head = readHead(socket.getInputStream());
                assertWriterHeaders(head);
                if (expectContinue) {
                    assertThat(head, containsString("Expect: 100-continue\r\n"));
                    send(socket, "HTTP/1.1 100 Continue\r\n\r\n");
                } else {
                    assertThat(head, not(containsString("Expect: 100-continue\r\n")));
                }
                assertThat(readBody(socket.getInputStream(), head), is(payload));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        })) {
            Http1Client client = client(server, expectContinue);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertWriterFinished(body, expectContinue);
                    assertThat("writer close must return normally", body.closeReturned.get(), is(true));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bufferedWrites")
    void bufferedWriterPreservesBytesAcrossWriteShapes(String description, byte[] payload, BodyWriter writer) throws Exception {
        LegacyBody body = new LegacyBody(payload, writer);
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                String head = readHead(socket.getInputStream());
                assertWriterHeaders(head);
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                send(socket, "HTTP/1.1 100 Continue\r\n\r\n");
                assertThat(description + " must preserve the complete payload", readBody(socket.getInputStream(), head), is(payload));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                assertThat("write shape must not leave extra request framing", readHead(socket.getInputStream()),
                           startsWith("GET /next HTTP/1.1\r\n"));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        })) {
            Http1Client client = client(server, true);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertWriterFinished(body, true);
                    assertThat("writer must return from normal close", body.closeReturned.get(), is(true));
                }
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "flushBeforeHeaders={0}")
    @ValueSource(booleans = {false, true})
    void noExpectWriterSetsContentLengthBeforeFirstBytes(boolean flushBeforeHeaders) throws Exception {
        byte[] payload = payload(6);
        LegacyBody body = new LegacyBody(payload,
                                         flushBeforeHeaders ? Preparation.FLUSH_BEFORE_HEADERS : Preparation.CONTENT_LENGTH);
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                String head = readHead(socket.getInputStream());
                assertWriterHeaders(head);
                assertThat("content length set inside write must determine framing", head,
                           containsString("Content-Length: " + payload.length + "\r\n"));
                assertThat(head, not(containsString("Transfer-Encoding:")));
                assertThat(head, not(containsString("Expect: 100-continue\r\n")));
                assertThat(readBody(socket.getInputStream(), head), is(payload));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                assertThat("late content length must not leave chunk framing", readHead(socket.getInputStream()),
                           startsWith("GET /next HTTP/1.1\r\n"));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        })) {
            Http1Client client = client(server, false);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertWriterFinished(body, false);
                    assertThat(body.closeReturned.get(), is(true));
                }
                try (Http1ClientResponse response = client.get("/next").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    void earlyRedirectRetainsWriterHeadersAndOneShotSource(int status) throws Exception {
        byte[] payload = payload(6);
        LegacyBody body = new LegacyBody(payload);
        try (TestServer server = new TestServer(listener -> {
            try (Socket first = accept(listener)) {
                String head = readHead(first.getInputStream());
                assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                assertWriterHeaders(head);
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                assertThat("writer starts once before the first handshake", body.invocations.get(), is(1));
                assertThat("writer must remain suspended at its first output", body.exited.isDone(), is(false));
                send(first, "HTTP/1.1 " + status + " Redirect\r\nLocation: /terminal\r\nContent-Length: 0\r\n\r\n");
                assertThat("early redirect must not receive body bytes", first.getInputStream().read(), is(-1));
            }
            try (Socket terminal = accept(listener)) {
                String head = readHead(terminal.getInputStream());
                assertThat(head, startsWith("POST /terminal HTTP/1.1\r\n"));
                assertThat("replayable content type must survive the redirect", head,
                           containsString("Content-Type: " + CONTENT_TYPE + "\r\n"));
                assertThat("arbitrary writer headers are not copied to the redirected request", head,
                           not(containsString("X-Legacy:")));
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                assertThat("redirect must retain the same writer invocation", body.invocations.get(), is(1));
                send(terminal, "HTTP/1.1 100 Continue\r\n\r\n");
                assertThat(readBody(terminal.getInputStream(), head), is(payload));
                send(terminal, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        })) {
            Http1Client client = client(server, true);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertWriterFinished(body, true);
                    assertThat("one-shot source must be exhausted exactly once", body.input.read(), is(-1));
                    assertThat(body.closeReturned.get(), is(true));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void earlyRedirectRetainsProducerWhenTargetConnectionSkipsContinue() throws Exception {
        byte[] payload = payload(6);
        LegacyBody body = new LegacyBody(payload);
        try (TestServer destination = new TestServer(listener -> {
            try (Socket terminal = accept(listener)) {
                String warmup = readHead(terminal.getInputStream());
                assertThat(warmup, startsWith("POST /warmup HTTP/1.1\r\n"));
                assertThat(warmup, containsString("Expect: 100-continue\r\n"));
                assertThat(readBody(terminal.getInputStream(), warmup), is(payload));
                send(terminal, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");

                String head = readHead(terminal.getInputStream());
                assertThat("redirect must reuse the warmed connection", head, startsWith("POST /terminal HTTP/1.1\r\n"));
                assertThat("timed-out connection must no longer send Expect", head,
                           not(containsString("Expect: 100-continue\r\n")));
                assertThat(head, containsString("Content-Type: " + CONTENT_TYPE + "\r\n"));
                assertThat("redirect must retain the prepared producer", body.invocations.get(), is(1));
                assertThat(readBody(terminal.getInputStream(), head), is(payload));
                send(terminal, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        });
             TestServer origin = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     String head = readHead(first.getInputStream());
                     assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                     assertWriterHeaders(head);
                     assertThat(head, containsString("Expect: 100-continue\r\n"));
                     assertThat(body.invocations.get(), is(1));
                     send(first, "HTTP/1.1 307 Temporary Redirect\r\nLocation: http://127.0.0.1:"
                             + destination.listener.getLocalPort() + "/terminal\r\nContent-Length: 0\r\n\r\n");
                     assertThat(first.getInputStream().read(), is(-1));
                 }
             })) {
            Http1Client client = client(origin, true, true);
            try {
                try (Http1ClientResponse response = client.post("http://127.0.0.1:"
                                                                       + destination.listener.getLocalPort() + "/warmup")
                        .readContinueTimeout(Duration.ofMillis(100))
                        .outputStream(output -> {
                            output.write(payload);
                            output.close();
                        })) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertWriterFinished(body, true);
                    assertThat("one-shot source must not restart on the no-Expect target", body.input.read(), is(-1));
                    assertThat(body.closeReturned.get(), is(true));
                }
                origin.await();
                destination.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {417, 303})
    void earlyResponseCancelsAndJoinsPreparedWriter(int status) throws Exception {
        LegacyBody body = new LegacyBody(payload(6));
        try (TestServer server = new TestServer(listener -> {
            try (Socket first = accept(listener)) {
                String head = readHead(first.getInputStream());
                assertWriterHeaders(head);
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                String location = status == 303 ? "Location: /terminal\r\n" : "";
                send(first, "HTTP/1.1 " + status + " Early Response\r\n" + location + "Content-Length: 0\r\n\r\n");
                assertThat("early response must cancel before body framing or bytes", first.getInputStream().read(), is(-1));
            }
            if (status == 303) {
                try (Socket terminal = accept(listener)) {
                    assertThat(readHead(terminal.getInputStream()), startsWith("GET /terminal HTTP/1.1\r\n"));
                    send(terminal, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                }
            }
        })) {
            Http1Client client = client(server, true);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status().code(), is(status == 303 ? 200 : 417));
                    assertWriterFinished(body, true);
                    assertThat("canceled writer must not reach normal close return", body.closeReturned.get(), is(false));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "expectContinue={0}")
    @ValueSource(booleans = {false, true})
    void emptyWriterClosesWithoutExpectHandshake(boolean expectContinue) throws Exception {
        LegacyBody body = new LegacyBody(new byte[0]);
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                String head = readHead(socket.getInputStream());
                assertWriterHeaders(head);
                assertThat(head, not(containsString("Expect: 100-continue\r\n")));
                assertThat(readBody(socket.getInputStream(), head), is(new byte[0]));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        })) {
            Http1Client client = client(server, expectContinue);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertWriterFinished(body, expectContinue);
                    assertThat(body.closeReturned.get(), is(true));
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "afterOutput={0}")
    @ValueSource(booleans = {false, true})
    void writerFailurePropagatesWithoutChangingCallerThread(boolean afterOutput) throws Exception {
        byte[] payload = payload(6);
        LegacyBody body = new LegacyBody(payload, afterOutput ? Failure.AFTER_OUTPUT : Failure.BEFORE_OUTPUT);
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                if (afterOutput) {
                    awaitConnectionClose(socket.getInputStream());
                } else {
                    assertThat("preparation failure must close before request bytes", socket.getInputStream().read(), is(-1));
                }
            }
        })) {
            Http1Client client = client(server, false);
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> submit(client, body));
                assertThat("original writer failure must propagate", failure, sameInstance(body.failure));
                assertWriterFinished(body, false);
                assertThat(body.closeReturned.get(), is(false));
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "expectContinue={0}")
    @ValueSource(booleans = {false, true})
    void failureAfterWriterClosePropagatesAndClosesResponseConnection(boolean expectContinue) throws Exception {
        byte[] payload = payload(6);
        LegacyBody body = new LegacyBody(payload, Failure.AFTER_CLOSE);
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                String head = readHead(socket.getInputStream());
                assertWriterHeaders(head);
                if (expectContinue) {
                    assertThat(head, containsString("Expect: 100-continue\r\n"));
                    send(socket, "HTTP/1.1 100 Continue\r\n\r\n");
                } else {
                    assertThat(head, not(containsString("Expect: 100-continue\r\n")));
                }
                assertThat(readBody(socket.getInputStream(), head), is(payload));
                send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                body.responseSent.complete(null);
                awaitConnectionClose(socket.getInputStream());
            }
        })) {
            Http1Client client = client(server, expectContinue);
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
                    try (Http1ClientResponse ignored1 = submit(client, body)) {
                    }
                });
                assertThat("failure after output.close must not be suppressed", failure, sameInstance(body.failure));
                assertWriterFinished(body, expectContinue);
                assertThat("writer must fail after close has returned", body.closeReturned.get(), is(true));
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void postUploadCrossOriginReplaySanitizesHeadersSetInsideWriter() throws Exception {
        byte[] payload = payload(6);
        LegacyBody body = new LegacyBody(payload, Failure.NONE, true, "Bearer legacy-secret");
        AtomicReference<Thread> firstWriter = new AtomicReference<>();
        try (TestServer destination = new TestServer(listener -> {
            try (Socket terminal = accept(listener)) {
                String head = readHead(terminal.getInputStream());
                assertThat(head, startsWith("POST /terminal HTTP/1.1\r\n"));
                assertWriterHeaders(head);
                assertThat("late writer preparation must not restore cross-origin credentials", head,
                           not(containsString("Authorization:")));
                assertThat(readBody(terminal.getInputStream(), head), is(payload));
                send(terminal, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        });
             TestServer origin = new TestServer(listener -> {
                 try (Socket first = accept(listener)) {
                     String head = readHead(first.getInputStream());
                     assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
                     assertWriterHeaders(head);
                     assertThat(head, containsString("Authorization: Bearer legacy-secret\r\n"));
                     firstWriter.set(body.thread.get());
                     assertThat(readBody(first.getInputStream(), head), is(payload));
                     send(first, "HTTP/1.1 307 Temporary Redirect\r\nLocation: http://127.0.0.1:"
                             + destination.listener.getLocalPort() + "/terminal\r\nContent-Length: 0\r\n\r\n");
                     assertThat(first.getInputStream().read(), is(-1));
                 }
             })) {
            Http1Client client = client(origin, false, true);
            try {
                try (Http1ClientResponse response = submit(client, body)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat("post-upload replay invokes the repeatable writer twice", body.invocations.get(), is(2));
                    assertThat("original no-Expect writer must run on the caller", firstWriter.get(),
                               sameInstance(body.callerThread.get()));
                    assertThat("replayed no-Expect writer must run on the caller", body.thread.get(),
                               sameInstance(body.callerThread.get()));
                    assertThat("replayed writer must preserve the caller Context", body.contextMarker.get(), is(CONTEXT_MARKER));
                    assertThat(body.closeReturned.get(), is(true));
                }
                origin.await();
                destination.await();
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void interruptedCallerCancelsWriterWaitingForContinue() throws Exception {
        LegacyBody body = new LegacyBody(payload(6));
        CompletableFuture<Void> headReceived = new CompletableFuture<>();
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        try (TestServer server = new TestServer(listener -> {
            try (Socket socket = accept(listener)) {
                String head = readHead(socket.getInputStream());
                assertWriterHeaders(head);
                assertThat(head, containsString("Expect: 100-continue\r\n"));
                headReceived.complete(null);
                assertThat("interruption must close without sending the prepared body", socket.getInputStream().read(), is(-1));
            }
        });
             ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            Http1Client client = client(server, true);
            Future<RuntimeException> request = callers.submit(() -> {
                callerThread.set(Thread.currentThread());
                return assertThrows(RuntimeException.class, () -> {
                    try (Http1ClientResponse ignored2 = submit(client, body)) {
                    }
                });
            });
            try {
                headReceived.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                callerThread.get().interrupt();
                assertThat(request.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS), notNullValue());
                assertWriterFinished(body, true);
                assertThat(body.closeReturned.get(), is(false));
                server.await();
            } finally {
                request.cancel(true);
                client.closeResource();
                callers.shutdownNow();
            }
        }
    }

    private static Stream<Arguments> directUploads() {
        return Stream.of(false, true)
                .flatMap(expectContinue -> Stream.of(6, 65536).map(size -> Arguments.of(expectContinue, size)));
    }

    private static Stream<Arguments> bufferedWrites() {
        byte[] smallWrites = payload(8449);
        byte[] offsetBulk = payload(193 + 98321 + 37);
        byte[] reusedArray = payload(70000 + 40000);
        return Stream.of(
                Arguments.of("repeated small writes", smallWrites, (BodyWriter) output -> {
                    for (int offset = 0; offset < smallWrites.length; offset += 17) {
                        output.write(smallWrites, offset, Math.min(17, smallWrites.length - offset));
                    }
                }),
                Arguments.of("byte-at-a-time writes", smallWrites, (BodyWriter) output -> {
                    for (byte value : smallWrites) {
                        output.write(value);
                    }
                }),
                Arguments.of("prefix, large offset bulk, and suffix", offsetBulk, (BodyWriter) output -> {
                    byte[] source = new byte[98321 + 64];
                    System.arraycopy(offsetBulk, 193, source, 23, 98321);
                    output.write(offsetBulk, 0, 193);
                    output.write(source, 23, 98321);
                    output.write(offsetBulk, 193 + 98321, 37);
                    output.flush();
                }),
                Arguments.of("source array mutation and reuse", reusedArray, (BodyWriter) output -> {
                    byte[] source = new byte[70000 + 19];
                    System.arraycopy(reusedArray, 0, source, 11, 70000);
                    output.write(source, 11, 70000);
                    Arrays.fill(source, (byte) '#');
                    System.arraycopy(reusedArray, 70000, source, 11, 40000);
                    output.write(source, 11, 40000);
                    Arrays.fill(source, (byte) '!');
                }));
    }

    private static byte[] payload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('a' + i % 26);
        }
        return payload;
    }

    private static Http1Client client(TestServer server, boolean expectContinue) {
        return client(server, expectContinue, false);
    }

    private static Http1Client client(TestServer server, boolean expectContinue, boolean crossOriginReplay) {
        return Http1Client.builder()
                .baseUri("http://127.0.0.1:" + server.listener.getLocalPort())
                .readTimeout(TIMEOUT)
                .readContinueTimeout(TIMEOUT)
                .sendExpectContinue(expectContinue)
                .followCrossOriginEntityRedirects(crossOriginReplay)
                .addMediaSupport(new LegacyMediaSupport())
                .build();
    }

    private static Http1ClientResponse submit(Http1Client client, LegacyBody body) {
        body.callerThread.set(Thread.currentThread());
        Context context = Context.create();
        context.register(CONTEXT_MARKER);
        return Contexts.runInContext(context, () -> client.post("/upload").submit(body));
    }

    private static void assertWriterHeaders(String head) {
        assertThat("content type set inside write must precede transport headers", head,
                   containsString("Content-Type: " + CONTENT_TYPE + "\r\n"));
        assertThat("custom header set inside write must precede transport headers", head,
                   containsString("X-Legacy: set-in-write\r\n"));
    }

    private static void assertWriterFinished(LegacyBody body, boolean expectContinue) {
        assertThat("legacy writer invocation count", body.invocations.get(), is(1));
        assertThat("writer must preserve the submit caller's Context", body.contextMarker.get(), is(CONTEXT_MARKER));
        assertThat("writer must have exited before submit returns", body.exited.isDone(), is(true));
        assertThat("writer thread must be observable", body.thread.get(), notNullValue());
        if (expectContinue) {
            assertThat("Expect-enabled writer must use a separate producer", body.thread.get(),
                       not(sameInstance(body.callerThread.get())));
            assertThat("writer thread must be joined before submit returns", body.thread.get().isAlive(), is(false));
        } else {
            assertThat("no-Expect writer must preserve caller-thread execution", body.thread.get(),
                       sameInstance(body.callerThread.get()));
        }
    }

    private static Socket accept(ServerSocket listener) throws IOException {
        Socket socket = listener.accept();
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        return socket;
    }

    private static void awaitConnectionClose(InputStream input) throws IOException {
        try {
            input.transferTo(OutputStream.nullOutputStream());
        } catch (SocketException ignored3) {
            // Closing a connection with unread response bytes can reset it; read timeouts must still fail the test.
        }
    }

    private static String readHead(InputStream input) throws IOException {
        StringBuilder head = new StringBuilder();
        while (head.length() < 16384) {
            String line = readLine(input);
            head.append(line).append("\r\n");
            if (line.isEmpty()) {
                return head.toString();
            }
        }
        throw new IOException("Request headers exceed test limit");
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        while (line.length() < 16384) {
            int next = input.read();
            if (next == -1) {
                throw new IOException("Connection closed before line completed");
            }
            if (next == '\r') {
                assertThat(input.read(), is((int) '\n'));
                return line.toString();
            }
            line.append((char) next);
        }
        throw new IOException("Line exceeds test limit");
    }

    private static byte[] readBody(InputStream input, String head) throws IOException {
        if (head.contains("Transfer-Encoding: chunked\r\n")) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            int length;
            while ((length = Integer.parseInt(readLine(input), 16)) != 0) {
                if (length < 0 || length > 131072 - body.size()) {
                    throw new IOException("Request body exceeds test limit");
                }
                byte[] chunk = input.readNBytes(length);
                assertThat("complete chunk", chunk.length, is(length));
                body.write(chunk);
                assertThat(readLine(input), is(""));
            }
            assertThat(readLine(input), is(""));
            return body.toByteArray();
        }
        for (String line : head.split("\r\n")) {
            if (line.startsWith("Content-Length: ")) {
                int length = Integer.parseInt(line.substring("Content-Length: ".length()));
                if (length < 0 || length > 131072) {
                    throw new IOException("Request body exceeds test limit");
                }
                return input.readNBytes(length);
            }
        }
        return new byte[0];
    }

    private static void send(Socket socket, String response) throws IOException {
        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private enum Failure {
        NONE,
        BEFORE_OUTPUT,
        AFTER_OUTPUT,
        AFTER_CLOSE
    }

    private enum Preparation {
        DEFAULT,
        CONTENT_LENGTH,
        FLUSH_BEFORE_HEADERS
    }

    @FunctionalInterface
    private interface BodyWriter {
        void write(OutputStream output) throws IOException;
    }

    @FunctionalInterface
    private interface ServerTask {
        void run(ServerSocket listener) throws Exception;
    }

    private static final class LegacyBody {
        private final byte[] payload;
        private final InputStream input;
        private final Failure failurePoint;
        private final Preparation preparation;
        private final BodyWriter customWriter;
        private final boolean replayable;
        private final String authorization;
        private final IllegalStateException failure = new IllegalStateException("legacy writer failure");
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicReference<Thread> callerThread = new AtomicReference<>();
        private final AtomicReference<Thread> thread = new AtomicReference<>();
        private final AtomicReference<String> contextMarker = new AtomicReference<>();
        private final AtomicBoolean closeReturned = new AtomicBoolean();
        private final CompletableFuture<Void> exited = new CompletableFuture<>();
        private final CompletableFuture<Void> responseSent = new CompletableFuture<>();

        private LegacyBody(byte[] payload) {
            this(payload, Failure.NONE);
        }

        private LegacyBody(byte[] payload, Failure failurePoint) {
            this(payload, failurePoint, false, "");
        }

        private LegacyBody(byte[] payload, Preparation preparation) {
            this(payload, Failure.NONE, false, "", preparation);
        }

        private LegacyBody(byte[] payload, BodyWriter customWriter) {
            this(payload, Failure.NONE, false, "", Preparation.DEFAULT, customWriter);
        }

        private LegacyBody(byte[] payload, Failure failurePoint, boolean replayable, String authorization) {
            this(payload, failurePoint, replayable, authorization, Preparation.DEFAULT);
        }

        private LegacyBody(byte[] payload,
                           Failure failurePoint,
                           boolean replayable,
                           String authorization,
                           Preparation preparation) {
            this(payload, failurePoint, replayable, authorization, preparation, null);
        }

        private LegacyBody(byte[] payload,
                           Failure failurePoint,
                           boolean replayable,
                           String authorization,
                           Preparation preparation,
                           BodyWriter customWriter) {
            this.payload = payload;
            this.input = new ByteArrayInputStream(payload);
            this.failurePoint = failurePoint;
            this.preparation = preparation;
            this.customWriter = customWriter;
            this.replayable = replayable;
            this.authorization = authorization;
        }
    }

    private static final class LegacyMediaSupport implements MediaSupport {
        @Override
        public String name() {
            return "legacy-test";
        }

        @Override
        public String type() {
            return "legacy-test";
        }

        @Override
        public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
            return type.rawType() == LegacyBody.class
                    ? new WriterResponse<>(SupportLevel.SUPPORTED, LegacyWriter::new)
                    : WriterResponse.unsupported();
        }
    }

    private static final class LegacyWriter<T> implements EntityWriter<T> {
        @Override
        public boolean supportsInstanceWriter() {
            return false;
        }

        @Override
        public void write(GenericType<T> type,
                          T object,
                          OutputStream output,
                          Headers requestHeaders,
                          WritableHeaders<?> responseHeaders) {
            throw new UnsupportedOperationException("Client writer only");
        }

        @Override
        public void write(GenericType<T> type, T object, OutputStream output, WritableHeaders<?> headers) {
            LegacyBody body = (LegacyBody) object;
            body.invocations.incrementAndGet();
            body.thread.set(Thread.currentThread());
            body.contextMarker.set(Contexts.context().flatMap(context -> context.get(String.class)).orElse("missing"));
            try {
                if (body.preparation == Preparation.FLUSH_BEFORE_HEADERS) {
                    output.flush();
                }
                headers.set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                headers.set(HeaderNames.create("X-Legacy"), "set-in-write");
                if (body.preparation != Preparation.DEFAULT) {
                    headers.set(HeaderNames.CONTENT_LENGTH, Integer.toString(body.payload.length));
                }
                if (!body.authorization.isEmpty()) {
                    headers.set(HeaderNames.AUTHORIZATION, body.authorization);
                }
                if (body.failurePoint == Failure.BEFORE_OUTPUT) {
                    throw body.failure;
                }
                if (body.customWriter == null) {
                    InputStream input = body.replayable ? new ByteArrayInputStream(body.payload) : body.input;
                    input.transferTo(output);
                } else {
                    body.customWriter.write(output);
                }
                if (body.failurePoint == Failure.AFTER_OUTPUT) {
                    output.flush();
                    throw body.failure;
                }
                output.close();
                body.closeReturned.set(true);
                if (body.failurePoint == Failure.AFTER_CLOSE) {
                    body.responseSent.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
                    throw body.failure;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                body.exited.complete(null);
            }
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Future<?> task;

        private TestServer(ServerTask handler) throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout((int) TIMEOUT.toMillis());
            task = executor.submit(() -> {
                handler.run(listener);
                return null;
            });
        }

        void await() throws Exception {
            task.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            listener.close();
            executor.shutdownNow();
            executor.close();
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while collecting test server result", e);
            } catch (ExecutionException e) {
                throw new IOException("Test server failed", e.getCause());
            }
        }
    }
}
