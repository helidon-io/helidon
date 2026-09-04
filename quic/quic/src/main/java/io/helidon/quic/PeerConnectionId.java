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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * A free-form connection ID to wrap the connection ID bytes
 * sent by the peer.
 * Client and server might impose some structure on the
 * connection ID bytes. For instance, they might choose to
 * encode the connection ID length in the connection ID bytes.
 * This class makes no assumption on the structure of the
 * connection id bytes.
 */
@Api.Internal
public final class PeerConnectionId extends QuicConnectionId {
    private final byte[] statelessResetToken;

    /**
     * A new {@link QuicConnectionId} represented by the given bytes.
     *
     * @param connId The connection ID bytes.
     */
    PeerConnectionId(byte[] connId) {
        super(ByteBuffer.wrap(Objects.requireNonNull(connId, "connId").clone()));
        this.statelessResetToken = null;
    }

    /**
     * A new {@link QuicConnectionId} represented by the remaining bytes in the given buffer.
     *
     * @param connId The connection ID bytes.
     */
    PeerConnectionId(ByteBuffer connId) {
        super(cloneBuffer(connId));
        this.statelessResetToken = null;
    }

    /**
     * A new {@link QuicConnectionId} represented by the bytes in the given buffer data.
     *
     * @param connId The connection ID bytes.
     */
    PeerConnectionId(BufferData connId) {
        super(cloneBuffer(connId));
        this.statelessResetToken = null;
    }

    /**
     * A new {@link QuicConnectionId} represented by the given bytes.
     *
     * @param connId              The connection ID bytes.
     * @param statelessResetToken The stateless reset token to be associated with this connection id.
     * @throws IllegalArgumentException If the {@code statelessResetToken} length isn't 16 bytes
     *
     */
    PeerConnectionId(ByteBuffer connId, byte[] statelessResetToken) {
        super(cloneBuffer(connId));
        Objects.requireNonNull(statelessResetToken, "statelessResetToken");
        if (statelessResetToken.length != 16) {
            throw new IllegalArgumentException("Invalid stateless reset token length "
                                                       + statelessResetToken.length);
        }
        this.statelessResetToken = statelessResetToken.clone();
    }

    /**
     * Creates a peer connection ID from raw bytes.
     *
     * @param connId connection-ID bytes
     * @return peer connection ID
     */
    public static PeerConnectionId create(byte[] connId) {
        return new PeerConnectionId(connId);
    }

    /**
     * Creates a peer connection ID from buffer data without a stateless-reset token.
     *
     * @param connId connection-ID bytes
     * @return peer connection ID
     */
    public static PeerConnectionId create(BufferData connId) {
        return new PeerConnectionId(connId);
    }

    /**
     * Creates a peer connection ID from buffer data and a stateless-reset token.
     *
     * @param connId              connection-ID bytes
     * @param statelessResetToken stateless-reset token
     * @return peer connection ID
     */
    public static PeerConnectionId create(BufferData connId, byte[] statelessResetToken) {
        return new PeerConnectionId(cloneBuffer(connId), statelessResetToken);
    }

    /**
     * Creates a peer connection ID from a byte-buffer view without a stateless-reset token.
     *
     * @param connId connection-ID bytes
     * @return peer connection ID
     */
    public static PeerConnectionId create(ByteBuffer connId) {
        return new PeerConnectionId(connId);
    }

    /**
     * Creates a peer connection ID from a byte-buffer view and a stateless-reset token.
     *
     * @param connId              connection-ID bytes
     * @param statelessResetToken stateless-reset token
     * @return peer connection ID
     */
    public static PeerConnectionId create(ByteBuffer connId, byte[] statelessResetToken) {
        return new PeerConnectionId(connId, statelessResetToken);
    }

    /**
     * Returns the stateless reset token associated with this connection id, if one exists.
     *
     * @return the stateless reset token associated with this connection id, if one exists.
     */
    public Optional<byte[]> statelessResetToken() {
        return Optional.ofNullable(this.statelessResetToken == null ? null : this.statelessResetToken.clone());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(length:" + length() + ')';
    }

    private static ByteBuffer cloneBuffer(BufferData src) {
        Objects.requireNonNull(src, "connId");
        byte[] idBytes = new byte[src.available()];
        for (int i = 0; i < idBytes.length; i++) {
            idBytes[i] = (byte) src.get(i);
        }
        return ByteBuffer.wrap(idBytes);
    }

    private static ByteBuffer cloneBuffer(ByteBuffer src) {
        Objects.requireNonNull(src, "connId");
        // we make a copy of the bytes and create a new
        // ByteBuffer here because we do not want to retain
        // the memory that was allocated for the original
        // ByteBuffer, which could ba a slice of a larger
        // buffer, such as the whole datagram payload.
        byte[] idBytes = new byte[src.remaining()];
        src.get(src.position(), idBytes);
        return ByteBuffer.wrap(idBytes);
    }
}
