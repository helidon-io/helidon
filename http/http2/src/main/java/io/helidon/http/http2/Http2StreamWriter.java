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
 * Writer for a single stream.
 */
public interface Http2StreamWriter {
    /**
     * Write a frame.
     *
     * @param frame frame to write
     */
    void write(Http2FrameData frame);

    /**
     * Write a frame with flow control.
     *
     * @param frame       data frame
     * @param flowControl outbound flow control
     */
    void writeData(Http2FrameData frame, FlowControl.Outbound flowControl);

    /**
     * Write headers with no (or streaming) entity.
     *
     * @param headers     headers
     * @param streamId    stream ID
     * @param flags       flags to use
     * @param flowControl flow control
     * @return number of bytes written
     */
    int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl);

    /**
     * Write headers and entity.
     *
     * @param headers     headers
     * @param streamId    stream ID
     * @param flags       header flags
     * @param dataFrame   data frame
     * @param flowControl flow control
     * @return number of bytes written
     */
    int writeHeaders(Http2Headers headers,
                     int streamId,
                     Http2Flag.HeaderFlags flags,
                     Http2FrameData dataFrame,
                     FlowControl.Outbound flowControl);
}
