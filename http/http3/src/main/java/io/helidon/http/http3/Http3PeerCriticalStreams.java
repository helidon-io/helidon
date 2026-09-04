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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.quic.stream.QuicReceiverStream;

/**
 * Connection-owned registry of peer HTTP/3 critical streams.
 */
@Api.Internal
public final class Http3PeerCriticalStreams {
    private final Map<Http3StreamType, ObservedStream> streams = new EnumMap<>(Http3StreamType.class);
    private final ReentrantLock lock = new ReentrantLock();
    private Throwable closeCause;

    private Http3PeerCriticalStreams() {
    }

    /**
     * Create an empty peer critical-stream registry.
     *
     * @return new registry
     */
    public static Http3PeerCriticalStreams create() {
        return new Http3PeerCriticalStreams();
    }

    /**
     * Claim a peer critical stream for this connection. Non-critical stream types are ignored.
     *
     * @param streamType stream type
     * @param stream peer critical stream
     * @param observation completion of the stream observation
     * @return whether the stream was claimed; {@code false} when the connection registry is already closed
     * @throws Http3ProtocolException when the peer already opened a critical stream of this type
     */
    public boolean claim(Http3StreamType streamType,
                         QuicReceiverStream stream,
                         CompletableFuture<Void> observation) {
        Objects.requireNonNull(streamType, "streamType");
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(observation, "observation");
        if (streamType == Http3StreamType.PUSH) {
            return true;
        }
        Throwable currentCloseCause;
        lock.lock();
        try {
            currentCloseCause = closeCause;
            if (currentCloseCause == null) {
                ObservedStream current = streams.putIfAbsent(streamType, new ObservedStream(stream, observation));
                if (current != null) {
                    throw Http3ProtocolException.connectionError(
                            Http3ErrorCode.STREAM_CREATION_ERROR,
                            "Duplicate HTTP/3 " + streamType + " stream " + stream.streamId()
                                    + "; existing stream " + current.stream().streamId());
                }
            }
        } finally {
            lock.unlock();
        }
        if (currentCloseCause != null) {
            observation.completeExceptionally(currentCloseCause);
            return false;
        }
        return true;
    }

    /**
     * End all peer critical-stream observations owned by this connection.
     *
     * @param cause connection close cause
     */
    public void close(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        List<CompletableFuture<Void>> observations;
        lock.lock();
        try {
            if (closeCause != null) {
                return;
            }
            closeCause = cause;
            observations = streams.values()
                    .stream()
                    .map(ObservedStream::observation)
                    .toList();
        } finally {
            lock.unlock();
        }
        observations.forEach(observation -> observation.completeExceptionally(cause));
    }

    private record ObservedStream(QuicReceiverStream stream, CompletableFuture<Void> observation) {
    }
}
