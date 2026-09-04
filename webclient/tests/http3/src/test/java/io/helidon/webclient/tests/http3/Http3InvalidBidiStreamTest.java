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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3InvalidBidiStreamTest {
    private static final Duration STREAM_OPEN_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void shouldCloseConnectionAndCleanUpOneServerInitiatedBidirectionalStream() throws Exception {
        assertInvalidBidirectionalStreams(1);
    }

    @Test
    void shouldCloseConnectionAndCleanUpManyServerInitiatedBidirectionalStreams() throws Exception {
        assertInvalidBidirectionalStreams(8);
    }

    private static void assertInvalidBidirectionalStreams(int streamCount) throws Exception {
        CompletableFuture<QuicTermination> peerTermination = new CompletableFuture<>();
        CompletableFuture<List<QuicBidiStream>> openedStreams = new CompletableFuture<>();
        try (Http3RawTestServer server = Http3RawTestServer.createRawPeer(connection -> {
                 connection.whenTerminated().whenComplete((termination, throwable) -> {
                     if (throwable == null) {
                         peerTermination.complete(termination);
                     } else {
                         peerTermination.completeExceptionally(throwable);
                     }
                 });
                 try {
                     List<QuicBidiStream> streams = new ArrayList<>(streamCount);
                     for (int i = 0; i < streamCount; i++) {
                         streams.add(connection.openNewLocalBidiStream(STREAM_OPEN_TIMEOUT).join());
                     }
                     openedStreams.complete(List.copyOf(streams));
                     for (QuicBidiStream stream : streams) {
                         Http3StreamSupport.writeAll(stream, new byte[] {1}, false, connection);
                     }
                 } catch (RuntimeException | Error failure) {
                     openedStreams.completeExceptionally(failure);
                     throw failure;
                 }
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();
            try {
                RuntimeException requestFailure = assertThrows(
                        RuntimeException.class,
                        () -> client.get("/invalid-bidi-stream").request());

                QuicTermination termination = peerTermination.get(10, TimeUnit.SECONDS);
                assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
                assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
                assertThat(termination.layer(), is(QuicTermination.Layer.APPLICATION));
                assertThat("request failure: " + requestFailure
                                   + ", cause: " + requestFailure.getCause()
                                   + ", root: " + (requestFailure.getCause() == null
                                           ? null
                                           : requestFailure.getCause().getCause()),
                           termination.errorCode().orElseThrow(),
                           is(Http3ErrorCode.STREAM_CREATION_ERROR.code()));
                for (QuicBidiStream stream : openedStreams.get(10, TimeUnit.SECONDS)) {
                    assertThat(stream.futureSendingCompletion().isDone(), is(true));
                }
            } finally {
                client.closeResource();
            }
        }
    }
}
