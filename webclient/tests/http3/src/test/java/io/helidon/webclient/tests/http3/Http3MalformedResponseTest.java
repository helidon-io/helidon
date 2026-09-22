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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicConnection;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3MalformedResponseTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final String MALFORMED_HEAD_PATH = "/malformed-head";
    private static final String MALFORMED_NO_ENTITY_PATH = "/malformed-no-entity";
    private static final String CONNECTION_ID_PATH = "/connection-id";

    @Test
    void shouldAbortMalformedResponsesAndReuseConnection() throws Exception {
        MalformedResponseProbe probe = new MalformedResponseProbe();
        try (Http3RawTestServer server = Http3RawTestServer.create(probe::handle)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                assertThrows(RuntimeException.class, () -> client.get(MALFORMED_HEAD_PATH).request());
                assertThrows(RuntimeException.class, () -> client.get(MALFORMED_NO_ENTITY_PATH).request());

                try (Http3ClientResponse response = client.get(CONNECTION_ID_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is(probe.connectionId()));
                }
                assertThat(probe.awaitStopSending(), is(true));
            } finally {
                client.closeResource();
            }
        }
    }

    private static final class MalformedResponseProbe {
        private final AtomicReference<String> connectionId = new AtomicReference<>();
        private final CountDownLatch stopSendingObserved = new CountDownLatch(2);
        private final AtomicBoolean exactErrorsObserved = new AtomicBoolean(true);

        private Http3RawTestServer.BufferedResponse handle(Http3Protocol.DecodedRequestHead request,
                                                           QuicConnection connection,
                                                           long streamId,
                                                           Http3RawTestServer.StreamControl stream) {
            connectionId.compareAndSet(null, connection.childSocketId());
            return switch (request.path().orElseThrow()) {
                case MALFORMED_HEAD_PATH -> {
                    stream.writeResponseHeaders(Status.SWITCHING_PROTOCOLS_101.code(),
                                                WritableHeaders.create(),
                                                false);
                    Thread.ofVirtual().start(() -> observeStopSending(stream));
                    yield null;
                }
                case MALFORMED_NO_ENTITY_PATH -> {
                    stream.writeResponseHeaders(Status.NO_CONTENT_204.code(), WritableHeaders.create(), false);
                    stream.writeData(new byte[] {'x'}, false);
                    Thread.ofVirtual().start(() -> observeStopSending(stream));
                    yield null;
                }
                case CONNECTION_ID_PATH -> Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
                default -> Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
            };
        }

        private void observeStopSending(Http3RawTestServer.StreamControl stream) {
            long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
            while ((!stream.stopSendingReceived()
                    || stream.stopSendingErrorCode() != Http3ErrorCode.MESSAGE_ERROR.code())
                    && System.nanoTime() < deadline) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!stream.stopSendingReceived()
                    || stream.stopSendingErrorCode() != Http3ErrorCode.MESSAGE_ERROR.code()) {
                exactErrorsObserved.set(false);
            }
            stopSendingObserved.countDown();
        }

        private String connectionId() {
            return Objects.requireNonNull(connectionId.get(), "Malformed response did not capture a connection id.");
        }

        private boolean awaitStopSending() throws InterruptedException {
            return stopSendingObserved.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    && exactErrorsObserved.get();
        }
    }
}
