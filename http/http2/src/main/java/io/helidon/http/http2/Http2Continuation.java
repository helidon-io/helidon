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
 * HTTP/2 Continuation frame.
 */
public final class Http2Continuation implements Http2Frame<Http2Flag.ContinuationFlags> {
    private final BufferData data;

    private Http2Continuation(BufferData data) {
        this.data = data;
    }

    /**
     * Create continuation from frame data.
     *
     * @param data buffer with frame data
     * @return continuation
     */
    public static Http2Continuation create(BufferData data) {
        return new Http2Continuation(data);
    }

    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.ContinuationFlags flags) {
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          frameTypes(),
                                                          flags,
                                                          streamId);
        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.CONTINUATION.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.CONTINUATION;
    }

    @Override
    public Http2FrameTypes<Http2Flag.ContinuationFlags> frameTypes() {
        return Http2FrameTypes.CONTINUATION;
    }

    /**
     * Get bytes from this continuation.
     *
     * @return bytes
     */
    public byte[] getBytes() {
        byte[] result = new byte[data.available()];
        data.read(result, 0, result.length);
        data.rewind();
        return result;
    }
}
