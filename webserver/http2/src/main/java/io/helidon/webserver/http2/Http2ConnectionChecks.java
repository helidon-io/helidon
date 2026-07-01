/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.webserver.CloseConnectionException;

class Http2ConnectionChecks {
    private static final System.Logger LOGGER = System.getLogger(Http2ConnectionChecks.class.getName());

    private final Http2Connection connection;
    private final long rapidResetCheckPeriod;
    private final int maxRapidResets;
    private final int maxServerSideResets;
    private int rapidResetCnt = 0;
    private long rapidResetPeriodStart = 0;
    private long serverSideResetCounter = 0;

    Http2ConnectionChecks(Http2Config http2Config, Http2Connection connection) {
        this.rapidResetCheckPeriod = http2Config.rapidResetCheckPeriod().toNanos();
        this.maxRapidResets = http2Config.maxRapidResets();
        this.maxServerSideResets = http2Config.maxRapidResets();
        this.connection = connection;
    }

    /**
     * Rapid reset counter. Not thread safe, expected to run on dispatcher thread.
     *
     * @param rapidReset true if reset is suspected to be part of an attack.
     */
    void rapidResetCheck(boolean rapidReset) {
        if (rapidReset && maxRapidResets != -1) {
            long currentTime = System.nanoTime();
            if (rapidResetPeriodStart == 0 || currentTime - rapidResetPeriodStart > rapidResetCheckPeriod) {
                rapidResetCnt = 0;
                rapidResetPeriodStart = currentTime;
            }
            if (++rapidResetCnt > maxRapidResets) {
                closeConnection("Rapid reset attack detected!");
            }
        }
    }

    /**
     * Made you reset counter. Not thread safe, expected to run on dispatcher thread.
     * Uses the configured reset threshold as a connection lifetime limit for server-side resets.
     */
    void madeYouResetCheck() {
        if (maxServerSideResets != -1 && ++serverSideResetCounter > maxServerSideResets) {
            closeConnection("MadeYouReset attack detected!");
        }
    }

    private void closeConnection(String msg) {
        closeConnection(Http2ErrorCode.ENHANCE_YOUR_CALM, msg);
    }

    void closeConnection(Http2ErrorCode errorCode, String msg) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, msg + " Closing connection " + connection + " with GOAWAY");
        }
        connection.writeGoAwayAndFinish(errorCode, msg);
        // Avoid further processing changing the connection state
        throw new CloseConnectionException("Enhance your calm.");
    }
}
