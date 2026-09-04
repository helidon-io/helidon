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

package io.helidon.quic.frame;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.Utils;
import io.helidon.quic.VariableLengthEncoder;

/**
 * A CRYPTO frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class CryptoFrame extends QuicFrame {

    private final long offset;
    private final int length;
    private final ByteBuffer cryptoData;

    CryptoFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(CRYPTO);
        offset = decodeVLField(buffer, "offset");
        length = decodeVLFieldAsInt(buffer, "length");
        if (offset + length > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new QuicTransportException("Maximum crypto offset exceeded",
                                             type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, length, type);
        int pos = buffer.position();
        // The buffer is the datagram: we will make a copy if the datagram
        // is larger than the crypto frame by 64 bytes.
        cryptoData = Utils.sliceOrCopy(buffer, pos, length, 64);
        buffer.position(pos + length);
    }

    private CryptoFrame(long offset, int length, ByteBuffer cryptoData) {
        this(offset, length, cryptoData, true);
    }

    private CryptoFrame(long offset, int length, ByteBuffer cryptoData, boolean slice) {
        super(CRYPTO);
        this.offset = requireVLRange(offset, "offset");
        if (length != cryptoData.remaining()) {
            throw new IllegalArgumentException("bad length: " + length);
        }
        this.length = length;
        this.cryptoData = slice
                ? cryptoData.slice(cryptoData.position(), length)
                : cryptoData;
    }

    /**
     * Create an outgoing CRYPTO frame.
     *
     * @param offset     crypto stream offset
     * @param length     payload length in bytes
     * @param cryptoData payload buffer
     * @return new CRYPTO frame
     */
    public static CryptoFrame create(long offset, int length, ByteBuffer cryptoData) {
        return new CryptoFrame(offset, length, cryptoData);
    }

    /**
     * Compare frames by crypto-stream offset.
     *
     * @param cf1 first frame
     * @param cf2 second frame
     * @return comparison result based on {@link #offset()}
     */
    public static int compareOffsets(CryptoFrame cf1, CryptoFrame cf2) {
        return Long.compare(cf1.offset, cf2.offset);
    }

    /**
     * Creates a new CryptoFrame which is a slice of this crypto frame.
     *
     * @param offset the new offset
     * @param length the new length
     * @return a slice of the current crypto frame
     * @throws IndexOutOfBoundsException if the offset or length
     *                                  exceed the bounds of this crypto frame
     */
    public CryptoFrame slice(long offset, int length) {
        long offsetdiff = offset - offset();
        long oldlen = length();
        Objects.checkFromIndexSize(offsetdiff, length, oldlen);
        int pos = cryptoData.position();
        // safe cast to int since offsetdiff < length
        int newpos = Math.addExact(pos, (int) offsetdiff);
        ByteBuffer slice = Utils.sliceOrCopy(cryptoData, newpos, length);
        return new CryptoFrame(offset, length, slice, false);
    }

    @Override
    public void encode(ByteBuffer dest) {
        if (size() > dest.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(dest, CRYPTO, "type");
        encodeVLField(dest, offset, "offset");
        encodeVLField(dest, length, "length");
        putByteBuffer(dest, cryptoData);
    }

    @Override
    public int size() {
        return variableLengthFieldLength(CRYPTO)
                + variableLengthFieldLength(offset)
                + variableLengthFieldLength(length)
                + length;
    }

    /**
     * Returns the frame offset.
     *
     * @return frame offset
     */
    public long offset() {
        return offset;
    }

    /**
     * Returns the payload length in bytes.
     *
     * @return payload length in bytes
     */
    public int length() {
        return length;
    }

    /**
     * Returns the frame payload.
     *
     * @return frame payload
     */
    public ByteBuffer payload() {
        return cryptoData.slice();
    }

    @Override
    public String toString() {
        return "CryptoFrame("
                + "offset=" + offset
                + ", length=" + length
                + ')';
    }
}
