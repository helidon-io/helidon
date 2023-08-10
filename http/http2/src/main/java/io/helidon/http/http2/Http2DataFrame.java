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
 * Data frame.
 */
public final class Http2DataFrame implements Http2Frame<Http2Flag.DataFlags> {
    private final BufferData bufferData;

    private Http2DataFrame(BufferData bufferData) {
        this.bufferData = bufferData;
    }

    /**
     * Create data frame from buffer.
     *
     * @param bufferData buffer
     * @return data frame
     */
    public static Http2DataFrame create(BufferData bufferData) {
        return new Http2DataFrame(bufferData);
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.DataFlags flags) {
        Http2FrameHeader header = Http2FrameHeader.create(bufferData.available(),
                                                          Http2FrameTypes.DATA,
                                                          flags,
                                                          streamId);
        return new Http2FrameData(header, bufferData);
    }

    @Override
    public String name() {
        return Http2FrameType.DATA.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.DATA;
    }

    @Override
    public Http2FrameTypes<Http2Flag.DataFlags> frameTypes() {
        return Http2FrameTypes.DATA;
    }
}
