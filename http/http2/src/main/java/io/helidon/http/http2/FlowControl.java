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
 * Flow control used by HTTP/2 for backpressure.
 */
public interface FlowControl {

    /**
     * Decrement window size.
     *
     * @param decrement decrement in bytes
     */
    void decrementWindowSize(int decrement);

    /**
     * Reset stream window size.
     *
     * @param size new window size
     */
    void resetStreamWindowSize(int size);

    /**
     * Remaining window size in bytes.
     *
     * @return remaining size
     */
    int getRemainingWindowSize();

    /**
     * Inbound flow control used by HTTP/2 for backpressure.
     */
    interface Inbound extends FlowControl {

        /**
         * Increment window size.
         *
         * @param increment increment in bytes
         */
        void incrementWindowSize(int increment);
    }

    /**
     * Outbound flow control used by HTTP/2 for backpressure.
     */
    interface Outbound extends FlowControl {

        /**
         * No-op outbound flow control, used for connection related frames.
         */
        Outbound NOOP = new FlowControlNoop.Outbound();

        /**
         * Increment stream window size.
         *
         * @param increment increment in bytes
         * @return {@code true} if succeeded, {@code false} if timed out
         */
        long incrementStreamWindowSize(int increment);

        /**
         * Split frame into frames that can be sent.
         *
         * @param frame frame to split
         * @return result
         */
        Http2FrameData[] cut(Http2FrameData frame);

        /**
         * Block until a window size update happens.
         *
         */
        void blockTillUpdate();

        /**
         * MAX_FRAME_SIZE setting last received from the other side or default.
         * @return MAX_FRAME_SIZE
         */
        int maxFrameSize();
    }

}
