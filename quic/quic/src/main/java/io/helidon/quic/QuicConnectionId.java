/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
import java.util.HexFormat;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * Models a Quic Connection id.
 * QuicConnectionId instance are typically created by a Quic client or server.
 */
// Connection IDs are used as keys in an ID to connection map.
// They implement Comparable to mitigate the penalty of hash collisions.
@Api.Internal
public abstract class QuicConnectionId implements Comparable<QuicConnectionId> {

    /**
     * The maximum length, in bytes, of a connection id.
     * This is supposed to be version-specific, but for now, we
     * are going to treat that as a universal constant.
     */
    public static final int MAX_CONNECTION_ID_LENGTH = 20;
    /**
     * Cached hash code of the connection-ID bytes.
     */
    private final int hashCode;
    /**
     * Read-only view of the connection-ID bytes.
     */
    private final ByteBuffer buf;

    /**
     * Create a connection ID backed by the supplied bytes.
     *
     * @param buf buffer exposing the connection-ID bytes
     */
    protected QuicConnectionId(ByteBuffer buf) {
        this.buf = buf.asReadOnlyBuffer();
        hashCode = this.buf.hashCode();
    }

    /**
     * Returns the length of this connection id, in bytes.
     *
     * @return the length of this connection id
     */
    public int length() {
        return buf.remaining();
    }

    /**
     * Returns this connection id bytes as buffer data.
     *
     * @return a new read-only buffer data containing this connection id bytes
     */
    public BufferData bufferData() {
        byte[] bytes = bytes();
        return BufferData.createReadOnly(bytes, 0, bytes.length);
    }

    /**
     * Returns this connection id bytes as a read-only buffer.
     *
     * @return A new read only buffer containing this connection id bytes.
     */
    public ByteBuffer asReadOnlyBuffer() {
        return buf.asReadOnlyBuffer();
    }

    /**
     * Returns this connection id bytes as a byte array.
     *
     * @return A new byte array containing this connection id bytes.
     */
    public byte[] bytes() {
        var length = length();
        byte[] bytes = new byte[length];
        buf.get(buf.position(), bytes, 0, length);
        return bytes;
    }

    /**
     * Compare this connection id bytes with the bytes in the
     * given byte buffer.
     * <p> The given byte buffer is expected to have
     * its {@linkplain ByteBuffer#position() position} set at the start
     * of the connection id, and its {@linkplain ByteBuffer#limit() limit}
     * at the end. In other words, {@code Buffer.remaining()} should
     * indicate the connection id length.
     * <p> This method does not advance the buffer position.
     *
     * @param idbytes A byte buffer containing the id bytes of another
     *               connection id.
     * @return {@code -1}, {@code 0}, or {@code 1} if this connection's id
     *        bytes are less, equal, or greater than the provided bytes.
     * @implSpec This is equivalent to: <pre>{@code
     *         this.asReadOnlyBuffer().comparesTo(idbytes)
     *         }</pre>
     */
    public int compareBytes(BufferData idbytes) {
        int length = buf.remaining();
        int otherLength = idbytes.available();
        int start = buf.position();
        int minLength = Math.min(length, otherLength);
        for (int i = 0; i < minLength; i++) {
            int cmp = Byte.compare(buf.get(start + i), (byte) idbytes.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(length, otherLength);
    }

    /**
     * Tells whether the given buffer matches this connection id.
     * The provided buffer is expected to expose exactly the bytes of the connection id.
     * This method does not advance the buffer read position.
     *
     * @param idbytes buffer that delimits a connection id
     * @return {@code true} if the bytes in the given buffer match this connection id bytes
     */
    public boolean matches(BufferData idbytes) {
        int length = buf.remaining();
        if (idbytes.available() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if ((buf.get(buf.position() + i) & 0xFF) != (idbytes.get(i) & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(QuicConnectionId o) {
        return buf.compareTo(o.buf);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuicConnectionId that) {
            return buf.equals(that.buf);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    /**
     * Returns a hexadecimal string representing this connection id bytes.
     *
     * @return a hexadecimal string representing this connection id bytes
     */
    public String toHexString() {
        return HexFormat.of().formatHex(bytes());
    }

    int compareBytes(ByteBuffer idbytes) {
        return buf.compareTo(idbytes);
    }

    /**
     * Tells whether the given byte buffer matches this connection id.
     * The given byte buffer is expected to have
     * its {@linkplain ByteBuffer#position() position} set at the start
     * of the connection id, and its {@linkplain ByteBuffer#limit() limit}
     * at the end. In other words, {@code Buffer.remaining()} should
     * indicate the connection id length.
     * <p> This method does not advance the buffer position.
     *
     * @param idbytes A buffer that delimits a connection id.
     * @return {@code true} if the bytes in the given buffer match this
     *        connection id bytes.
     * @implSpec This is equivalent to: <pre>{@code
     *         this.asReadOnlyBuffer().mismatch(idbytes) == -1
     *         }</pre>
     */
    boolean matches(ByteBuffer idbytes) {
        return buf.equals(idbytes);
    }

}
