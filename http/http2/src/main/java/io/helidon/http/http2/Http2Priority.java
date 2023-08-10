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
 * HTTP/2 priority frame.
 *
 * @param exclusive exclusive
 * @param streamId  stream ID this priority is for
 * @param weight    weigth of this stream
 */
public record Http2Priority(boolean exclusive, int streamId, int weight) implements Http2Frame<Http2Flag.NoFlags> {

    /**
     * Create priority frame from frame data.
     *
     * @param frame frame data
     * @return priority frame
     */
    public static Http2Priority create(BufferData frame) {
        int streamDependencyWithFlag = frame.readInt32();

        boolean exclusive = (streamDependencyWithFlag & (1 << 31)) != 0;
        int streamId = (streamDependencyWithFlag & 0x7FFFFFFF);
        int weight = frame.read() + 1;
        return new Http2Priority(exclusive, streamId, weight);
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int forStreamId, Http2Flag.NoFlags flags) {
        BufferData data = BufferData.create(5);
        int streamDependency = streamId & 0x7FFFFFFF;
        if (streamDependency != streamId) {
            // the stream id is 32bits, not 31 bits
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Attempt to use 32bit integer for stream id, "
                    + "only 31 bits are allowed");
        }

        if (exclusive) {
            streamDependency = streamId | (1 << 31);
        }
        data.writeInt32(streamDependency);
        data.write(weight);

        Http2FrameHeader header = Http2FrameHeader.create(5, frameTypes(), flags, forStreamId);
        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.PRIORITY.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.PRIORITY;
    }

    @Override
    public Http2FrameTypes<Http2Flag.NoFlags> frameTypes() {
        return Http2FrameTypes.PRIORITY;
    }
}
