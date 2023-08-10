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

/**
 * HTTP/2 frame.
 *
 * @param <T> type of supported flags
 */
public sealed interface Http2Frame<T extends Http2Flag>
        permits Http2Continuation,
                Http2DataFrame,
                Http2GoAway,
                Http2Ping,
                Http2Priority,
                Http2RstStream,
                Http2Settings,
                Http2WindowUpdate {
    /**
     * Not implemented in headers, data, as these may use continuations.
     *
     * @param settings settings
     * @param streamId stream id of this frame
     * @param flags    to use
     * @return frame data
     */
    Http2FrameData toFrameData(Http2Settings settings, int streamId, T flags);

    /**
     * Frame name.
     *
     * @return frame type name
     */
    String name();

    /**
     * Frame type enum.
     *
     * @return type of this frame
     */
    Http2FrameType frameType();

    /**
     * Frame types.
     *
     * @return frame types
     */
    Http2FrameTypes<T> frameTypes();
}
