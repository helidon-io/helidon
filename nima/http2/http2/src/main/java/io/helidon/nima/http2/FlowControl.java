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

/**
 * Flow control used by HTTP/2 for backpressure.
 */
public interface FlowControl {
    /**
     * No-op flow control, used for connection related frames.
     */
    FlowControl NOOP = new FlowControl() {
        @Override
        public void resetStreamWindowSize(long increment) {
        }

        @Override
        public void decrementWindowSize(int decrement) {
        }

        @Override
        public boolean incrementStreamWindowSize(int increment) {
            return false;
        }

        @Override
        public int getRemainingWindowSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Http2FrameData[] split(Http2FrameData frame) {
            return new Http2FrameData[] {frame};
        }

        @Override
        public boolean blockTillUpdate() {
            return false;
        }

        @Override
        public void streamId(int streamId) {
        }
    };

    /**
     * Create a flow control for a stream.
     *
     * @param streamId                stream id
     * @param streamInitialWindowSize initial window size for stream
     * @param connectionWindowSize    connection window size
     * @return a new flow control
     */
    static FlowControl create(int streamId, int streamInitialWindowSize, WindowSize connectionWindowSize) {
        return new FlowControlImpl(streamId, streamInitialWindowSize, connectionWindowSize);
    }

    static FlowControlImpl create(int streamInitialWindowSize, WindowSize connectionWindowSize) {
        return new FlowControlImpl(streamInitialWindowSize, connectionWindowSize);
    }

    /**
     * Reset stream window size.
     *
     * @param increment increment
     */
    void resetStreamWindowSize(long increment);

    /**
     * Decrement window size.
     *
     * @param decrement decrement in bytes
     */
    void decrementWindowSize(int decrement);

    /**
     * Increment stream window size.
     *
     * @param increment increment in bytes
     * @return {@code true} if succeeded, {@code false} if timed out
     */
    boolean incrementStreamWindowSize(int increment);

    /**
     * Remaining window size in bytes.
     *
     * @return remaining size
     */
    int getRemainingWindowSize();

    /**
     * Split frame into frames that can be sent.
     *
     * @param frame frame to split
     * @return result
     */
    Http2FrameData[] split(Http2FrameData frame);

    /**
     * Block until a window size update happens.
     *
     * @return {@code true} if window update happened, {@code false} in case of timeout
     */
    boolean blockTillUpdate();

    void streamId(int streamId);
}
