/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.websocket;

import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;

/**
 * WebSocket session.
 */
public interface WsSession {
    /**
     * Send text fragment.
     *
     * @param text text to send
     * @param last if last fragment
     * @return this instance
     */
    WsSession send(String text, boolean last);

    /**
     * Send binary fragment.
     *
     * @param bufferData buffer with data
     * @param last       if last fragment
     * @return this instance
     */
    WsSession send(BufferData bufferData, boolean last);

    /**
     * Send ping.
     *
     * @param bufferData buffer with data
     * @return this instance
     */
    WsSession ping(BufferData bufferData);

    /**
     * Send pong.
     *
     * @param bufferData buffer with data
     * @return this instance
     */
    WsSession pong(BufferData bufferData);

    /**
     * Close session.
     *
     * @param code   close code, may be one of {@link WsCloseCodes}
     * @param reason reason description
     * @return this instance
     */
    WsSession close(int code, String reason);

    /**
     * Terminate session. Sends a close and closes the connection.
     *
     * @return this instance
     */
    WsSession terminate();

    /**
     * The WebSocket sub-protocol negotiated for this session.
     *
     * @return sub-protocol negotiated, if any
     */
    default Optional<String> subProtocol() {
        return Optional.empty();
    }

    /**
     * The underlying socket context.
     *
     * @return socket context
     */
    SocketContext socketContext();
}
