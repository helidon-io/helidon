/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http2;

import io.helidon.common.buffers.BufferData;

class FlowControlImpl implements FlowControl {

    private int streamId;
    private final WindowSize connectionWindowSize;
    private final WindowSize streamWindowSize;

    FlowControlImpl(int streamId, int streamInitialWindowSize, WindowSize connectionWindowSize) {
        this.streamId = streamId;
        this.connectionWindowSize = connectionWindowSize;
        this.streamWindowSize = new WindowSize(streamInitialWindowSize);
    }

    FlowControlImpl(int streamInitialWindowSize, WindowSize connectionWindowSize) {
        this.connectionWindowSize = connectionWindowSize;
        this.streamWindowSize = new WindowSize(streamInitialWindowSize);
    }

    public void streamId(int streamId){
        this.streamId = streamId;
    }

    @Override
    public void resetStreamWindowSize(long increment) {
        streamWindowSize.resetWindowSize(increment);
    }

    @Override
    public void decrementWindowSize(int decrement) {
        connectionWindowSize.decrementWindowSize(decrement);
        streamWindowSize.decrementWindowSize(decrement);
    }

    @Override
    public boolean incrementStreamWindowSize(int increment) {
        boolean overflow = streamWindowSize.incrementWindowSize(increment);
        connectionWindowSize.triggerUpdate();
        return overflow;
    }

    @Override
    public int getRemainingWindowSize() {
        return Integer.min(connectionWindowSize.getRemainingWindowSize(), streamWindowSize.getRemainingWindowSize());
    }

    @Override
    public Http2FrameData[] split(Http2FrameData frame) {
        return split(getRemainingWindowSize(), frame);
    }

    @Override
    public boolean blockTillUpdate() {
        return connectionWindowSize.blockTillUpdate();
    }

    @Override
    public String toString() {
        return "FlowControlImpl{"
                + "streamId=" + streamId
                + ", connectionWindowSize=" + connectionWindowSize
                + ", streamWindowSize=" + streamWindowSize
                + '}';
    }

    private Http2FrameData[] split(int size, Http2FrameData frame) {
        int length = frame.header().length();
        if (length <= size || length == 0) {
            return new Http2FrameData[] {frame};
        }

        if (size == 0) {
            return new Http2FrameData[0];
        }

        byte[] data1 = new byte[size];
        byte[] data2 = new byte[length - size];

        frame.data().read(data1);
        frame.data().read(data2);

        BufferData bufferData1 = BufferData.create(data1);
        BufferData bufferData2 = BufferData.create(data2);

        Http2FrameData frameData1 = new Http2FrameData(Http2FrameHeader.create(bufferData1.available(),
                                                                               Http2FrameTypes.DATA,
                                                                               Http2Flag.DataFlags.create(0),
                                                                               frame.header().streamId()),
                                                       bufferData1);

        Http2FrameData frameData2 = new Http2FrameData(Http2FrameHeader.create(bufferData2.available(),
                                                                               Http2FrameTypes.DATA,
                                                                               Http2Flag.DataFlags.create(0),
                                                                               frame.header().streamId()),
                                                       bufferData2);

        return new Http2FrameData[] {frameData1, frameData2};
    }
}
