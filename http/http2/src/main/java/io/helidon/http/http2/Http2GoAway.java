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

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.BufferData;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

/**
 * Go away frame.
 *
 * @param lastStreamId last stream ID that was correctly processed
 * @param errorCode    error code
 * @param details      details message
 */
public record Http2GoAway(int lastStreamId, Http2ErrorCode errorCode, String details)
        implements Http2Frame<Http2Flag.NoFlags> {

    /**
     * Create a go away frame from frame data.
     *
     * @param frame frame data
     * @return go away frame
     */
    public static Http2GoAway create(BufferData frame) {
        int lastStreamId = BufferData.toInt31(frame.readInt32());
        int errorCode = frame.readInt32();
        Http2ErrorCode code = Http2ErrorCode.get(errorCode);
        String details = frame.debugDataHex(false);
        return new Http2GoAway(lastStreamId, code, details);
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.NoFlags flags) {

        byte[] detailBytes = (details == null) ? EMPTY_BYTES : details.getBytes(StandardCharsets.UTF_8);
        // max size is maximal frame size - 4 bytes for last stream id + 4 bytes for error code
        long len = Math.min(detailBytes.length, settings.value(Http2Setting.MAX_FRAME_SIZE) - 8);
        BufferData data = BufferData.create(8 + (int) len);

        int toWriteLastStream = lastStreamId & 0x7FFFFFFF;
        if (toWriteLastStream != lastStreamId) {
            // the stream id is 32bits, not 31 bits
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempt to use 32bit integer for stream id, "
                    + "only 31 bits are allowed");
        }
        data.writeInt32(toWriteLastStream);
        data.writeInt32(errorCode.code());
        data.write(detailBytes, 0, (int) len);

        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          frameTypes(),
                                                          flags,
                                                          streamId);

        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.GO_AWAY.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.GO_AWAY;
    }

    @Override
    public Http2FrameTypes<Http2Flag.NoFlags> frameTypes() {
        return Http2FrameTypes.GO_AWAY;
    }
}
