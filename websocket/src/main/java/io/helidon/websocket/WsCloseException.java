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

package io.helidon.websocket;

/**
 * Exception requesting a close of the WebSocket communication.
 */
public class WsCloseException extends RuntimeException {
    private final int code;

    /**
     * Create a new exception.
     *
     * @param message   message to use, will be used as the description of the close code
     * @param closeCode WebSocket close code, see {@link WsCloseCodes}
     */
    public WsCloseException(String message, int closeCode) {
        super(message);
        this.code = closeCode;
    }

    /**
     * Close code that should be used to close the connection.
     *
     * @return close code
     */
    public int closeCode() {
        return code;
    }
}
