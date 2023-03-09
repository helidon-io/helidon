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

/**
 * Window size container, used with {@link io.helidon.nima.http2.FlowControl}.
 */
public interface WindowSize {

    /**
     * Default window size.
     */
    int DEFAULT_WIN_SIZE = 65_535;

    /**
     * Maximal window size.
     */
    int MAX_WIN_SIZE = Integer.MAX_VALUE;

    /**
     * Create inbound window size container with initial window size set.
     *
     * @param initialWindowSize  initial window size
     * @param maxFrameSize       maximal frame size
     * @param windowUpdateWriter writer method for HTTP/2 WINDOW_UPDATE frame
     * @return a new window size container
     */
    static WindowSize.Inbound createInbound(int initialWindowSize,
                                            int maxFrameSize,
                                            Consumer<Http2WindowUpdate> windowUpdateWriter) {
        return new WindowSizeImpl.Inbound(initialWindowSize, maxFrameSize, windowUpdateWriter);
    }

    /**
     * Create outbound window size container with default initial size.
     *
     * @return a new window size container
     */
    static WindowSize.Outbound createOutbound() {
        return new WindowSizeImpl.Outbound(WindowSize.DEFAULT_WIN_SIZE);
    }

    /**
     * Create outbound window size container with initial window size set.
     *
     * @param initialWindowSize initial window size
     * @return a new window size container
     */
    static WindowSize.Outbound createOutbound(int initialWindowSize) {
        return new WindowSizeImpl.Outbound(initialWindowSize);
    }

    /**
     * Create inbound window size container with flow control turned off.
     *
     * @param windowUpdateWriter WINDOW_UPDATE frame writer
     * @return a new window size container
     */
    static WindowSize.Inbound createInboundNoop(Consumer<Http2WindowUpdate> windowUpdateWriter) {
        return new WindowSizeImpl.InboundNoop(windowUpdateWriter);
    }

    /**
     * Reset window size.
     *
     * @param size window size
     */
    void resetWindowSize(long size);

    /**
     * Increment window size.
     *
     * @param increment increment
     * @return whether the increment succeeded
     */
    boolean incrementWindowSize(int increment);

    /**
     * Decrement window size.
     *
     * @param decrement decrement
     */
    void decrementWindowSize(int decrement);

    /**
     * Remaining window size.
     *
     * @return remaining sze
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
