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
package io.helidon.nima.http2;

import java.util.Objects;
import java.util.function.Consumer;

abstract class FlowControlImpl implements FlowControl {

    private final int streamId;

    FlowControlImpl(int streamId) {
        this.streamId = streamId;
    }

    abstract WindowSize connectionWindowSize();
    abstract WindowSize streamWindowSize();

    public void decrementWindowSize(int decrement) {
        connectionWindowSize().decrementWindowSize(decrement);
        streamWindowSize().decrementWindowSize(decrement);
    }

    @Override
    public void resetStreamWindowSize(long increment) {
        streamWindowSize().resetWindowSize(increment);
    }

    @Override
    public int getRemainingWindowSize() {
        return Math.max(0,
                        Integer.min(
                                connectionWindowSize().getRemainingWindowSize(),
                                streamWindowSize().getRemainingWindowSize()
                        )
        );
    }

    @Override
    public String toString() {
        return "FlowControlImpl{"
                + "streamId=" + streamId
                + ", connectionWindowSize=" + connectionWindowSize()
                + ", streamWindowSize=" + streamWindowSize()
                + '}';
    }

    static class Inbound extends FlowControlImpl implements FlowControl.Inbound {

        private final WindowSize.Inbound connectionWindowSize;
        private final WindowSize.Inbound streamWindowSize;

        Inbound(int streamId,
                int streamInitialWindowSize,
                int streamMaxFrameSize,
                WindowSize.Inbound connectionWindowSize,
                Consumer<Http2WindowUpdate> windowUpdateStreamWriter) {
            super(streamId);
            if (streamInitialWindowSize == 0) {
                throw new IllegalArgumentException("Window size in bytes for stream-level flow control was not set.");
            }
            Objects.requireNonNull(connectionWindowSize, "Window size in bytes for connection-level flow control was not set.");
            Objects.requireNonNull(windowUpdateStreamWriter, "Stream-level window update writer was not set.");
            this.connectionWindowSize = connectionWindowSize;
            this.streamWindowSize = WindowSize.createInbound(streamInitialWindowSize,
                                                             streamMaxFrameSize,
                                                             windowUpdateStreamWriter);
        }

        @Override
        WindowSize connectionWindowSize() {
            return connectionWindowSize;
        }

        @Override
        WindowSize streamWindowSize() {
            return streamWindowSize;
        }

        @Override
        public void incrementWindowSize(int increment) {
            streamWindowSize.incrementWindowSize(increment);
            connectionWindowSize.incrementWindowSize(increment);
        }

    }

    static class Outbound extends FlowControlImpl implements FlowControl.Outbound {

        private final WindowSize.Outbound connectionWindowSize;
        private final WindowSize.Outbound streamWindowSize;

        Outbound(int streamId,
                 int streamInitialWindowSize,
                 WindowSize.Outbound connectionWindowSize) {
            super(streamId);
            this.connectionWindowSize = connectionWindowSize;
            this.streamWindowSize = WindowSize.createOutbound(streamInitialWindowSize);
        }

        @Override
        WindowSize connectionWindowSize() {
            return connectionWindowSize;
        }

        @Override
        WindowSize streamWindowSize() {
            return streamWindowSize;
        }

        @Override
        public boolean incrementStreamWindowSize(int increment) {
            boolean result = streamWindowSize.incrementWindowSize(increment);
            connectionWindowSize.triggerUpdate();
            return result;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            return frame.cut(getRemainingWindowSize());
        }

        @Override
        public void blockTillUpdate() {
            connectionWindowSize.blockTillUpdate();
            streamWindowSize.blockTillUpdate();
        }
    }

}
