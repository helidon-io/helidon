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

package io.helidon.http.http3;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.helidon.quic.stream.QuicReceiverStream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3PeerCriticalStreamsTest {
    @Test
    void rejectsDuplicateCriticalStreamsPerConnection() {
        for (Http3StreamType streamType : List.of(Http3StreamType.CONTROL,
                                                  Http3StreamType.QPACK_ENCODER,
                                                  Http3StreamType.QPACK_DECODER)) {
            Http3PeerCriticalStreams streams = Http3PeerCriticalStreams.create();
            streams.claim(streamType, stream(3), new CompletableFuture<>());

            Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                            () -> streams.claim(streamType,
                                                                                stream(7),
                                                                                new CompletableFuture<>()));
            assertThat(exception.errorCode(), equalTo(Http3ErrorCode.STREAM_CREATION_ERROR));
            assertThat(exception.scope(), equalTo(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void ignoresPushStreams() {
        Http3PeerCriticalStreams streams = Http3PeerCriticalStreams.create();

        streams.claim(Http3StreamType.PUSH, stream(3), new CompletableFuture<>());
        streams.claim(Http3StreamType.PUSH, stream(7), new CompletableFuture<>());
    }

    @Test
    void closesOwnedObservationsAndRejectsLateClaims() {
        Http3PeerCriticalStreams streams = Http3PeerCriticalStreams.create();
        CompletableFuture<Void> owned = new CompletableFuture<>();
        RuntimeException closeCause = new IllegalStateException("connection closed");

        assertThat(streams.claim(Http3StreamType.CONTROL, stream(3), owned), is(true));
        streams.close(closeCause);

        CompletionException ownedFailure = assertThrows(CompletionException.class, owned::join);
        assertThat(ownedFailure.getCause(), sameInstance(closeCause));

        CompletableFuture<Void> late = new CompletableFuture<>();
        assertThat(streams.claim(Http3StreamType.QPACK_ENCODER, stream(7), late), is(false));
        CompletionException lateFailure = assertThrows(CompletionException.class, late::join);
        assertThat(lateFailure.getCause(), sameInstance(closeCause));
    }

    private static QuicReceiverStream stream(long streamId) {
        return (QuicReceiverStream) Proxy.newProxyInstance(Http3PeerCriticalStreamsTest.class.getClassLoader(),
                                                           new Class<?>[] {QuicReceiverStream.class},
                                                           (proxy, method, arguments) -> switch (method.getName()) {
                                                               case "streamId" -> streamId;
                                                               case "hashCode" -> Long.hashCode(streamId);
                                                               case "toString" -> "stream-" + streamId;
                                                               case "equals" -> proxy == arguments[0];
                                                               default -> throw new UnsupportedOperationException(
                                                                       method.getName());
                                                           });
    }
}
