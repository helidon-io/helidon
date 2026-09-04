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

import java.util.concurrent.TimeUnit;

import io.helidon.common.Api;

/**
 * Implementation of the CUBIC congestion controller
 * based on RFC 9438.
 *
 * <p>Specification: https://www.rfc-editor.org/rfc/rfc9438.html
 *        RFC 9438: CUBIC for Fast and Long-Distance Networks
 */
@Api.Internal
public final class QuicCubicCongestionController extends QuicBaseCongestionController {

    /**
     * Multiplicative decrease factor used by CUBIC after congestion.
     */
    public static final double BETA = 0.7;
    /**
     * Reno-friendly additive increase factor derived from {@link #BETA}.
     */
    public static final double ALPHA = 3 * (1 - BETA) / (1 + BETA);
    private static final double C = 0.4;
    private final QuicRttEstimator rttEstimator;
    // Cubic curve inflection point, in bytes
    private long wMaxBytes;
    // cwnd before the most recent congestion event
    private long cwndPriorBytes;
    // "t" from RFC 9438
    private long timeNanos;
    // "K" from RFC 9438
    private long kNanos;
    // estimate for the Reno-friendly congestion window
    private long wEstBytes;
    // the most recent time when the congestion window was filled
    private Deadline lastFullWindow;

    private QuicCubicCongestionController(QuicRuntimeConfig runtimeConfig,
                                          String dbgTag,
                                          QuicRttEstimator rttEstimator,
                                          int initialDatagramSize) {
        super(runtimeConfig, dbgTag, rttEstimator, initialDatagramSize);
        this.rttEstimator = rttEstimator;
    }

    static QuicCubicCongestionController create(QuicRuntimeConfig runtimeConfig,
                                                String dbgTag,
                                                QuicRttEstimator rttEstimator,
                                                int initialDatagramSize) {
        return new QuicCubicCongestionController(runtimeConfig, dbgTag, rttEstimator, initialDatagramSize);
    }

    @Override
    public void packetSent(int packetBytes) {
        congestionLock().lock();
        try {
            super.packetSent(packetBytes);
            if (isCwndLimited()) {
                Deadline now = timeSource().instant();
                if (lastFullWindow == null) {
                    lastFullWindow = now;
                } else {
                    long timePassedNanos = Deadline.between(lastFullWindow, now).toNanos();
                    if (timePassedNanos > 0) {
                        /* "The elapsed time MUST NOT include periods during which cwnd
                           has not been updated due to application-limited behavior"
                           "A flow is application limited if it is currently sending less
                            than what is allowed by the congestion window."

                           We are sending asynchronously; one thread is sending data,
                           a separate thread is processing the acknowledgements.
                           We can't rely on cwnd being fully utilized when we process an ack, because
                           most of the time it won't be.

                           Instead, we assume that if we filled the cwnd, we were not application-limited
                           in the last RTT (which is a pretty good approximation because of pacing),
                           and acknowledgements for all packets sent prior to filling the cwnd
                           count towards cwnd increase.
                         */
                        long rttNanos = TimeUnit.MICROSECONDS.toNanos(rttEstimator.state().smoothedRttMicros());
                        timeNanos += Math.min(timePassedNanos, rttNanos);
                        lastFullWindow = now;
                    }
                }
            }
        } finally {
            congestionLock().unlock();
        }
    }

    boolean congestionAvoidanceAcked(int packetBytes, Deadline sentTime) {
        boolean isAppLimited = sentTime.isAfter(lastFullWindow);
        if (!isAppLimited) {
            if (wEstBytes < cwndPriorBytes) {
                wEstBytes += Math.max((long) (ALPHA * maxDatagramSizeValue() * packetBytes / congestionWindowValue()), 1);
            } else {
                wEstBytes += Math.max((long) maxDatagramSizeValue() * packetBytes / congestionWindowValue(), 1);
            }
            // target = Wcubic(t + RTT)
            long rttNanos = TimeUnit.MICROSECONDS.toNanos(rttEstimator.state().smoothedRttMicros());
            double dblTargetBytes = wCubicBytes(timeNanos + rttNanos);
            long targetBytes = (long) Math.min(dblTargetBytes, 1.5 * congestionWindowValue());
            if (targetBytes > congestionWindowValue()) {
                increaseCongestionWindow(Math.max((targetBytes - congestionWindowValue()) * packetBytes
                                                          / congestionWindowValue(), 1L));
            }
            if (wEstBytes > congestionWindowValue()) {
                congestionWindow(wEstBytes);
            }
        }
        return isAppLimited;
    }

    void onCongestionEvent(Deadline sentTime) {
        if (inCongestionRecovery(sentTime)) {
            return;
        }
        if (congestionWindowValue() < wMaxBytes) {
            // fast convergence
            wMaxBytes = (long) ((1 + BETA) * congestionWindowValue() / 2);
        } else {
            wMaxBytes = congestionWindowValue();
        }
        cwndPriorBytes = congestionWindowValue();
        congestionRecoveryStartTime(timeSource().instant());
        slowStartThreshold((long) (congestionWindowValue() * BETA));
        wEstBytes = Math.max(minimumWindow(), slowStartThreshold());
        congestionWindow(wEstBytes);
        maxBytesInFlight(0);
        timeNanos = 0;
        // set lastFullWindow to prevent rapid timeNanos growth
        lastFullWindow = congestionRecoveryStartTime();
        // ((wmax_segments - cwnd_segments) / C) ^ (1/3) seconds
        kNanos = (long) (Math.cbrt((wMaxBytes - congestionWindowValue()) / C / maxDatagramSizeValue())
                * 1_000_000_000);
        // kNanos may be negative if we reduced the window below minimum,
        // and fast convergence was used. This is acceptable.
        if (isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "Congestion: ssThresh: %s, in flight: %s, cwnd:%s, K: %s ms",
                slowStartThreshold(), bytesInFlight(), congestionWindowValue(),
                TimeUnit.NANOSECONDS.toMillis(kNanos));
        }
    }

    @Override
    void resetAlgorithmState() {
        wMaxBytes = 0;
        cwndPriorBytes = 0;
        timeNanos = 0;
        kNanos = 0;
        wEstBytes = 0;
        lastFullWindow = null;
    }

    // Wcubic(t) = C * (t-K [seconds])^3 + Wmax (segments)
    private double wCubicBytes(long timeNanos) {
        return (C * maxDatagramSizeValue() * Math.pow((timeNanos - kNanos) / 1e9, 3)) + wMaxBytes;
    }
}
