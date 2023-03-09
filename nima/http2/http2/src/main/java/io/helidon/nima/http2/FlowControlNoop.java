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

import java.util.function.Consumer;

class FlowControlNoop implements FlowControl {

    @Override
    public void decrementWindowSize(int decrement) {
    }

    @Override
    public void resetStreamWindowSize(long increment) {
    }

    @Override
    public int getRemainingWindowSize() {
        return Integer.MAX_VALUE;
    }


    // Even NOOP sends WINDOW_UPDATE frames
    static class Inbound extends FlowControlNoop implements FlowControl.Inbound {

        private final WindowSize.Inbound connectionWindowSize;
        private final WindowSize.Inbound streamWindowSize;

        Inbound(WindowSize.Inbound connectionWindowSize, Consumer<Http2WindowUpdate> windowUpdateStreamWriter) {
            this.connectionWindowSize = connectionWindowSize;
            this.streamWindowSize = WindowSize.createInboundNoop(windowUpdateStreamWriter);
        }

        @Override
        public void incrementWindowSize(int increment) {
            streamWindowSize.incrementWindowSize(increment);
            connectionWindowSize.incrementWindowSize(increment);
        }

    }

    static class Outbound extends FlowControlNoop implements FlowControl.Outbound {

        @Override
        public boolean incrementStreamWindowSize(int increment) {
            return false;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            return new Http2FrameData[] {frame};
        }

        @Override
        public void blockTillUpdate() {
        }

    }

}
