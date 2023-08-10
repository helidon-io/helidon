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

import java.util.function.BiConsumer;

class FlowControlNoop implements FlowControl {

    @Override
    public void decrementWindowSize(int decrement) {
    }

    @Override
    public void resetStreamWindowSize(int size) {
    }

    @Override
    public int getRemainingWindowSize() {
        return Integer.MAX_VALUE;
    }


    // Even NOOP sends WINDOW_UPDATE frames
    static class Inbound extends FlowControlNoop implements FlowControl.Inbound {

        private final WindowSize.Inbound connectionWindowSize;
        private final WindowSize.Inbound streamWindowSize;

        Inbound(int streamId,
                WindowSize.Inbound connectionWindowSize,
                BiConsumer<Integer, Http2WindowUpdate> windowUpdateStreamWriter) {
            this.connectionWindowSize = connectionWindowSize;
            this.streamWindowSize = WindowSize.createInboundNoop(streamId, windowUpdateStreamWriter);
        }

        @Override
        public void incrementWindowSize(int increment) {
            streamWindowSize.incrementWindowSize(increment);
            connectionWindowSize.incrementWindowSize(increment);
        }

    }

    static class Outbound extends FlowControlNoop implements FlowControl.Outbound {

        @Override
        public long incrementStreamWindowSize(int increment) {
            return WindowSize.MAX_WIN_SIZE;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            return new Http2FrameData[] {frame};
        }

        @Override
        public void blockTillUpdate() {
        }

        @Override
        public int maxFrameSize() {
            return WindowSize.DEFAULT_MAX_FRAME_SIZE;
        }
    }

}
