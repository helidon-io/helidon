/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Settings;

class Http2ConnectionChecks {
    private static final System.Logger LOGGER = System.getLogger(Http2ConnectionChecks.class.getName());

    // No fancy client settings needed for goaway
    private final Http2Settings clientSettings = Http2Settings.builder().build();
    private final Http2ConnectionWriter writer;
    private final Http2Connection connection;
    private final long rapidResetCheckPeriod;
    private final int maxRapidResets;
    private int rapidResetCnt = 0;
    private long rapidResetPeriodStart = 0;
    private long serverSideResetCounter = 0;

    Http2ConnectionChecks(Http2Config http2Config, Http2ConnectionWriter connectionWriter, Http2Connection connection) {
        this.rapidResetCheckPeriod = http2Config.rapidResetCheckPeriod().toNanos();
        this.maxRapidResets = http2Config.maxRapidResets();
        this.writer = connectionWriter;
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
            if (rapidResetCheckPeriod >= currentTime - rapidResetPeriodStart) {
                rapidResetCnt = 1;
                rapidResetPeriodStart = currentTime;
            } else if (maxRapidResets < rapidResetCnt) {
                closeConnection("Rapid reset attack detected!");
            } else {
                rapidResetCnt++;
            }
        }
    }

    /**
     * Made you reset counter. Not thread safe, expected to run on dispatcher thread.
     *
     * @param lastStreamId the highest stream id seen on the connection so far.
     */
    void madeYouResetCheck(int lastStreamId) {
        if (serverSideResetCounter++ > maxRapidResets
                && serverSideResetCounter > lastStreamId / 4) {
            closeConnection("MadeYouReset attack detected!");
        }
    }

    private void closeConnection(String msg) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, msg + " Closing connection " + connection + " with GOAWAY");
        }
        Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.ENHANCE_YOUR_CALM, msg);
        writer.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
        // Finish to avoid implicit goaway after close
        connection.finish();
        // Close and interrupt
        connection.close(true);
        // Avoid further processing changing the connection state
        throw new Http2Exception(Http2ErrorCode.ENHANCE_YOUR_CALM, msg);
    }
}
