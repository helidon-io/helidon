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
 * Ping frame.
 */
public final class Http2Ping implements Http2Frame<Http2Flag.PingFlags> {
    private static final byte[] EMPTY_PING_DATA = new byte[8];
    private final BufferData data;

    Http2Ping(BufferData data) {
        this.data = data;
    }

    /**
     * Create ping from buffer.
     *
     * @param data frame buffer
     * @return ping frame
     */
    public static Http2Ping create(BufferData data) {
        return new Http2Ping(data);
    }

    /**
     * Create ping.
     *
     * @return ping frame
     */
    public static Http2Ping create() {
        return new Http2Ping(BufferData.create(EMPTY_PING_DATA));
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.PingFlags flags) {
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          frameTypes(),
                                                          flags,
                                                          streamId);

        return new Http2FrameData(header, data);
    }

    /**
     * Representation of ping data.
     *
     * @return frame data crated from this ping
     */
    public Http2FrameData toFrameData() {
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          frameTypes(),
                                                          Http2Flag.PingFlags.create(0),
                                                          0);

        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.PING.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.PING;
    }

    @Override
    public Http2FrameTypes<Http2Flag.PingFlags> frameTypes() {
        return Http2FrameTypes.PING;
    }

    /**
     * Get bytes of this frame.
     *
     * @return frame bytes
     */
    public byte[] getBytes() {
        byte[] result = new byte[data.available()];
        data.read(result, 0, result.length);
        data.rewind();
        return result;
    }

    /**
     * Underlying buffer.
     *
     * @return frame data
     */
    public BufferData data() {
        return data;
    }
}
