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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3LargeBodyTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BACKPRESSURE_OBSERVATION_TIMEOUT = Duration.ofSeconds(2);
    private static final String CHUNK = "0123456789abcdef".repeat(256);
    private static final int REPETITIONS = 64;
    private static final int FLOW_CONTROL_BODY_SIZE = 8 * 1024 * 1024;
    private static final String LARGE_BODY = CHUNK.repeat(REPETITIONS);
    private static final String EARLY_RESPONSE = "request body not required";
    private static final byte[] CHUNK_BYTES = CHUNK.getBytes(StandardCharsets.UTF_8);
    private static final byte[] LARGE_BODY_BYTES = LARGE_BODY.getBytes(StandardCharsets.UTF_8);

    private TestEnvironment environment;
    private CountDownLatch firstChunkConsumed;
    private CountDownLatch remainderAllowed;
    private CountDownLatch earlyResponseQueued;

    @BeforeEach
    void beforeEach() throws Exception {
        firstChunkConsumed = new CountDownLatch(1);
        remainderAllowed = new CountDownLatch(1);
        earlyResponseQueued = new CountDownLatch(1);
        environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/stream-large", Http3LargeBodyTest::streamLarge)
                .post("/echo-large", Http3LargeBodyTest::echoLarge)
                .post("/echo-single-byte", Http3LargeBodyTest::echoSingleByte)
                .post("/echo-streamed", this::echoStreamed)
                .post("/respond-without-request-body", this::respondWithoutRequestBody));
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldHandleLargeResponseWithTypedHttp3Client() {
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.get("/stream-large").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is(LARGE_BODY));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldHandleLargeEchoRequestWithTypedHttp3Client() {
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.post("/echo-large").submit(LARGE_BODY)) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is(LARGE_BODY));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRoundTripEntityWithSingleByteApplicationReads() throws Exception {
        byte[] expected = CHUNK.repeat(5).getBytes(StandardCharsets.UTF_8);
        byte[] actual = new byte[expected.length];
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.post("/echo-single-byte").submit(expected);
                 InputStream inputStream = response.entity().inputStream()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                for (int i = 0; i < actual.length; i++) {
                    int next = inputStream.read();
                    if (next == -1) {
                        throw new AssertionError("HTTP/3 response ended after " + i + " bytes");
                    }
                    actual[i] = (byte) next;
                }
                assertThat(inputStream.read(), is(-1));
                assertThat(actual, is(expected));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldHandleLargeEchoRequestWithExplicitGenericHttp3Request() {
        WebClient client = strictWebClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();

        try {
            try (HttpClientResponse response = client.post("/echo-large")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .submit(LARGE_BODY)) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is(LARGE_BODY));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldStreamLargeEchoRequestWithTypedHttp3Client() {
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.post("/echo-streamed").outputStream(outputStream -> {
                try (outputStream) {
                    outputStream.write(CHUNK_BYTES);
                    outputStream.flush();
                    try {
                        assertThat("Server should consume the first chunk before the producer writes the remainder",
                                   firstChunkConsumed.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                   is(true));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while awaiting the first consumed chunk.", e);
                    } finally {
                        remainderAllowed.countDown();
                    }
                    outputStream.write(LARGE_BODY_BYTES,
                                       CHUNK_BYTES.length,
                                       LARGE_BODY_BYTES.length - CHUNK_BYTES.length);
                }
            })) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is(LARGE_BODY));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRejectEmptyStreamAgainstDeclaredContentLength() {
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            RuntimeException failure = assertThrows(RuntimeException.class,
                                                     () -> client.post("/echo-large")
                                                             .header(HeaderNames.CONTENT_LENGTH, "1")
                                                             .outputStream(OutputStream::close));
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause.getMessage(),
                       is("Content length was set to 1, but the request producer wrote 0 bytes"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRetainFinalResponseWhenServerStopsStreamingRequest() {
        AtomicBoolean producerCompleted = new AtomicBoolean();
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.post("/respond-without-request-body")
                    .sendExpectContinue(false)
                    .outputStream(outputStream -> {
                        try (outputStream) {
                            outputStream.write(CHUNK_BYTES, 0, 1);
                            outputStream.flush();
                            try {
                                assertThat("Server should queue the final response before the remaining upload",
                                           earlyResponseQueued.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                           is(true));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted while awaiting the final response.", e);
                            }
                            outputStream.write(LARGE_BODY_BYTES);
                            outputStream.flush();
                        }
                        producerCompleted.set(true);
                    })) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is(EARLY_RESPONSE));
            }
            assertThat("STOP_SENDING should interrupt the request producer", producerCompleted.get(), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRetainFinalResponseWhenServerStopsStreamingRequestWithNoError() throws Exception {
        AtomicBoolean producerCompleted = new AtomicBoolean();
        CountDownLatch stopSendingScheduled = new CountDownLatch(1);
        byte[] responseBytes = EARLY_RESPONSE.getBytes(StandardCharsets.UTF_8);

        try (Http3RawTestServer server = Http3RawTestServer.create((_, connection, streamId, stream) -> {
                 stream.writeResponseHeaders(
                         Status.OK_200.code(),
                         WritableHeaders.create()
                                 .add(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                          String.valueOf(responseBytes.length))),
                         false);
                 stream.writeData(responseBytes, true);
                 ((QuicConnectionImpl) connection).scheduleStopSendingFrame(streamId,
                                                                            Http3ErrorCode.NO_ERROR.code());
                 stopSendingScheduled.countDown();
                 return null;
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                try (Http3ClientResponse response = client.post("/respond-without-request-body")
                        .sendExpectContinue(false)
                        .outputStream(outputStream -> {
                            try (outputStream) {
                                outputStream.write(1);
                                outputStream.flush();
                                try {
                                    assertThat("Server should schedule STOP_SENDING before the remaining upload",
                                               stopSendingScheduled.await(AWAIT_TIMEOUT.toMillis(),
                                                                          TimeUnit.MILLISECONDS),
                                               is(true));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(
                                            "Interrupted while awaiting STOP_SENDING.", e);
                                }
                                outputStream.write(LARGE_BODY_BYTES);
                            }
                            producerCompleted.set(true);
                        })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is(EARLY_RESPONSE));
                }
                assertThat("H3_NO_ERROR should interrupt the request producer", producerCompleted.get(), is(false));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldBackpressureIdleRequestWithoutBlockingConsumedStream() throws Exception {
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch allowConsumption = new CountDownLatch(1);
        CountDownLatch producerCompleted = new CountDownLatch(1);
        AtomicReference<String> stalledConnectionId = new AtomicReference<>();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 try {
                     if ("/active".equals(request.path().orElseThrow())) {
                         try (InputStream inputStream = stream.requestBodyInputStream()) {
                             inputStream.transferTo(OutputStream.nullOutputStream());
                         }
                         return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
                     }

                     stalledConnectionId.set(connection.childSocketId());
                     handlerStarted.countDown();
                     if (!allowConsumption.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                         throw new IllegalStateException("Timed out waiting to consume the request body.");
                     }
                     long received;
                     try (InputStream inputStream = stream.requestBodyInputStream()) {
                         received = inputStream.transferTo(OutputStream.nullOutputStream());
                     }
                     if (received != FLOW_CONTROL_BODY_SIZE) {
                         throw new IllegalStateException("Expected %d request bytes, got %d."
                                                                 .formatted(FLOW_CONTROL_BODY_SIZE, received));
                     }
                     return Http3RawTestServer.text(Status.OK_200.code(), "consumed");
                 } catch (IOException e) {
                     throw new UncheckedIOException("Failed to consume the HTTP/3 request body.", e);
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     throw new IllegalStateException("Interrupted while awaiting HTTP/3 request consumption.", e);
                 }
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                    try (Http3ClientResponse response = client.post("/backpressure")
                            .sendExpectContinue(false)
                            .outputStream(outputStream -> {
                                try (outputStream) {
                                    for (int sent = 0; sent < FLOW_CONTROL_BODY_SIZE; sent += CHUNK_BYTES.length) {
                                        outputStream.write(CHUNK_BYTES);
                                    }
                                }
                                producerCompleted.countDown();
                            })) {
                        return response.as(String.class);
                    }
                });

                assertThat("The server handler should receive request headers",
                           handlerStarted.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(true));
                assertThat("The producer must stop at the QUIC receive window while the handler is idle",
                           producerCompleted.await(BACKPRESSURE_OBSERVATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(false));
                assertThat("The stalled request must remain active while waiting for receive credit",
                           responseBody.isDone(),
                           is(false));

                try (Http3ClientResponse activeResponse = client.post("/active")
                        .sendExpectContinue(false)
                        .submit(CHUNK_BYTES)) {
                    assertThat(activeResponse.status(), is(Status.OK_200));
                    assertThat("A consumed stream should progress on the existing multiplexed connection",
                               activeResponse.as(String.class),
                               is(stalledConnectionId.get()));
                }

                allowConsumption.countDown();
                assertThat(responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("consumed"));
                assertThat("The producer should finish after handler consumption restores receive credit",
                           producerCompleted.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(true));
            } finally {
                allowConsumption.countDown();
                client.closeResource();
            }
        }
    }

    private static void streamLarge(ServerRequest req, ServerResponse res) {
        try (OutputStream outputStream = res.outputStream()) {
            for (int i = 0; i < REPETITIONS; i++) {
                outputStream.write(CHUNK_BYTES);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void echoLarge(ServerRequest req, ServerResponse res) {
        try (InputStream inputStream = req.content().inputStream();
             OutputStream outputStream = res.outputStream()) {
            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void echoSingleByte(ServerRequest req, ServerResponse res) {
        try (InputStream inputStream = req.content().inputStream();
             ByteArrayOutputStream collected = new ByteArrayOutputStream()) {
            int next;
            while ((next = inputStream.read()) != -1) {
                collected.write(next);
            }
            res.send(collected.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void echoStreamed(ServerRequest req, ServerResponse res) {
        try (InputStream inputStream = req.content().inputStream();
             OutputStream outputStream = res.outputStream()) {
            byte[] firstChunk = inputStream.readNBytes(CHUNK_BYTES.length);
            if (firstChunk.length != CHUNK_BYTES.length) {
                throw new IllegalStateException(
                        "Expected a complete first request chunk, got %d bytes.".formatted(firstChunk.length));
            }
            firstChunkConsumed.countDown();
            try {
                if (!remainderAllowed.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for the request producer to release the remainder.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting the request remainder.", e);
            }
            outputStream.write(firstChunk);
            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void respondWithoutRequestBody(ServerRequest req, ServerResponse res) {
        res.send(EARLY_RESPONSE);
        req.reset();
        earlyResponseQueued.countDown();
    }
}
