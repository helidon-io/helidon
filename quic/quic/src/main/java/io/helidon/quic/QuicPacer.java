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

package io.helidon.quic;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.Api;

/**
 * Implementation of pacing.
 *
 * When the connection is sending at a rate lower than permitted
 * by the congestion controller, pacer is responsible for spreading out
 * the outgoing packets across the entire RTT.
 *
 * Technically the pacer provides two functions:
 * - computes the number of packets that can be sent now
 * - computes the time when another packet can be sent
 *
 * When a new flow starts, or when the flow is not pacer-limited,
 * the pacer limits the window to:
 * max(INITIAL_WINDOW, pacingRate / timerFreq)
 * timerFreq is the best timer resolution we can get from the selector.
 * pacingRate is N * congestionWindow / smoothedRTT
 * where N = 2 when in slow start, N = 1.25 otherwise.
 *
 * After that, the window refills at pacingRate, up to two timer periods or 4 packets,
 * whichever is higher.
 *
 * The time when another packet can be sent is computed
 * as the time when the window will allow at least 2 packets.
 *
 * All methods are externally serialized in congestion controller.
 *
 * Ideas taken from:
 * https://www.rfc-editor.org/rfc/rfc9002.html#name-pacing
 * https://www.ietf.org/archive/id/draft-welzl-iccrg-pacing-03.html
 */
@Api.Internal
public class QuicPacer {
    private static final System.Logger LOGGER = System.getLogger(QuicPacer.class.getName());

    private final QuicRttEstimator rttEstimator;
    private final QuicCongestionController congestionController;
    private final long timerFrequencyHz;
    private final Supplier<String> logTagSupplier;

    private boolean appLimited;
    private long quota;
    private Deadline lastUpdate;

    QuicPacer(QuicRuntimeConfig.Recovery recoveryConfig,
              Supplier<String> logTagSupplier,
              QuicRttEstimator rttEstimator,
              QuicCongestionController congestionController) {
        this.rttEstimator = rttEstimator;
        this.congestionController = congestionController;
        this.timerFrequencyHz = recoveryConfig.timerFrequencyHz();
        this.appLimited = true;
        this.logTagSupplier = logTagSupplier;
    }

    /**
     * called to indicate that the flow is app-limited.
     * Alters the behavior of the following updateQuota call.
     */
    public void appLimited() {
        appLimited = true;
    }

    /**
     * Returns whether the pacer quota has not been hit yet.
     *
     * @return true if the pacer quota has not been hit yet, false otherwise
     */
    public boolean canSend() {
        return quota >= congestionController.maxDatagramSize();
    }

    /**
     * Update quota based on time since the last call to this method
     * and whether appLimited() was called or not.
     *
     * @param now current time
     */
    public void updateQuota(Deadline now) {
        if (lastUpdate != null && !now.isAfter(lastUpdate)) {
            // might happen when transmission tasks from different packet spaces
            // race to update quota. Keep the most recent update only.
            return;
        }
        long rttMicros = rttEstimator.state().smoothedRttMicros();
        long cwnd = congestionController.congestionWindow();
        if (rttMicros * timerFrequencyHz < TimeUnit.SECONDS.toMicros(2)) {
            // RTT less than two timer periods; don't pace
            quota = 2 * cwnd;
            lastUpdate = now;
            return;
        }
        long pacingRate = cwnd * (congestionController.isSlowStart() ? 2_000_000 : 1_250_000) / rttMicros; // bytes per second
        long initialWindow = congestionController.initialWindow();
        long onePeriodWindow = pacingRate / timerFrequencyHz;
        long maxQuota;
        if (appLimited) {
            maxQuota = Math.max(initialWindow, onePeriodWindow);
        } else {
            maxQuota = Math.max(2 * onePeriodWindow, 4 * congestionController.maxDatagramSize());
        }
        if (lastUpdate == null) {
            quota = Math.max(initialWindow, maxQuota);
        } else {
            long nanosSinceUpdate = Deadline.between(lastUpdate, now).toNanos();
            if (nanosSinceUpdate >= TimeUnit.MICROSECONDS.toNanos(rttMicros)) {
                // don't bother computing the increment, it might overflow and will be capped to maxQuota anyway
                quota = maxQuota;
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "pacer cwnd: %s, rtt %s us, duration %s ns, quota: %s",
                        cwnd, rttMicros, nanosSinceUpdate, quota);
                }
            } else {
                long quotaIncrement = pacingRate * nanosSinceUpdate / 1_000_000_000;
                quota += quotaIncrement;
                quota = Math.min(quota, maxQuota);
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "pacer cwnd: %s, rtt %s us, duration %s ns, increment %s, quota %s",
                        cwnd, rttMicros, nanosSinceUpdate, quotaIncrement, quota);
                }
            }
        }
        lastUpdate = now;
        appLimited = false;
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        LOGGER.log(level,
                   () -> "[" + logTagSupplier.get() + "] "
                           + (arguments.length == 0 ? format : format.formatted(arguments)));
    }

    /**
     * Returns the deadline when quota will increase to two packets.
     *
     * @return the deadline when quota will increase to two packets
     */
    public Deadline twoPacketDeadline() {
        long datagramSize = congestionController.maxDatagramSize();
        long quotaNeeded = datagramSize * 2 - quota;
        if (quotaNeeded <= 0) {
            return lastUpdate;
        }
        // Window increases at a rate of rtt / cwnd / N
        long rttMicros = rttEstimator.state().smoothedRttMicros();
        long cwnd = congestionController.congestionWindow();
        return lastUpdate.plus(rttMicros
                                       * (congestionController.isSlowStart() ? 500 : 800) /* 1000/N */
                                       * quotaNeeded / cwnd, ChronoUnit.NANOS);
    }

    /**
     * Called to indicate that a packet was sent.
     *
     * @param packetBytes packet size in bytes
     */
    public void packetSent(int packetBytes) {
        quota -= packetBytes;
    }

    void reset() {
        appLimited = true;
        quota = 0;
        lastUpdate = null;
    }
}
