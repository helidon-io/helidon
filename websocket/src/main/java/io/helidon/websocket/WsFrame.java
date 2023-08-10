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

import io.helidon.common.buffers.BufferData;

/**
 * WebSocket frame. A frame represents single message from WebSocket client or server.
 */
public interface WsFrame {
    /**
     * Is the end of message (or end of continuation).
     *
     * @return {@code true} if this is a full message, or the last message in a continuation
     */
    boolean fin();

    /**
     * Operation code of this frame.
     *
     * @return code of this frame
     */
    WsOpCode opCode();

    /**
     * Whether this frame is masked. Server frames must not be masked, client frames must be masked.
     *
     * @return {@code true} for masked frames
     */
    boolean masked();

    /**
     * Length of the payload bytes.
     *
     * @return payload length
     */
    long payloadLength();

    /**
     * Masking key, if {@link #masked()} returns {@code true}.
     *
     * @return masking key if available
     * @throws java.lang.IllegalStateException if this frame is not masked
     */
    int[] maskingKey();

    /**
     * Always unmasked.
     *
     * @return payload bytes
     */
    BufferData payloadData();

    /**
     * Helper method to check whether this is a payload frame (text or binary),
     * or a control frame (such as ping, pong, close etc.).
     *
     * @return {@code true} for text or binary frames, {@code false} for control frames
     * @see #opCode()
     */
    default boolean isPayload() {
        return opCode() == WsOpCode.TEXT || opCode() == WsOpCode.BINARY;
    }
}
