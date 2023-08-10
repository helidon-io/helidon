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
 * Frame data record.
 *
 * @param header frame header
 * @param data   frame data
 */
public record Http2FrameData(Http2FrameHeader header, BufferData data) {

    /**
     * Split this frame to smaller frames of maximum frame size.
     *
     * @param size maximum frame size
     * @return array of
     */
    public Http2FrameData[] split(int size) {
        int length = this.header().length();

        // Already smaller than max size
        if (length <= size || length == 0) {
            return new Http2FrameData[] {this};
        }

        // Zero max size fast path
        if (size == 0) {
            return new Http2FrameData[0];
        }

        // End of stream flag is set only to the last frame in the array
        boolean endOfStream = this.header().flags(Http2FrameTypes.DATA).endOfStream();

        int lastFrameSize = length % size;

        // Avoid creating 0 length last frame
        int allFrames = (length / size) + (lastFrameSize != 0 ? 1 : 0);
        Http2FrameData[] splitFrames = new Http2FrameData[allFrames];

        for (int i = 0; i < allFrames; i++) {
            boolean lastFrame = allFrames == i + 1;
            // only last frame can be smaller than max size
            byte[] data = new byte[lastFrame ? (lastFrameSize != 0 ? lastFrameSize : size) : size];
            this.data().read(data);
            BufferData bufferData = BufferData.create(data);
            splitFrames[i] = new Http2FrameData(
                    Http2FrameHeader.create(bufferData.available(),
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(endOfStream && lastFrame
                                                                               ? Http2Flag.END_OF_STREAM
                                                                               : 0),
                                            this.header().streamId()),
                    bufferData);
        }
        return splitFrames;
    }

    /**
     * Cut the frame of given size from larger frame,
     * returns two frames, first of given size, second with the rest of the data.
     *
     * @param size maximum frame size of the first frame
     * @return array of 0,1 or 2 frames
     */
    public Http2FrameData[] cut(int size) {
        int length = this.header().length();

        // Already smaller than max size
        if (length <= size || length == 0) {
            return new Http2FrameData[] {this};
        }

        // Zero max size fast path
        if (size == 0) {
            return new Http2FrameData[0];
        }

        // End of stream flag is set only to the last frame in the array
        boolean endOfStream = this.header.flags(Http2FrameTypes.DATA).endOfStream();

        byte[] data1 = new byte[size];
        byte[] data2 = new byte[length - size];

        this.data().read(data1);
        this.data().read(data2);

        BufferData bufferData1 = BufferData.create(data1);
        BufferData bufferData2 = BufferData.create(data2);

        Http2FrameData frameData1 =
                new Http2FrameData(Http2FrameHeader.create(bufferData1.available(),
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(0),
                                                           this.header().streamId()),
                                   bufferData1);

        Http2FrameData frameData2 =
                new Http2FrameData(Http2FrameHeader.create(bufferData2.available(),
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(endOfStream
                                                                                              ? Http2Flag.END_OF_STREAM
                                                                                              : 0),
                                                           this.header().streamId()),
                                   bufferData2);

        return new Http2FrameData[] {frameData1, frameData2};
    }

}
