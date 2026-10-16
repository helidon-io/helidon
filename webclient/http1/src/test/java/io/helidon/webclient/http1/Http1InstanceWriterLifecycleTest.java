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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaSupport;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@Timeout(15)
class Http1InstanceWriterLifecycleTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final byte[] PAYLOAD = "instance writer payload".getBytes(StandardCharsets.US_ASCII);
    private static final String CONTEXT_MARKER = "instance-writer-context";

    @ParameterizedTest(name = "exchange={0}, knownLength={1}")
    @MethodSource("exchanges")
    void streamedInstanceWriterAlwaysClosesItsSource(String exchange, boolean knownLength) throws Exception {
        var body = new Body(knownLength);
        try (var listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            listener.setSoTimeout((int) TIMEOUT.toMillis());
            Future<?> peer = executor.submit(() -> {
                serve(listener, exchange, knownLength);
                return null;
            });
            Http1Client client = Http1Client.builder()
                    .baseUri("http://127.0.0.1:" + listener.getLocalPort())
                    .readTimeout(TIMEOUT)
                    .readContinueTimeout(TIMEOUT)
                    .sendExpectContinue(!exchange.equals("no-expect"))
                    .maxInMemoryEntity(0)
                    .addMediaSupport(new InstanceMediaSupport())
                    .build();
            try {
                Context context = Context.create();
                context.register(CONTEXT_MARKER);
                try (Http1ClientResponse response = Contexts.runInContext(context,
                                                                         () -> client.post("/upload").submit(body))) {
                    assertThat(response.status(), is(exchange.equals("reject") ? Status.UNAUTHORIZED_401 : Status.OK_200));
                    assertThat("one instance writer must be created", body.created.get(), is(1));
                    assertThat("the streamed instance writer must be invoked", body.writes.get(), is(1));
                    assertThat("the writer-owned source must close before submit returns", body.closes.get(), is(1));
                    assertThat("the writer must retain the caller's Helidon context", body.context.get(), is(CONTEXT_MARKER));
                }
                peer.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } finally {
                client.closeResource();
                listener.close();
                executor.shutdownNow();
            }
            assertThat("response and client close must not close the source twice", body.closes.get(), is(1));
        }
    }

    private static Stream<Arguments> exchanges() {
        return Stream.of("continue", "no-expect", "reject", "303", "307", "308")
                .flatMap(exchange -> Stream.of(false, true).map(knownLength -> Arguments.of(exchange, knownLength)));
    }

    private static void serve(ServerSocket listener, String exchange, boolean knownLength) throws IOException {
        boolean redirect = exchange.equals("303") || exchange.equals("307") || exchange.equals("308");
        try (Socket first = accept(listener)) {
            String head = readHead(first.getInputStream());
            assertThat(head, startsWith("POST /upload HTTP/1.1\r\n"));
            assertThat(head, containsString("Content-Type: application/x-instance\r\n"));
            if (knownLength) {
                assertThat(head, containsString("Content-Length: " + PAYLOAD.length + "\r\n"));
            } else {
                assertThat(head, containsString("Transfer-Encoding: chunked\r\n"));
            }
            if (exchange.equals("no-expect")) {
                assertThat(head, not(containsString("Expect: 100-continue\r\n")));
            } else {
                assertThat(head, containsString("Expect: 100-continue\r\n"));
            }
            if (redirect || exchange.equals("reject")) {
                send(first, redirect
                        ? "HTTP/1.1 " + exchange + " Redirect\r\nLocation: /terminal\r\nContent-Length: 0\r\n\r\n"
                        : "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n");
                assertThat("early response must not receive body bytes or chunk framing", first.getInputStream().read(), is(-1));
            } else {
                if (!exchange.equals("no-expect")) {
                    send(first, "HTTP/1.1 100 Continue\r\n\r\n");
                }
                assertThat(readBody(first.getInputStream(), head), is(PAYLOAD));
                send(first, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        }
        if (redirect) {
            try (Socket terminal = accept(listener)) {
                String head = readHead(terminal.getInputStream());
                if (exchange.equals("303")) {
                    assertThat(head, startsWith("GET /terminal HTTP/1.1\r\n"));
                    assertThat(head, not(containsString("Expect: 100-continue\r\n")));
                    assertThat(head, not(containsString("Transfer-Encoding:")));
                } else {
                    assertThat(head, startsWith("POST /terminal HTTP/1.1\r\n"));
                    send(terminal, "HTTP/1.1 100 Continue\r\n\r\n");
                    assertThat("redirect must preserve the one-shot source", readBody(terminal.getInputStream(), head),
                               is(PAYLOAD));
                }
                send(terminal, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            }
        }
    }

    private static Socket accept(ServerSocket listener) throws IOException {
        Socket socket = listener.accept();
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        return socket;
    }

    private static String readHead(InputStream input) throws IOException {
        var head = new StringBuilder();
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
        var line = new StringBuilder();
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
        if (!head.contains("Transfer-Encoding: chunked\r\n")) {
            assertThat(head, containsString("Content-Length: " + PAYLOAD.length + "\r\n"));
            return input.readNBytes(PAYLOAD.length);
        }
        var body = new ByteArrayOutputStream();
        int length;
        while ((length = Integer.parseInt(readLine(input), 16)) != 0) {
            if (length < 0 || length > PAYLOAD.length - body.size()) {
                throw new IOException("Request body exceeds expected length");
            }
            byte[] chunk = input.readNBytes(length);
            assertThat("complete chunk", chunk.length, is(length));
            body.write(chunk);
            assertThat(readLine(input), is(""));
        }
        assertThat(readLine(input), is(""));
        return body.toByteArray();
    }

    private static void send(Socket socket, String response) throws IOException {
        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static final class Body {
        private final boolean knownLength;
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicReference<String> context = new AtomicReference<>();

        private Body(boolean knownLength) {
            this.knownLength = knownLength;
        }
    }

    private static final class InstanceMediaSupport implements MediaSupport {
        @Override
        public String name() {
            return "instance-test";
        }

        @Override
        public String type() {
            return "instance-test";
        }

        @Override
        public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
            return type.rawType() == Body.class
                    ? new WriterResponse<>(SupportLevel.SUPPORTED, TestEntityWriter::new)
                    : WriterResponse.unsupported();
        }
    }

    private static final class TestEntityWriter<T> implements EntityWriter<T> {
        @Override
        public boolean supportsInstanceWriter() {
            return true;
        }

        @Override
        public InstanceWriter instanceWriter(GenericType<T> type, T object, WritableHeaders<?> headers) {
            Body body = (Body) object;
            body.created.incrementAndGet();
            headers.set(HeaderNames.CONTENT_TYPE, "application/x-instance");
            InputStream source = new ByteArrayInputStream(PAYLOAD) {
                @Override
                public void close() throws IOException {
                    body.closes.incrementAndGet();
                    super.close();
                }
            };
            return new InstanceWriter() {
                @Override
                public OptionalLong contentLength() {
                    return body.knownLength ? OptionalLong.of(PAYLOAD.length) : OptionalLong.empty();
                }

                @Override
                public boolean alwaysInMemory() {
                    return false;
                }

                @Override
                public void write(OutputStream output) {
                    body.writes.incrementAndGet();
                    body.context.set(Contexts.context().flatMap(context -> context.get(String.class)).orElse("missing"));
                    try (source; output) {
                        source.transferTo(output);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public byte[] instanceBytes() {
                    throw new IllegalStateException("The test entity must be streamed");
                }
            };
        }

        @Override
        public void write(GenericType<T> type, T object, OutputStream output, WritableHeaders<?> headers) {
            throw new UnsupportedOperationException("Instance writer only");
        }

        @Override
        public void write(GenericType<T> type, T object, OutputStream output, Headers request, WritableHeaders<?> response) {
            throw new UnsupportedOperationException("Client instance writer only");
        }
    }
}
