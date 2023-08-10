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

package io.helidon.websocket;

import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;

/**
 * WebSocket listener.
 */
public interface WsListener {
    /**
     * Received text fragment.
     *
     * @param session WebSocket session
     * @param text    text received
     * @param last    is this last fragment
     */
    default void onMessage(WsSession session, String text, boolean last) {
    }

    /**
     * Received binary fragment.
     *
     * @param session WebSocket session
     * @param buffer  buffer with data
     * @param last    is this last fragment
     */
    default void onMessage(WsSession session, BufferData buffer, boolean last) {
    }

    /**
     * Received ping.
     *
     * @param session WebSocket session
     * @param buffer  buffer with data
     */
    default void onPing(WsSession session, BufferData buffer) {
    }

    /**
     * Received pong.
     *
     * @param session WebSocket session
     * @param buffer  buffer with data
     */
    default void onPong(WsSession session, BufferData buffer) {
    }

    /**
     * Received close.
     *
     * @param session WebSocket session
     * @param status  close status
     * @param reason  reason of close
     */
    default void onClose(WsSession session, int status, String reason) {
    }

    /**
     * Error occurred.
     *
     * @param session WebSocket session
     * @param t       throwable caught
     */
    default void onError(WsSession session, Throwable t) {
    }

    /**
     * Session is open.
     *
     * @param session WebSocket session
     */
    default void onOpen(WsSession session) {
    }

    /**
     * Invoked during handshake process. Can be used to negotiate sub-protocols and/or
     * reject an upgrade by throwing {@link WsUpgradeException}.
     *
     * @param prologue the http handshake request
     * @param headers headers in request
     * @return headers to be included in handshake response
     * @throws WsUpgradeException if handshake is rejected
     */
    default Optional<Headers> onHttpUpgrade(HttpPrologue prologue, Headers headers) throws WsUpgradeException {
        return Optional.empty();
    }
}
