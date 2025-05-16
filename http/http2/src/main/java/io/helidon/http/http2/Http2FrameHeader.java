/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.http2;

import io.helidon.common.buffers.BufferData;

/**
 * Frame header.
 * HTTP/2 frame header has a fixed length of {@value #LENGTH} bytes.
 */
public class Http2FrameHeader {
    /**
     * Frame header length in bytes.
     */
    public static final int LENGTH = 9;
    private final Http2FrameType type;
    private final int length;
    private final int flags;
    private final int streamId;

    Http2FrameHeader(Http2FrameType type, int length, int flags, int streamId) {
        this.type = type;
        this.length = length;
        this.flags = flags;
        this.streamId = streamId;
    }

    /**
     * Create a header from header bytes.
     *
     * @param bytes frame header data, contains at least {@value #LENGTH} bytes.
     * @return frame header parsed from the data
     */
    public static Http2FrameHeader create(BufferData bytes) {
        // frame header is 9 bytes
        int length = bytes.readInt24();  // 3 bytes
        short type = (short) bytes.read(); // 1 byte
        short flags = (short) bytes.read(); // 1 byte
        // 1 bit (ignored, reserved)
        int streamIdentifier = bytes.readInt32() & 0x7FFFFFFF; // 4 bytes (-1 bit above)

        return new Http2FrameHeader(Http2FrameType.byId(type),
                                    length,
                                    flags,
                                    streamIdentifier);
    }

    /**
     * Create a frame header from known values.
     *
     * @param length           length of the associate frame data
     * @param frameType        frame types of this frame
     * @param flags            flags of the frame
     * @param streamIdentifier stream this frame belongs to
     * @param <T>              type of flags
     * @return frame header
     */
    public static <T extends Http2Flag> Http2FrameHeader create(int length,
                                                                Http2FrameTypes<T> frameType,
                                                                T flags,
                                                                int streamIdentifier) {
        // TODO validate
        // length <= 3 bytes
        // stream identifier <= 31 bits (4 bytes - 1 bit)
        return new Http2FrameHeader(frameType.type(),
                                    length,
                                    flags.value(),
                                    streamIdentifier);
    }

    @Override
    public String toString() {
        return streamId + " " + type + " (" + length + " bytes)";
    }

    /**
     * Get typed flags of this frame header.
     *
     * @param types frame types
     * @param <T>   type of flags
     * @return correctly typed flags
     */
    public <T extends Http2Flag> T flags(Http2FrameTypes<T> types) {
        if (types.type() != type) {
            throw new IllegalArgumentException("Attempting to get flags for type " + types.type()
                                                       + " in frame header of type " + type);
        }
        return types.flags(flags);
    }

    /**
     * Frame type enum.
     *
     * @return frame type
     */
    public Http2FrameType type() {
        return type;
    }

    /**
     * Length of the associated frame data.
     *
     * @return frame data length
     */
    public int length() {
        return length;
    }

    /**
     * Flags as an integer.
     *
     * @return flags
     */
    public int flags() {
        return flags;
    }

    /**
     * Stream id this header belongs to.
     *
     * @return stream id ({@code 0} means this is connection related, not stream related)
     */
    public int streamId() {
        return streamId;
    }

    /**
     * Write this header as buffer data.
     *
     * @return buffer data of this headewr
     */
    public BufferData write() {
        BufferData bufferData = BufferData.create(9);

        bufferData.writeInt24(length);
        bufferData.writeInt8(type.type());
        bufferData.writeInt8(flags);
        bufferData.writeInt32(streamId);

        return bufferData;
    }

    /**
     * Flags typed correctly based on the {@link #type()}.
     *
     * @return typed flags
     * @see #flags(Http2FrameTypes)
     */
    public Http2Flag typedFlags() {
        return Http2FrameTypes.get(type).flags(flags);
    }
}
