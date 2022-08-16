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

import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;

/**
 * Frame listener for HTTP/2 connection.
 */
public interface Http2FrameListener {
    /**
     * Create a new composite listener.
     *
     * @param sendFrameListeners list of listener to use
     * @return a new composite listener
     */
    static Http2FrameListener create(List<Http2FrameListener> sendFrameListeners) {
        return Http2FrameListenerUtil.toSingleListener(sendFrameListeners);
    }

    /**
     * Frame header data.
     *
     * @param ctx         context
     * @param frameHeader header data
     */
    default void frameHeader(SocketContext ctx, BufferData frameHeader) {

    }

    /**
     * Frame header.
     *
     * @param ctx    context
     * @param header frame header
     */
    default void frameHeader(SocketContext ctx, Http2FrameHeader header) {
    }

    /**
     * Frame.
     *
     * @param ctx  context
     * @param data frame
     */
    default void frame(SocketContext ctx, Http2DataFrame data) {

    }

    /**
     * Frame data.
     *
     * @param ctx  context
     * @param data frame data
     */
    default void frame(SocketContext ctx, BufferData data) {
    }

    /**
     * Priority frame.
     *
     * @param ctx      context
     * @param priority priority
     */
    default void frame(SocketContext ctx, Http2Priority priority) {

    }

    /**
     * RST stream frame.
     *
     * @param ctx       context
     * @param rstStream rst stream
     */
    default void frame(SocketContext ctx, Http2RstStream rstStream) {
    }

    /**
     * Settings frame.
     *
     * @param ctx      context
     * @param settings settings
     */
    default void frame(SocketContext ctx, Http2Settings settings) {

    }

    /**
     * Ping frame.
     *
     * @param ctx  context
     * @param ping ping
     */
    default void frame(SocketContext ctx, Http2Ping ping) {

    }

    /**
     * Go away frame.
     *
     * @param ctx    context
     * @param goAway go away
     */
    default void frame(SocketContext ctx, Http2GoAway goAway) {

    }

    /**
     * Window update frame.
     *
     * @param ctx          context
     * @param windowUpdate window update
     */
    default void frame(SocketContext ctx, Http2WindowUpdate windowUpdate) {

    }

    /**
     * Headers received.
     *
     * @param ctx     context
     * @param headers headers
     */
    default void headers(SocketContext ctx, Http2Headers headers) {

    }

    /**
     * Continuation frame.
     *
     * @param ctx          context
     * @param continuation continuation
     */
    default void frame(SocketContext ctx, Http2Continuation continuation) {
    }
}
