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
import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.Utils;
import io.helidon.quic.packet.QuicPacketEncoder;

/**
 * A STREAM frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class StreamFrame extends QuicFrame {

    // Flags in frameType()
    private static final int OFF = 0x4;
    private static final int LEN = 0x2;
    private static final int FIN = 0x1;

    private final long streamID;
    // true if the OFF bit in the type field has been set
    private final boolean typeFieldHasOFF;
    private final long offset;
    private final int length; // -1 means consume all data in packet
    private final int dataLength;
    private final ByteBuffer streamData;
    private final PayloadOwnership payloadOwnership;
    private final boolean fin;

    StreamFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        this(buffer, type, PayloadOwnership.BORROWED);
    }

    private StreamFrame(ByteBuffer buffer, int type, PayloadOwnership payloadOwnership) throws QuicTransportException {
        super(STREAM);
        streamID = decodeVLField(buffer, "streamID");
        if ((type & OFF) > 0) {
            typeFieldHasOFF = true;
            offset = decodeVLField(buffer, "offset");
        } else {
            typeFieldHasOFF = false;
            offset = 0;
        }
        if ((type & LEN) > 0) {
            length = decodeVLFieldAsInt(buffer, "length");
        } else {
            length = -1;
        }
        if (length == -1) {
            int remaining = buffer.remaining();
            streamData = Utils.sliceOrCopy(buffer, buffer.position(), remaining);
            buffer.position(buffer.limit());
            dataLength = remaining;
        } else {
            validateRemainingLength(buffer, length, type);
            int pos = buffer.position();
            streamData = Utils.sliceOrCopy(buffer, pos, length);
            buffer.position(pos + length);
            dataLength = length;
        }
        this.payloadOwnership = payloadOwnership;
        fin = (type & FIN) == 1;
    }

    private StreamFrame(long streamID,
                        long offset,
                        int length,
                        boolean fin,
                        ByteBuffer streamData,
                        PayloadOwnership payloadOwnership) {
        this(streamID, offset, length, fin, streamData, payloadOwnership, true);
    }

    private StreamFrame(long streamID,
                        long offset,
                        int length,
                        boolean fin,
                        ByteBuffer streamData,
                        PayloadOwnership payloadOwnership,
                        boolean slice) {
        this(streamID, offset != 0, offset, length, fin, streamData, payloadOwnership, slice);
    }

    private StreamFrame(long streamID,
                        boolean typeFieldHasOFF,
                        long offset,
                        int length,
                        boolean fin,
                        ByteBuffer streamData,
                        PayloadOwnership payloadOwnership,
                        boolean slice) {
        super(STREAM);
        this.streamID = requireVLRange(streamID, "streamID");
        this.offset = requireVLRange(offset, "offset");
        Objects.requireNonNull(streamData, "streamData");
        this.typeFieldHasOFF = typeFieldHasOFF || this.offset != 0;
        if (length != -1 && length != streamData.remaining()) {
            throw new IllegalArgumentException("bad length");
        }
        this.length = length;
        this.dataLength = streamData.remaining();
        this.fin = fin;
        this.streamData = slice
                ? streamData.slice(streamData.position(), dataLength)
                : streamData;
        this.payloadOwnership = Objects.requireNonNull(payloadOwnership, "payloadOwnership");
    }

    static StreamFrame decodeOwned(ByteBuffer buffer, int type) throws QuicTransportException {
        return new StreamFrame(buffer, type, PayloadOwnership.OWNED);
    }

    /**
     * Creates StreamFrame (length == -1 means no length specified in frame
     * and is assumed to occupy the remainder of the Quic/UDP packet.
     * If a length is specified then it must correspond with the remaining bytes
     * in streamData
     *
     * @param streamID   stream identifier
     * @param offset     stream offset
     * @param length     payload length, or {@code -1} to consume the remaining packet bytes
     * @param fin        whether this frame carries the FIN flag
     * @param streamData borrowed stream payload; the caller must not modify it while the frame is in use
     * @return new STREAM frame
     */
    // It would be interesting to have a version of this constructor that can take
    // a list of ByteBuffer.
    public static StreamFrame create(long streamID, long offset, int length, boolean fin, ByteBuffer streamData) {
        return new StreamFrame(streamID, offset, length, fin, streamData, PayloadOwnership.BORROWED);
    }

    /**
     * Creates a STREAM frame and transfers ownership of the payload storage to the frame.
     *
     * <p>The caller transfers the payload range visible through {@code streamData} and must not modify that range after this
     * method returns. Non-overlapping ranges in the same backing storage may continue to be used. Derived frame slices preserve
     * ownership of their ranges.
     *
     * @param streamID   stream identifier
     * @param offset     stream offset
     * @param length     payload length, or {@code -1} to consume the remaining packet bytes
     * @param fin        whether this frame carries the FIN flag
     * @param streamData stream payload whose storage is transferred to the frame
     * @return new STREAM frame
     */
    @Api.Internal
    public static StreamFrame createOwned(long streamID,
                                          long offset,
                                          int length,
                                          boolean fin,
                                          ByteBuffer streamData) {
        return new StreamFrame(streamID, offset, length, fin, streamData, PayloadOwnership.OWNED);
    }

    /**
     * Compares two stream frames by their offsets.
     *
     * @param sf1 first frame
     * @param sf2 second frame
     * @return a negative value, zero, or a positive value if the first offset is less than,
     *        equal to, or greater than the second offset
     */
    public static int compareOffsets(StreamFrame sf1, StreamFrame sf2) {
        return Long.compare(sf1.offset, sf2.offset);
    }

    /**
     * Computes the header size that would be required to encode a frame with
     * the given streamId, offset, and length.
     *
     * @param encoder  the {@code QuicPacketEncoder} - which can be used in case
     *                some part of the computation is Quic-version dependent.
     * @param streamId the stream id
     * @param offset   the stream offset
     * @param length   the estimated length of the frame, typically this will be
     *                the min between the data available in the stream with respect
     *                to flow control, and the maximum remaining size for the datagram
     *                payload
     * @return the estimated size of the header for a {@code StreamFrame} that would
     *        be created with the given parameters.
     * <p>Note: This method is useful to figure out how many bytes can be allocated for
     *        the frame data, given a size constraint imposed by the space available
     *        for the whole datagram payload.
     */
    public static int headerSize(QuicPacketEncoder encoder, long streamId, long offset, long length) {
        // the header length is the size needed to encode the frame type,
        // plus the size needed to encode the streamId, plus the size needed
        // to encode the offset (if not 0) and the size needed to encode the
        // length (if present)
        int headerLength = variableLengthFieldLength(STREAM | OFF | LEN | FIN)
                + variableLengthFieldLength(streamId);
        if (offset != 0) {
            headerLength += variableLengthFieldLength(offset);
        }
        if (length >= 0) {
            headerLength += variableLengthFieldLength(length);
        }
        return headerLength;
    }

    /**
     * Creates a new StreamFrame which is a slice of this stream frame.
     * Owned slices retain ownership of their payload range; borrowed slices remain dependent on caller storage until
     * canonicalized for retention.
     *
     * @param offset the new offset
     * @param length the new length
     * @return a slice of the current stream frame
     * @throws IndexOutOfBoundsException if the offset or length
     *                                  exceed the bounds of this stream frame
     */
    public StreamFrame slice(long offset, int length) {
        long oldoffset = offset();
        long offsetdiff = offset - oldoffset;
        long oldlen = dataLength();
        Objects.checkFromIndexSize(offsetdiff, length, oldlen);
        int pos = streamData.position();
        // safe cast to int since offsetdiff < length
        int newpos = Math.addExact(pos, (int) offsetdiff);
        // preserves the FIN bit if set
        boolean fin = this.fin && offset + length == oldoffset + oldlen;
        ByteBuffer slice = Utils.sliceOrCopy(streamData, newpos, length);
        return new StreamFrame(streamID,
                               typeFieldHasOFF,
                               offset,
                               length,
                               fin,
                               slice,
                               payloadOwnership,
                               false);
    }

    /**
     * Returns the stream id.
     *
     * @return stream id
     */
    public long streamId() {
        return streamID;
    }

    /**
     * Checks whether this frame has a length.
     * A frame that doesn't have a length must be the last
     * frame in the packet.
     *
     * @return whether this frame has a length
     */
    public boolean hasLength() {
        return length != -1;
    }

    /**
     * Checks whether this is the last frame in the stream.
     * The last frame has the FIN bit set.
     *
     * @return true if this is the last frame in the stream
     */
    public boolean isLast() {
        return fin;
    }

    @Override
    public long typeField() {
        return STREAM | (hasLength() ? LEN : 0)
                | (typeFieldHasOFF ? OFF : 0)
                | (fin ? FIN : 0);
    }

    @Override
    public void encode(ByteBuffer dest) {
        if (size() > dest.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(dest, typeField(), "type");
        encodeVLField(dest, streamID, "streamID");
        if (typeFieldHasOFF) {
            encodeVLField(dest, offset, "offset");
        }
        if (hasLength()) {
            encodeVLField(dest, length, "length");
        }
        putByteBuffer(dest, streamData);
    }

    @Override
    public int size() {
        int size = variableLengthFieldLength(typeField())
                + variableLengthFieldLength(streamID);
        if (typeFieldHasOFF) {
            size += variableLengthFieldLength(offset);
        }
        if (hasLength()) {
            return size + variableLengthFieldLength(length) + length;
        } else {
            return size + streamData.remaining();
        }
    }

    /**
     * Returns a read-only view of the frame payload.
     *
     * @return frame payload
     */
    public ByteBuffer payload() {
        return streamData.asReadOnlyBuffer();
    }

    /**
     * Returns a frame whose payload can be retained independently of the caller.
     *
     * <p>Frames created by {@link QuicFrame#decodeOwned(ByteBuffer)} or
     * {@link #createOwned(long, long, int, boolean, ByteBuffer)} already own their payload and are returned unchanged. Public
     * {@link QuicFrame#decode(ByteBuffer)} and {@link #create(long, long, int, boolean, ByteBuffer)} borrow caller storage, so
     * this method returns a new frame backed by an exact independent copy. Receive paths must call this method before retaining
     * a frame for reassembly.
     *
     * @return this frame when its payload is owned, otherwise a frame with an independently owned payload copy
     */
    @Api.Internal
    public StreamFrame withOwnedPayload() {
        if (payloadOwnership == PayloadOwnership.OWNED) {
            return this;
        }
        ByteBuffer source = streamData.asReadOnlyBuffer();
        ByteBuffer copy = ByteBuffer.allocate(source.remaining());
        copy.put(source).flip();
        return new StreamFrame(streamID,
                               typeFieldHasOFF,
                               offset,
                               length,
                               fin,
                               copy,
                               PayloadOwnership.OWNED,
                               false);
    }

    /**
     * Returns a read-only buffer-data view of this frame's owned payload.
     *
     * <p>Payloads with an accessible backing array, as reported by {@link ByteBuffer#hasArray()}, are transferred without
     * copying. The returned view retains the owned array independently of this frame, and therefore retains that array's
     * complete allocation until the view becomes unreachable. Owned payloads whose storage cannot be exposed as an array use
     * an exact read-only copy instead. Each invocation returns a new read cursor.
     *
     * @return read-only payload data
     * @throws IllegalStateException if this frame still borrows caller storage
     */
    @Api.Internal
    public BufferData ownedPayloadData() {
        if (payloadOwnership != PayloadOwnership.OWNED) {
            throw new IllegalStateException("Cannot transfer a borrowed STREAM frame payload");
        }
        if (streamData.hasArray()) {
            return BufferData.createReadOnly(streamData.array(),
                                             streamData.arrayOffset() + streamData.position(),
                                             streamData.remaining());
        }
        ByteBuffer source = streamData.duplicate();
        byte[] copy = new byte[source.remaining()];
        source.get(copy);
        return BufferData.createReadOnly(copy, 0, copy.length);
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
     * Returns the number of data bytes in the frame.
     *
     * @return number of data bytes in the frame
     * <p>Note: This is equivalent to calling {@code payload().remaining()}.
     */
    public int dataLength() {
        return dataLength;
    }

    @Override
    public String toString() {
        return "StreamFrame(stream=" + streamID
                + ", offset=" + offset
                + ", length=" + length
                + ", fin=" + fin + ')';
    }

    private enum PayloadOwnership {
        OWNED,
        BORROWED
    }
}
