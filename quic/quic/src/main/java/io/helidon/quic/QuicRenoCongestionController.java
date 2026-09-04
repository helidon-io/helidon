/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

/**
 * Implementation of QUIC congestion controller based on RFC 9002.
 * This is a QUIC variant of New Reno algorithm.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * <p>Specification: https://www.rfc-editor.org/info/rfc9002
 *        RFC 9002: QUIC Loss Detection and Congestion Control
 */
final class QuicRenoCongestionController extends QuicBaseCongestionController {
    QuicRenoCongestionController(QuicRuntimeConfig runtimeConfig,
                                 String dbgTag,
                                 QuicRttEstimator rttEstimator,
                                 int initialDatagramSize) {
        super(runtimeConfig, dbgTag, rttEstimator, initialDatagramSize);
    }

    static QuicRenoCongestionController create(QuicRuntimeConfig runtimeConfig,
                                               String dbgTag,
                                               QuicRttEstimator rttEstimator,
                                               int initialDatagramSize) {
        return new QuicRenoCongestionController(runtimeConfig, dbgTag, rttEstimator, initialDatagramSize);
    }

    boolean congestionAvoidanceAcked(int packetBytes, Deadline sentTime) {
        boolean isAppLimited = congestionWindowValue() > maxBytesInFlight() + 2L * maxDatagramSizeValue();
        if (!isAppLimited) {
            increaseCongestionWindow(Math.max((long) maxDatagramSizeValue() * packetBytes / congestionWindowValue(), 1L));
        }
        return isAppLimited;
    }

    void onCongestionEvent(Deadline sentTime) {
        if (inCongestionRecovery(sentTime)) {
            return;
        }
        congestionRecoveryStartTime(timeSource().instant());
        slowStartThreshold(congestionWindowValue() / 2);
        congestionWindow(Math.max(minimumWindow(), slowStartThreshold()));
        maxBytesInFlight(0);
        if (isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "Congestion: ssThresh: %s, in flight: %s, cwnd:%s",
                slowStartThreshold(), bytesInFlight(), congestionWindowValue());
        }
    }
}
