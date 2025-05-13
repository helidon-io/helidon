/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.function.BiConsumer;

import io.helidon.common.Builder;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * HTTP/2 Flow control for connection.
 */
public class ConnectionFlowControl {

    private static final System.Logger LOGGER_OUTBOUND = System.getLogger(FlowControl.class.getName() + ".ofc");

    private final Type type;
    private final BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter;
    private final Duration timeout;
    private final WindowSize.Inbound inboundConnectionWindowSize;
    private final WindowSize.Outbound outboundConnectionWindowSize;

    private volatile int maxFrameSize = WindowSize.DEFAULT_MAX_FRAME_SIZE;
    private volatile int initialWindowSize = WindowSize.DEFAULT_WIN_SIZE;

    private ConnectionFlowControl(Type type,
                                  int initialWindowSize,
                                  int maxFrameSize,
                                  BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter,
                                  Duration timeout) {
        this.type = type;
        this.windowUpdateWriter = windowUpdateWriter;
        this.timeout = timeout;
        this.inboundConnectionWindowSize =
                WindowSize.createInbound(type,
                                         0,
                                         initialWindowSize,
                                         maxFrameSize,
                                         windowUpdateWriter);
        outboundConnectionWindowSize =
                WindowSize.createOutbound(type, 0, this);
    }

    /**
     * Create connection HTTP/2 flow-control for server side.
     *
     * @param windowUpdateWriter method called for sending WINDOW_UPDATE frames to the client.
     * @return Connection HTTP/2 flow-control
     */
    public static ConnectionFlowControlBuilder serverBuilder(BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
        return new ConnectionFlowControlBuilder(Type.SERVER, windowUpdateWriter);
    }

    /**
     * Create connection HTTP/2 flow-control for client side.
     *
     * @param windowUpdateWriter method called for sending WINDOW_UPDATE frames to the server.
     * @return Connection HTTP/2 flow-control
     */
    public static ConnectionFlowControlBuilder clientBuilder(BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
        return new ConnectionFlowControlBuilder(Type.CLIENT, windowUpdateWriter);
    }

    /**
     * Create stream specific inbound and outbound flow control.
     *
     * @param streamId stream id
     * @param outboundInitialWindowSize initial window size for inbound flow control.
     * @param outboundMaxFrameSize max frame size for inbound flow control.
     * @return stream flow control
     */
    public StreamFlowControl createStreamFlowControl(int streamId,
                                                     int outboundInitialWindowSize,
                                                     int outboundMaxFrameSize) {
        return new StreamFlowControl(
                type,
                streamId,
                outboundInitialWindowSize,
                outboundMaxFrameSize,
                this,
                windowUpdateWriter
        );
    }

    /**
     * Increment outbound connection flow control window, called when WINDOW_UPDATE is received.
     *
     * @param increment number of bytes other side has requested on top of actual demand
     * @return outbound window size after increment
     */
    public long incrementOutboundConnectionWindowSize(int increment) {
        return outboundConnectionWindowSize.incrementWindowSize(increment);
    }

    /**
     * Decrement inbound connection flow control window, called when DATA frame is received.
     *
     * @param decrement received DATA frame size in bytes
     * @return inbound window size after decrement
     */
    public long decrementInboundConnectionWindowSize(int decrement) {
        return inboundConnectionWindowSize.decrementWindowSize(decrement);
    }

    /**
     * Reset MAX_FRAME_SIZE for all streams, existing and future ones.
     *
     * @param maxFrameSize to split data frames according to when larger
     */
    public void resetMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Reset an initial window size value for outbound flow control windows of a new streams.
     * Don't forget to call stream.flowControl().outbound().resetStreamWindowSize(...) for each stream
     * to align window size of existing streams.
     *
     * @param initialWindowSize INIT_WINDOW_SIZE received
     */
    public void resetInitialWindowSize(int initialWindowSize) {
        if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
            LOGGER_OUTBOUND.log(DEBUG, String.format("%s OFC STR *: Recv INIT_WINDOW_SIZE %s", type, initialWindowSize));
        }
        this.initialWindowSize = initialWindowSize;
    }

    /**
     * Connection outbound flow control window,
     * decrements when DATA are sent and increments when WINDOW_UPDATE or INIT_WINDOW_SIZE is received.
     * Blocks sending when window is depleted.
     *
     * @return connection outbound flow control window
     */
    public WindowSize.Outbound outbound() {
        return outboundConnectionWindowSize;
    }

    /**
     * Connection inbound window is always manipulated by respective stream flow control,
     * therefore package private is enough.
     *
     * @return connection inbound flow control window
     */
    WindowSize.Inbound inbound() {
        return inboundConnectionWindowSize;
    }

    int maxFrameSize() {
        return maxFrameSize;
    }

    int initialWindowSize() {
        return initialWindowSize;
    }

    Duration timeout() {
        return timeout;
    }

    /**
     * Type of the flow control.
     */
    public enum Type {
        /**
         * Flow control for the server.
         */
        SERVER,
        /**
         * Flow control for the client.
         */
        CLIENT;
    }

    /**
     * Connection flow control builder.
     */
    public static class ConnectionFlowControlBuilder implements Builder<ConnectionFlowControlBuilder, ConnectionFlowControl> {

        private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(100);
        private final Type type;
        private final BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter;
        private int initialWindowSize = WindowSize.DEFAULT_WIN_SIZE;
        private int maxFrameSize = WindowSize.DEFAULT_MAX_FRAME_SIZE;
        private Duration blockTimeout = DEFAULT_TIMEOUT;

        ConnectionFlowControlBuilder(Type type, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
            this.type = type;
            this.windowUpdateWriter = windowUpdateWriter;
        }

        /**
         * Outbound flow control INITIAL_WINDOW_SIZE setting for new HTTP/2 connections.
         *
         * @param initialWindowSize units of octets
         * @return updated builder
         */
        public ConnectionFlowControlBuilder initialWindowSize(int initialWindowSize) {
            this.initialWindowSize = initialWindowSize;
            return this;
        }

        /**
         * Initial MAX_FRAME_SIZE setting for new HTTP/2 connections.
         * Maximum size of data frames in bytes we are prepared to accept from the other size.
         * Default value is 2^14(16_384).
         *
         * @param maxFrameSize data frame size in bytes between 2^14(16_384) and 2^24-1(16_777_215)
         * @return updated client
         */
        public ConnectionFlowControlBuilder maxFrameSize(int maxFrameSize) {
            this.maxFrameSize = maxFrameSize;
            return this;
        }

        /**
         * Timeout for blocking between windows size check iterations.
         *
         * @param timeout duration
         * @return updated builder
         */
        public ConnectionFlowControlBuilder blockTimeout(Duration timeout) {
            this.blockTimeout = timeout;
            return this;
        }

        @Override
        public ConnectionFlowControl build() {
            return new ConnectionFlowControl(type, initialWindowSize, maxFrameSize, windowUpdateWriter, blockTimeout);
        }
    }
}
