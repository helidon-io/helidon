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
     * Create inbound flow control builder for a stream.
     *
     * @return a new inbound flow control builder
     */
    static FlowControl.Inbound.Builder builderInbound() {
        return new FlowControl.Inbound.Builder();
    }

    /**
     * Create outbound flow control for a stream.
     *
     * @param streamId                stream id
     * @param streamInitialWindowSize initial window size for stream
     * @param connectionWindowSize    connection window size
     * @return a new flow control
     */
    static FlowControl.Outbound createOutbound(int streamId,
                                                   int streamInitialWindowSize,
                                                   WindowSize.Outbound connectionWindowSize) {
        return new FlowControlImpl.Outbound(streamId,
                                            streamInitialWindowSize,
                                            connectionWindowSize);
    }

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

        /**
         * Inbound flow control builder.
         */
        class Builder implements io.helidon.common.Builder<Builder, Inbound> {

            private int streamId;
            private int streamWindowSize;
            private int streamMaxFrameSize;
            private WindowSize.Inbound connectionWindowSize;
            private Consumer<Http2WindowUpdate> windowUpdateStreamWriter;
            private boolean noop;

            private Builder() {
                this.streamId = 0;
                this.streamWindowSize = 0;
                this.streamMaxFrameSize = 0;
                this.connectionWindowSize = null;
                this.windowUpdateStreamWriter = null;
                this.noop = false;
            }

            @Override
            public FlowControl.Inbound build() {
                return noop
                        ? new FlowControlNoop.Inbound(connectionWindowSize,
                                                      windowUpdateStreamWriter)
                        : new FlowControlImpl.Inbound(streamId,
                                                      streamWindowSize,
                                                      streamMaxFrameSize,
                                                      connectionWindowSize,
                                                      windowUpdateStreamWriter);
            }

            /**
             * Trigger build of NOOP flow control (flow control turned off).
             * NOOP flow control will be returned regardless of other setting when this method is called.
             *
             * @return this builder
             */
            public Builder noop() {
                noop = true;
                return this;
            }

            /**
             * Set HTTP/2 stream ID.
             *
             * @param streamId HTTP/2 stream ID
             * @return this builder
             */
            public Builder streamId(int streamId) {
                this.streamId = streamId;
                return this;
            }

            /**
             * Set HTTP/2 connection window size.
             *
             * @param windowSize HTTP/2 connection window size
             * @return this builder
             */
            public Builder connectionWindowSize(WindowSize.Inbound windowSize) {
                this.connectionWindowSize = windowSize;
                return this;
            }

            /**
             * Set HTTP/2 stream window size.
             *
             * @param windowSize HTTP/2 stream window size
             * @return this builder
             */
            public Builder streamWindowSize(int windowSize) {
                this.streamWindowSize = windowSize;
                return this;
            }

            /**
             * Set HTTP/2 stream window size.
             *
             * @param maxFrameSize HTTP/2 stream maximum frame size size
             * @return this builder
             */
            public Builder streamMaxFrameSize(int maxFrameSize) {
                this.streamMaxFrameSize = maxFrameSize;
                return this;
            }

            /**
             * Set writer method for current HTTP/2 stream WINDOW_UPDATE frame.
             *
             * @param windowUpdateWriter WINDOW_UPDATE frame writer for current HTTP/2 stream
             * @return this builder
             */
            public Builder windowUpdateStreamWriter(Consumer<Http2WindowUpdate> windowUpdateWriter) {
                this.windowUpdateStreamWriter = windowUpdateWriter;
                return this;
            }

        }

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
        boolean incrementStreamWindowSize(int increment);

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

    }

}
