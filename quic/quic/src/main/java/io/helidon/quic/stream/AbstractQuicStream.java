/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import io.helidon.quic.QuicConnectionImpl;

/**
 * An abstract class to model a QuicStream.
 * A quic stream can be either unidirectional
 * or bidirectional. A unidirectional stream can
 * be opened for reading or for writing.
 * Concrete subclasses of {@code AbstractQuicStream} should
 * implement {@link QuicSenderStream} (unidirectional {@link
 * StreamMode#WRITE_ONLY} stream), or {@link QuicReceiverStream}
 * (unidirectional {@link StreamMode#READ_ONLY} stream), or
 * both (bidirectional {@link StreamMode#READ_WRITE} stream).
 */
abstract sealed class AbstractQuicStream implements QuicStream
        permits QuicBidiStreamImpl, QuicSenderStreamImpl, QuicReceiverStreamImpl {

    private final QuicConnectionImpl connection;
    private final long streamId;
    private final StreamMode mode;

    AbstractQuicStream(QuicConnectionImpl connection, long streamId) {
        this.mode = mode(connection, streamId);
        this.streamId = streamId;
        this.connection = connection;
    }

    @Override
    public final long streamId() {
        return streamId;
    }

    @Override
    public final StreamMode mode() {
        return mode;
    }

    @Override
    public final boolean isClientInitiated() {
        return QuicStreams.isClientInitiated(type());
    }

    @Override
    public final boolean isServerInitiated() {
        return QuicStreams.isServerInitiated(type());
    }

    @Override
    public final boolean isBidirectional() {
        return QuicStreams.isBidirectional(type());
    }

    @Override
    public final boolean isLocalInitiated() {
        return connection().isClientConnection() == isClientInitiated();
    }

    @Override
    public final boolean isRemoteInitiated() {
        return connection().isClientConnection() != isClientInitiated();
    }

    @Override
    public final int type() {
        return QuicStreams.streamType(streamId);
    }

    /**
     * Returns true if this stream isn't expecting anything
     * from the peer and can be removed from the streams map.
     *
     * @return true if this stream isn't expecting anything
     *         from the peer and can be removed from the streams map
     */
    public abstract boolean isDone();

    /**
     * {@return the {@code QuicConnectionImpl} instance this stream
     * belongs to}
     */
    final QuicConnectionImpl connection() {
        return connection;
    }

    private static StreamMode mode(QuicConnectionImpl connection, long streamId) {
        if (QuicStreams.isBidirectional(streamId)) {
            return StreamMode.READ_WRITE;
        }
        if (connection.isClientConnection()) {
            return QuicStreams.isClientInitiated(streamId)
                    ? StreamMode.WRITE_ONLY : StreamMode.READ_ONLY;
        } else {
            return QuicStreams.isClientInitiated(streamId)
                    ? StreamMode.READ_ONLY : StreamMode.WRITE_ONLY;
        }
    }

}
