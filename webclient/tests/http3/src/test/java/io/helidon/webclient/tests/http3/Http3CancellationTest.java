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
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.REMOTE_CLOSE;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.CANCELLED;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3CancellationTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final String CANCEL_PATH = "/cancel";
    private static final String CONNECTION_ID_PATH = "/connection-id";
    private static final String TRANSPORT_CLOSE_PATH = "/transport-close";
    private static final String TRAILER_NAME = "x-cancel-trailer";
    private static final String FIRST_CHUNK = "cancelled-body-".repeat(32);
    private static final String FIRST_PREFIX = FIRST_CHUNK.substring(0, 32);

    @Test
    void shouldCancelTypedHttp3ResponseAndReuseConnection() throws Exception {
        CancellationProbe probe = new CancellationProbe();

        try (Http3RawTestServer server = Http3RawTestServer.create(probe::handle)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                try (Http3ClientResponse response = client.get(CANCEL_PATH).request()) {
                    assertCancelledResponse(response);
                }

                String connectionId = probe.connectionId();
                assertThat(probe.awaitStopSendingReceived(), is(true));

                try (Http3ClientResponse response = client.get(CONNECTION_ID_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is(connectionId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldCancelExplicitGenericHttp3ResponseAndReuseConnection() throws Exception {
        CancellationProbe probe = new CancellationProbe();

        try (Http3RawTestServer server = Http3RawTestServer.create(probe::handle)) {
            WebClient client = strictWebClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .build();

            try {
                try (HttpClientResponse response = client.get(CANCEL_PATH)
                        .protocolId(Http3Client.PROTOCOL_ID)
                        .request()) {
                    assertCancelledResponse(response);
                }

                String connectionId = probe.connectionId();
                assertThat(probe.awaitStopSendingReceived(), is(true));

                try (HttpClientResponse response = client.get(CONNECTION_ID_PATH)
                        .protocolId(Http3Client.PROTOCOL_ID)
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is(connectionId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldCancelActiveStreamAfterCleanTransportTermination() throws Exception {
        AtomicReference<QuicConnection> serverConnection = new AtomicReference<>();
        RecordingTransportObserverService observer = new RecordingTransportObserverService();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if (!TRANSPORT_CLOSE_PATH.equals(request.path().orElseThrow())) {
                     return Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
                 }
                 serverConnection.set(connection);
                 stream.writeResponseHeaders(Status.OK_200.code(), WritableHeaders.create(), false);
                 stream.writeData(FIRST_CHUNK.getBytes(UTF_8), false);
                 return null;
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .servicesDiscoverServices(false)
                    .addService(observer)
                    .build();

            try {
                Http3ClientResponse response = client.get(TRANSPORT_CLOSE_PATH).request();
                try {
                    assertThat(response.status(), is(Status.OK_200));
                    Objects.requireNonNull(serverConnection.get(), "Server connection was not captured")
                            .terminate(QuicCloseCommand.transport(QuicTransportErrors.NO_ERROR));

                    RecordingTransportObserverService.ConnectionRecord connection = observer.connections().getFirst();
                    assertThat(connection.streams().getFirst().outcome().get(10, TimeUnit.SECONDS), is(CANCELLED));
                    assertThat(connection.outcome().get(10, TimeUnit.SECONDS), is(REMOTE_CLOSE));
                } finally {
                    response.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    private static void assertCancelledResponse(HttpClientResponse response) {
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));

        try {
            assertThat(new String(response.inputStream().readNBytes(FIRST_PREFIX.length()), UTF_8), is(FIRST_PREFIX));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read HTTP/3 response prefix.", e);
        }

        response.close();
        IllegalStateException exception = assertThrows(IllegalStateException.class, response::trailers);
        assertThat(exception.getMessage(), is("HTTP/3 response closed before trailers were read."));
    }

    private static final class CancellationProbe {
        private final AtomicReference<String> connectionId = new AtomicReference<>();
        private final CountDownLatch cancelHandled = new CountDownLatch(1);
        private final CountDownLatch stopSendingObserved = new CountDownLatch(1);
        private final AtomicBoolean stopSendingReceived = new AtomicBoolean();

        private Http3RawTestServer.BufferedResponse handle(Http3Protocol.DecodedRequestHead request,
                                                           QuicConnection connection,
                                                           long streamId,
                                                           Http3RawTestServer.StreamControl stream) {
            return switch (request.path().orElseThrow()) {
                case CANCEL_PATH -> {
                    connectionId.set(connection.childSocketId());
                    cancelHandled.countDown();

                    stream.writeResponseHeaders(Status.OK_200.code(),
                                                headers(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                            "text/plain; charset=utf-8"),
                                                        HeaderValues.create(HeaderNames.TRAILER, TRAILER_NAME)),
                                                false);
                    stream.writeData(FIRST_CHUNK.getBytes(UTF_8), false);
                    Thread.ofVirtual().start(() -> observeStopSending(stream));
                    yield null;
                }
                case CONNECTION_ID_PATH ->
                        Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
                default -> Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
            };
        }

        private void observeStopSending(Http3RawTestServer.StreamControl stream) {
            long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
            boolean observed = false;

            while (System.nanoTime() < deadline) {
                if (stream.stopSendingReceived()) {
                    observed = true;
                    break;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            stopSendingReceived.set(observed || stream.stopSendingReceived());
            stopSendingObserved.countDown();
        }

        private String connectionId() {
            try {
                if (!cancelHandled.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Timed out waiting for cancellation request.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cancellation request.", e);
            }
            return Objects.requireNonNull(connectionId.get(), "Cancellation request did not capture a connection id.");
        }

        private boolean awaitStopSendingReceived() {
            try {
                if (!stopSendingObserved.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting STOP_SENDING.", e);
            }
            return stopSendingReceived.get();
        }
    }

    private static Headers headers(Header... headers) {
        WritableHeaders<?> writable = WritableHeaders.create();
        for (Header header : headers) {
            writable.add(header);
        }
        return writable;
    }
}
