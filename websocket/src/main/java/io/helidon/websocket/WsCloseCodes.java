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
 * Codes to use with {@link WsSession#close(int, String)} and to receive
 * in {@link WsListener#onClose(WsSession, int, String)}.
 */
public final class WsCloseCodes {
    /**
     * Code to use with {@link WsSession#close(int, String)} to indicate normal close operation.
     */
    public static final int NORMAL_CLOSE = 1000;
    /**
     * Client is leaving (browser tab closing).
     */
    public static final int GOING_AWAY = 1001;
    /**
     * Endpoint received a malformed frame.
     */
    public static final int PROTOCOL_ERROR = 1002;
    /**
     * Endpoint received an unsupported frame (e.g. binary-only endpoint received text frame).
     */
    public static final int CANNOT_ACCEPT = 1003;
    /**
     * Expected close status, received none.
     */
    public static final int NO_STATUS_CODE = 1005;
    /**
     * No close code frame has been received.
     */
    public static final int CLOSED_ABNORMALLY = 1006;
    /**
     * Endpoint received inconsistent message (e.g. malformed UTF-8).
     */
    public static final int NOT_CONSISTENT = 1007;
    /**
     * Generic code used for situations other than 1003 and 1009.
     */
    public static final int VIOLATED_POLICY = 1008;
    /**
     * Endpoint won't process large frame.
     */
    public static final int TOO_BIG = 1009;
    /**
     * Client wanted an extension which server did not negotiate.
     */
    public static final int NO_EXTENSION = 1010;
    /**
     * Internal server error while operating.
     */
    public static final int UNEXPECTED_CONDITION = 1011;
    /**
     * Server/service is restarting.
     */
    public static final int SERVICE_RESTART = 1012;
    /**
     * Temporary server condition forced blocking client's request.
     */
    public static final int TRY_AGAIN_LATER = 1013;
    /**
     * Server acting as gateway received an invalid response.
     */
    public static final int BAD_GATEWAY = 1014;
    /**
     * Transport Layer Security handshake failure.
     */
    public static final int TLS_HANDSHAKE_FAIL = 1015;

    /**
     * Reserved for later min value.
     */
    public static final int RESERVED_FOR_LATER_MIN = 1016;
    /**
     * Reserved for later max value.
     */
    public static final int RESERVED_FOR_LATER_MAX = 1999;

    /**
     * Reserved for extensions min value.
     */
    public static final int RESERVED_FOR_EXTENSIONS_MIN = 2000;
    /**
     * Reserved for extensions max value.
     */
    public static final int RESERVED_FOR_EXTENSIONS_MAX = 2999;

    /**
     * Registered at IANA min value.
     */
    public static final int REGISTERED_AT_IANA_MIN = 3000;
    /**
     * Registered at IANA max value.
     */
    public static final int REGISTERED_AT_IANA_MAX = 3999;

    /**
     * Application min value.
     */
    public static final int APPLICATION_MIN = 4000;
    /**
     * Application max value.
     */
    public static final int APPLICATION_MAX = 4999;

    private WsCloseCodes() {
    }
}
