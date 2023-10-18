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
 * HTTP/2 stream.
 * A stream represents a single request/response exchange in HTTP/2.
 */
public interface Http2Stream {
    /**
     * Close the stream.
     *
     * @param rstStream rst stream frame
     * @return true if rapid reset(rst received before any data are sent)
     */
    boolean rstStream(Http2RstStream rstStream);

    /**
     * Flow control window update.
     *
     * @param windowUpdate window update frame
     */
    void windowUpdate(Http2WindowUpdate windowUpdate);

    /**
     * Headers received.
     *
     * @param headers     headers
     * @param endOfStream whether these headers are the last data that would be received
     */
    void headers(Http2Headers headers, boolean endOfStream);

    /**
     * Data frame.
     *
     * @param header frame header
     * @param data   frame data
     * @param endOfStream whether this is the last data that would be received
     */
    void data(Http2FrameHeader header, BufferData data, boolean endOfStream);

    /**
     * Priority.
     *
     * @param http2Priority priority frame
     */
    void priority(Http2Priority http2Priority);

    /**
     * Stream ID.
     *
     * @return id of this stream
     */
    int streamId();

    /**
     * State of this stream.
     *
     * @return state
     */
    Http2StreamState streamState();

    /**
     * Outbound flow control of this stream.
     *
     * @return flow control
     */
    StreamFlowControl flowControl();

}
