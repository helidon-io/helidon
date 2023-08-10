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

import io.helidon.http.HttpPrologue;

/**
 * An exception that may be thrown by {@link WsListener#onHttpUpgrade(HttpPrologue, io.helidon.http.Headers)}
 * during handshake process to reject a websocket upgrade.
 */
public class WsUpgradeException extends Exception {

    /**
     * Create exception from message.
     *
     * @param message the message
     */
    public WsUpgradeException(String message) {
        super(message);
    }

    /**
     * Create exception from message and cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public WsUpgradeException(String message, Throwable cause) {
        super(message, cause);
    }
}
