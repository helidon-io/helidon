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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3CriticalStreamTest {
    private static final Duration STREAM_OPEN_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void shouldCloseConnectionWhenPeerQpackCriticalStreamCloses() throws Exception {
        CompletableFuture<QuicTermination> peerTermination = new CompletableFuture<>();
        try (Http3RawTestServer server = Http3RawTestServer.createRawPeer(connection -> {
                 connection.whenTerminated().whenComplete((termination, throwable) -> {
                     if (throwable == null) {
                         peerTermination.complete(termination);
                     } else {
                         peerTermination.completeExceptionally(throwable);
                     }
                 });
                 QuicSenderStream controlStream = connection.openNewLocalUniStream(STREAM_OPEN_TIMEOUT).join();
                 Http3StreamSupport.writeAll(controlStream,
                                             Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)),
                                             false,
                                             connection);
                 QuicSenderStream qpackStream = connection.openNewLocalUniStream(STREAM_OPEN_TIMEOUT).join();
                 Http3StreamSupport.writeAll(qpackStream,
                                             Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER),
                                             true,
                                             connection);
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();
            try {
                assertThrows(RuntimeException.class, () -> client.get("/closed-qpack-stream").request());
                QuicTermination termination = peerTermination.get(10, TimeUnit.SECONDS);
                assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
                assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
                assertThat(termination.layer(), is(QuicTermination.Layer.APPLICATION));
                assertThat(termination.errorCode().orElseThrow(),
                           is(Http3ErrorCode.CLOSED_CRITICAL_STREAM.code()));
            } finally {
                client.closeResource();
            }
        }
    }
}
