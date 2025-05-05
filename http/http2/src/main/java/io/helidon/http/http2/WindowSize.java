/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

/**
 * Window size container, used with {@link FlowControl}.
 */
public interface WindowSize {

    /**
     * Default window size.
     */
    int DEFAULT_WIN_SIZE = 65_535;
    /**
     * Default and smallest possible setting for MAX_FRAME_SIZE (2^14).
     */
    int DEFAULT_MAX_FRAME_SIZE = 16384;
    /**
     * Largest possible setting for MAX_FRAME_SIZE (2^24-1).
     */
    int MAX_MAX_FRAME_SIZE = 16_777_215;

    /**
     * Maximal window size.
     */
    int MAX_WIN_SIZE = Integer.MAX_VALUE;

    /**
     * Create inbound window size container with initial window size set.
     *
     * @param type               type of the flow control
     * @param streamId           id of the stream the size is created for
     * @param initialWindowSize  initial window size
     * @param maxFrameSize       maximal frame size
     * @param windowUpdateWriter writer method for HTTP/2 WINDOW_UPDATE frame
     * @return a new window size container
     */
    static WindowSize.Inbound createInbound(ConnectionFlowControl.Type type,
                                            int streamId,
                                            int initialWindowSize,
                                            int maxFrameSize,
                                            BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
        return new WindowSizeImpl.Inbound(type, streamId, initialWindowSize, maxFrameSize, windowUpdateWriter);
    }

    /**
     * Create outbound window size container with initial window size set.
     *
     * @param type                  Server or client
     * @param streamId              stream id
     * @param connectionFlowControl connection flow control
     * @return a new window size container
     */
    static WindowSize.Outbound createOutbound(ConnectionFlowControl.Type type,
                                              int streamId,
                                              ConnectionFlowControl connectionFlowControl) {
        return new WindowSizeImpl.Outbound(type, streamId, connectionFlowControl);
    }

    /**
     * Create inbound window size container with flow control turned off.
     *
     * @param streamId stream id or 0 for connection
     * @param windowUpdateWriter WINDOW_UPDATE frame writer
     * @return a new window size container
     */
    static WindowSize.Inbound createInboundNoop(int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
        return new WindowSizeImpl.InboundNoop(streamId, windowUpdateWriter);
    }

    /**
     * Reset window size.
     *
     * @param size new window size
     */
    void resetWindowSize(int size);

    /**
     * Increment window size.
     *
     * @param increment increment
     * @return whether the increment succeeded
     */
    long incrementWindowSize(int increment);

    /**
     * Decrement window size.
     *
     * @param decrement decrement
     * @return remaining size
     */
    int decrementWindowSize(int decrement);

    /**
     * Remaining window size.
     *
     * @return remaining size
     */
    int getRemainingWindowSize();

    // Does not add anything new but having a separate name makes code more human-readable.
    /**
     * Inbound window size container.
     */
    interface Inbound extends WindowSize {
    }

    /**
     * Outbound window size container.
     */
    interface Outbound extends WindowSize {

        /**
         * Trigger update of window size.
         */
        void triggerUpdate();

        /**
         * Block until window size update.
         *
         */
        void blockTillUpdate();
    }

}
