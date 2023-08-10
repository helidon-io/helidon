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
 * Window update frame.
 *
 * @param windowSizeIncrement window size increment
 */
public record Http2WindowUpdate(int windowSizeIncrement) implements Http2Frame<Http2Flag.NoFlags> {
    /**
     * Create from frame data.
     *
     * @param bufferData frame data
     * @return window update frame
     */
    public static Http2WindowUpdate create(BufferData bufferData) {
        return new Http2WindowUpdate(bufferData.readInt32() & 0x7FFFFFFF);
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.NoFlags flags) {
        BufferData data = BufferData.create(4);
        data.writeInt32(windowSizeIncrement);

        Http2FrameHeader header = Http2FrameHeader.create(4,
                                                          frameTypes(),
                                                          flags,
                                                          streamId);

        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.WINDOW_UPDATE.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.WINDOW_UPDATE;
    }

    @Override
    public Http2FrameTypes<Http2Flag.NoFlags> frameTypes() {
        return Http2FrameTypes.WINDOW_UPDATE;
    }
}
