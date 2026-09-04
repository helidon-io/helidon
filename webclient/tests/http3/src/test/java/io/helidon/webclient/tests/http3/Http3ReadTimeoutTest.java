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

package io.helidon.webclient.tests.http3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ReadTimeoutTest {
    private static final Duration READ_TIMEOUT = Duration.ofMillis(200);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final byte[] ONE_BYTE = {1};

    @Test
    void shouldTimeoutResponseHeadersWithoutRetiringConnection() throws Exception {
        AtomicReference<Http3RawTestServer.StreamControl> stalledStream = new AtomicReference<>();
        AtomicReference<String> stalledConnectionId = new AtomicReference<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if ("/stalled-head".equals(request.path().orElseThrow())) {
                     stalledStream.set(stream);
                     stalledConnectionId.set(connection.childSocketId());
                     long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
                     while (!stream.stopSendingReceived() && System.nanoTime() < deadline) {
                         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                     }
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             })) {
            Http3Client client = client(server);
            try {
                String connectionId = connectionId(client);

                RuntimeException failure = assertThrows(RuntimeException.class,
                                                         () -> client.get("/stalled-head")
                                                                 .readTimeout(READ_TIMEOUT)
                                                                 .request());

                assertThat(causedBy(failure, SocketTimeoutException.class), is(true));
                assertRequestCancelled(stalledStream);
                assertThat(stalledConnectionId.get(), is(connectionId));
                assertThat(connectionId(client), is(connectionId));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldTimeoutEntityWhileAnotherStreamProgresses() throws Exception {
        CountDownLatch finishActiveStream = new CountDownLatch(1);
        AtomicReference<Http3RawTestServer.StreamControl> stalledStream = new AtomicReference<>();
        AtomicReference<String> activeConnectionId = new AtomicReference<>();
        AtomicReference<String> stalledConnectionId = new AtomicReference<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if ("/active".equals(request.path().orElseThrow())) {
                     activeConnectionId.set(connection.childSocketId());
                     stream.writeResponseHeaders(Status.OK_200.code(), WritableHeaders.create(), false);
                     try {
                         while (!finishActiveStream.await(20, TimeUnit.MILLISECONDS)) {
                             stream.writeData(ONE_BYTE, false);
                         }
                     } catch (InterruptedException e) {
                         Thread.currentThread().interrupt();
                         throw new IllegalStateException("Interrupted while keeping the active stream open.", e);
                     }
                     stream.writeData(new byte[0], true);
                     return null;
                 }
                 if ("/stalled-entity".equals(request.path().orElseThrow())) {
                     stalledStream.set(stream);
                     stalledConnectionId.set(connection.childSocketId());
                     stream.writeResponseHeaders(Status.OK_200.code(), WritableHeaders.create(), false);
                     stream.writeData(ONE_BYTE, false);
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             })) {
            Http3Client client = client(server);
            try {
                String connectionId = connectionId(client);
                try (Http3ClientResponse activeResponse = client.get("/active").request();
                     Http3ClientResponse stalledResponse = client.get("/stalled-entity")
                             .readTimeout(READ_TIMEOUT)
                             .request()) {
                    InputStream activeInput = activeResponse.entity().inputStream();
                    InputStream stalledInput = stalledResponse.entity().inputStream();
                    assertThat(activeResponse.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(activeInput.read(), is(1));
                    assertThat(stalledResponse.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(stalledInput.read(), is(1));

                    UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                                                                 () -> stalledInput.skipNBytes(1));

                    assertThat(causedBy(failure, SocketTimeoutException.class), is(true));
                    assertRequestCancelled(stalledStream);
                    assertThat(activeConnectionId.get(), is(connectionId));
                    assertThat(stalledConnectionId.get(), is(connectionId));
                    finishActiveStream.countDown();
                    assertThat(activeInput.readAllBytes().length, greaterThan(0));
                } finally {
                    finishActiveStream.countDown();
                }
                assertThat(connectionId(client), is(connectionId));
            } finally {
                finishActiveStream.countDown();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldStartReadTimeoutAfterSlowRequestProducerFinishes() throws Exception {
        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, stream) -> {
                 try (InputStream input = stream.requestBodyInputStream()) {
                     assertThat(input.readAllBytes(), is(ONE_BYTE));
                 } catch (IOException e) {
                     throw new UncheckedIOException("Failed to read the request body.", e);
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), "ok");
             })) {
            Http3Client client = client(server);
            try {
                try (Http3ClientResponse response = client.post("/slow-request-producer")
                        .readTimeout(READ_TIMEOUT)
                        .sendExpectContinue(false)
                        .outputStream(output -> {
                            try {
                                TimeUnit.NANOSECONDS.sleep(READ_TIMEOUT.multipliedBy(3).toNanos());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted while delaying request production.", e);
                            }
                            try (output) {
                                output.write(ONE_BYTE);
                            }
                        })) {
                    assertThat(response.entity().as(String.class), is("ok"));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldUseContinueTimeoutBeforeResponseReadTimeout() throws Exception {
        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, stream) -> {
                 try (InputStream input = stream.requestBodyInputStream()) {
                     assertThat(input.readAllBytes(), is(ONE_BYTE));
                 } catch (IOException e) {
                     throw new UncheckedIOException("Failed to read the request body.", e);
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), "ok");
             })) {
            Http3Client client = client(server);
            try {
                try (Http3ClientResponse response = client.post("/continue-timeout")
                        .readTimeout(READ_TIMEOUT)
                        .readContinueTimeout(READ_TIMEOUT.multipliedBy(3))
                        .sendExpectContinue(true)
                        .outputStream(output -> {
                            try (output) {
                                output.write(ONE_BYTE);
                            }
                        })) {
                    assertThat(response.entity().as(String.class), is("ok"));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldUseRequestTimeoutWhileWaitingForTrailers() throws Exception {
        AtomicReference<Http3RawTestServer.StreamControl> stalledStream = new AtomicReference<>();
        AtomicReference<String> stalledConnectionId = new AtomicReference<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if ("/stalled-trailers".equals(request.path().orElseThrow())) {
                     stalledStream.set(stream);
                     stalledConnectionId.set(connection.childSocketId());
                     stream.writeResponseHeaders(
                             Status.OK_200.code(),
                             WritableHeaders.create()
                                     .add(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "1"))
                                     .add(HeaderValues.create(HeaderNames.TRAILER, "x-test-trailer")),
                             false);
                     stream.writeData(ONE_BYTE, false);
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             })) {
            Http3Client client = client(server);
            try {
                String connectionId = connectionId(client);
                try (Http3ClientResponse response = client.get("/stalled-trailers")
                        .readTimeout(READ_TIMEOUT)
                        .request()) {
                    InputStream input = response.entity().inputStream();
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(input.read(), is(1));

                    IllegalStateException failure = assertThrows(IllegalStateException.class, response::trailers);

                    assertThat(causedBy(failure, TimeoutException.class), is(true));
                    assertRequestCancelled(stalledStream);
                    assertThat(stalledConnectionId.get(), is(connectionId));
                }
                assertThat(connectionId(client), is(connectionId));
            } finally {
                client.closeResource();
            }
        }
    }

    private static Http3Client client(Http3RawTestServer server) {
        return strictClientBuilder()
                .baseUri(server.baseUri())
                .tls(server.clientTlsHttp3())
                .build();
    }

    private static String connectionId(Http3Client client) {
        try (Http3ClientResponse response = client.get("/connection-id").request()) {
            assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            return response.entity().as(String.class);
        }
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                return false;
            }
            current = next;
        }
        return false;
    }

    private static void assertRequestCancelled(AtomicReference<Http3RawTestServer.StreamControl> streamReference) {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        Http3RawTestServer.StreamControl stream = streamReference.get();
        while ((stream == null || !stream.stopSendingReceived()) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            stream = streamReference.get();
        }
        assertThat("Client should cancel the expired HTTP/3 response stream",
                   stream != null && stream.stopSendingReceived(),
                   is(true));
        assertThat(stream.stopSendingErrorCode(), is(Http3ErrorCode.REQUEST_CANCELLED.code()));
    }
}
