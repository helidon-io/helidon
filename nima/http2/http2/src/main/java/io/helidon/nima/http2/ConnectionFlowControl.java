/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.function.BiConsumer;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * HTTP/2 Flow control for connection.
 */
public class ConnectionFlowControl {

    private static final System.Logger LOGGER_OUTBOUND = System.getLogger(FlowControl.class.getName() + ".ofc");

    private final Type type;
    private final BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter;
    private final WindowSize.Inbound inboundConnectionWindowSize;
    private final WindowSize.Outbound outboundConnectionWindowSize;
    private int maxFrameSize = WindowSize.DEFAULT_MAX_FRAME_SIZE;
    private int initialWindowSize = WindowSize.DEFAULT_WIN_SIZE;

    /**
     * Create connection HTTP/2 flow-control for server side.
     *
     * @param windowUpdateWriter method called for sending WINDOW_UPDATE frames to the client.
     * @return Connection HTTP/2 flow-control
     */
    public static ConnectionFlowControl createServer(BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter){
        return new ConnectionFlowControl(Type.SERVER, windowUpdateWriter);
    }

    /**
     * Create connection HTTP/2 flow-control for client side.
     *
     * @param windowUpdateWriter method called for sending WINDOW_UPDATE frames to the server.
     * @return Connection HTTP/2 flow-control
     */
    public static ConnectionFlowControl createClient(BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter){
        return new ConnectionFlowControl(Type.CLIENT, windowUpdateWriter);
    }

    private ConnectionFlowControl(Type type, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
        this.type = type;
        this.windowUpdateWriter = windowUpdateWriter;
        //FIXME: configurable max frame size?
        this.inboundConnectionWindowSize =
                WindowSize.createInbound(type,
                                         0,
                                         WindowSize.DEFAULT_WIN_SIZE,
                                         WindowSize.DEFAULT_MAX_FRAME_SIZE,
                                         windowUpdateWriter);
        outboundConnectionWindowSize =
                WindowSize.createOutbound(type, 0, this);
    }

    /**
     * Create stream specific inbound and outbound flow control.
     *
     * @param streamId stream id
     * @return stream flow control
     */
    public StreamFlowControl createStreamFlowControl(int streamId) {
        return new StreamFlowControl(type, streamId, this, windowUpdateWriter);
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
        LOGGER_OUTBOUND.log(DEBUG, () -> String.format("%s OFC STR *: Recv INIT_WINDOW_SIZE %s", type, initialWindowSize));
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

    enum Type {
        SERVER, CLIENT;
    }
}
