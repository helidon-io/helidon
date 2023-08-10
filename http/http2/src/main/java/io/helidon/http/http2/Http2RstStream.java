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
 * RST Stream frame.
 *
 * @param errorCode error code of this frame
 */
public record Http2RstStream(Http2ErrorCode errorCode) implements Http2Frame<Http2Flag.NoFlags> {
    /**
     * Create a RST stream from frame data.
     *
     * @param bufferData frame data
     * @return RST stream frame
     */
    public static Http2RstStream create(BufferData bufferData) {
        return new Http2RstStream(Http2ErrorCode.get(bufferData.readInt32()));
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.NoFlags flags) {
        BufferData data = BufferData.create(4);
        data.writeInt32(errorCode.code());

        Http2FrameHeader header = Http2FrameHeader.create(4,
                                                          frameTypes(),
                                                          flags,
                                                          streamId);
        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.RST_STREAM.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.RST_STREAM;
    }

    @Override
    public Http2FrameTypes<Http2Flag.NoFlags> frameTypes() {
        return Http2FrameTypes.RST_STREAM;
    }
}
