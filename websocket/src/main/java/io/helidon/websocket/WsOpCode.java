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

/**
 * WebSocket operation code.
 * Each frame has an operation code to easily understand what should be done.
 */
public enum WsOpCode {
    /**
     * Continuation frame.
     */
    CONTINUATION(0),
    /**
     * Payload frame with text payload.
     */
    TEXT(0x1),
    /**
     * Payload frame with binary payload.
     */
    BINARY(0x2),
    /**
     * Close control frame.
     */
    CLOSE(0x8),
    /**
     * Ping control frame.
     */
    PING(0x9),
    /**
     * Pong control frame.
     */
    PONG(0xA);

    private static final WsOpCode[] OP_CODES = new WsOpCode[16];

    static {
        for (WsOpCode value : WsOpCode.values()) {
            OP_CODES[value.code] = value;
        }
    }

    private final int code;

    WsOpCode(int code) {
        this.code = code;
    }

    /**
     * Get operation code based on its numeric code.
     *
     * @param code code
     * @return operation code for the numeric code
     * @throws java.lang.IllegalArgumentException in case the code is not valid
     */
    public static WsOpCode get(int code) {
        WsOpCode opCode = OP_CODES[code];
        if (opCode == null) {
            throw new IllegalArgumentException("Requested code " + code + " is invalid, there is no OpCode for it.");
        }
        return opCode;
    }

    /**
     * Numeric code (used in binary frame representation) of this operation code.
     *
     * @return numeric code
     */
    public int code() {
        return code;
    }
}
