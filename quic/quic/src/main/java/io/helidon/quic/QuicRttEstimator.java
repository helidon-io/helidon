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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;

/**
 * Estimator for quic connection round trip time.
 * Defined in <a href="https://www.rfc-editor.org/rfc/rfc9002#section-5">
 * RFC 9002 section 5</a>.
 * Takes RTT samples as input (max 1 sample per ACK frame)
 * Produces:
 * - minimum RTT over a period of time (minRtt) for internal use
 * - exponentially weighted moving average (smoothedRtt)
 * - mean deviation / variation in the observed samples (rttVar)
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * <p>Specification: https://www.rfc-editor.org/info/rfc9002
 *        RFC 9002: QUIC Loss Detection and Congestion Control
 */
@Api.Internal
public class QuicRttEstimator {
    // kGranularity, 1ms is recommended by RFC 9002 section 6.1.2
    private static final long GRANULARITY_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    private final long maxPtoBackoff;
    private final Duration maxPtoBackoffTimeout;
    private final Duration minPtoBackoffTimeout;
    private final long initialRttMicros;
    private final ReentrantLock stateLock = new ReentrantLock();
    private Deadline firstSample;
    private long latestRttMicros;
    private long minRttMicros;
    private long smoothedRttMicros;
    private long rttVarMicros;
    private long ptoBackoffFactor = 1;
    private long rttSampleCount = 0;

    QuicRttEstimator(QuicRuntimeConfig.Recovery recoveryConfig) {
        this.maxPtoBackoff = 1L << recoveryConfig.maxPtoBackoffExponent();
        this.maxPtoBackoffTimeout = recoveryConfig.maxPtoBackoffTimeout();
        this.minPtoBackoffTimeout = recoveryConfig.minPtoBackoffTimeout();
        this.initialRttMicros = TimeUnit.MILLISECONDS.toMicros(recoveryConfig.initialRtt().toMillis());
        this.smoothedRttMicros = initialRttMicros;
        this.rttVarMicros = initialRttMicros / 2;
    }

    static QuicRttEstimator create(QuicRuntimeConfig.Recovery recoveryConfig) {
        return new QuicRttEstimator(recoveryConfig);
    }

    /**
     * Returns a snapshot of the current RTT estimator state.
     *
     * @return a snapshot of the current RTT estimator state
     */
    public QuicRttEstimatorState state() {
        stateLock.lock();
        try {
            return QuicRttEstimatorState.create(latestRttMicros,
                                                minRttMicros,
                                                smoothedRttMicros,
                                                rttVarMicros,
                                                rttSampleCount);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Update the estimator with latest RTT sample.
     * Use only samples where:
     * - the largest acknowledged PN is newly acknowledged
     * - at least one of the newly acked packets is ack-eliciting
     *
     * @param latestRttMicros time between when packet was sent
     *                       and ack was received, in microseconds
     * @param ackDelayMicros  ack delay received in ack frame, decoded to microseconds
     * @param now             time at which latestRttMicros was calculated
     */
    public void consumeRttSample(long latestRttMicros, long ackDelayMicros, Deadline now) {
        stateLock.lock();
        try {
            this.rttSampleCount += 1;
            this.latestRttMicros = latestRttMicros;
            if (firstSample == null) {
                firstSample = now;
                minRttMicros = latestRttMicros;
                smoothedRttMicros = latestRttMicros;
                rttVarMicros = latestRttMicros / 2;
            } else {
                minRttMicros = Math.min(minRttMicros, latestRttMicros);
                long adjustedRtt;
                if (latestRttMicros >= minRttMicros + ackDelayMicros) {
                    adjustedRtt = latestRttMicros - ackDelayMicros;
                } else {
                    adjustedRtt = latestRttMicros;
                }
                rttVarMicros = (3 * rttVarMicros + Math.abs(smoothedRttMicros - adjustedRtt)) / 4;
                smoothedRttMicros = (7 * smoothedRttMicros + adjustedRtt) / 8;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the time threshold for time-based loss detection.
     * See <a href="https://www.rfc-editor.org/rfc/rfc9002#section-6.2.1">
     * RFC 9002 section 6.1.2</a>
     *
     * @return time threshold for time-based loss detection
     */
    public Duration lossThreshold() {
        stateLock.lock();
        try {
            // max(kTimeThreshold * max(smoothed_rtt, latest_rtt), kGranularity)
            long maxRttMicros = Math.max(smoothedRttMicros, latestRttMicros);
            long lossThresholdMicros = Math.max(9 * maxRttMicros / 8, GRANULARITY_MICROS);
            return Duration.of(lossThresholdMicros, ChronoUnit.MICROS);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the amount of time to wait for acknowledgement of a sent packet,
     * excluding max ack delay.
     * See <a href="https://www.rfc-editor.org/rfc/rfc9002#section-6.2.1">
     * RFC 9002 section 6.1.2</a>
     *
     * @return the amount of time to wait for acknowledgement of a sent packet,
     *         excluding max ack delay
     */
    public Duration basePtoDuration() {
        stateLock.lock();
        try {
            // PTO = smoothed_rtt + max(4*rttvar, kGranularity) + max_ack_delay
            // max_ack_delay is applied by the caller
            long basePtoMicros = smoothedRttMicros
                    + Math.max(4 * rttVarMicros, GRANULARITY_MICROS);
            return Duration.of(basePtoMicros, ChronoUnit.MICROS);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns whether the current PTO duration multiplied by the backoff exceeds
     * the configured minimum PTO backoff timeout.
     *
     * @return whether the current PTO duration multiplied by the backoff exceeds
     *         the configured minimum PTO backoff timeout
     */
    public boolean isMinBackoffTimeoutExceeded() {
        stateLock.lock();
        try {
            return minPtoBackoffTimeout.compareTo(basePtoDuration().multipliedBy(ptoBackoffFactor)) < 0;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the current PTO backoff factor.
     *
     * @return the current PTO backoff factor
     */
    public long ptoBackoff() {
        stateLock.lock();
        try {
            return ptoBackoffFactor;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Increase the PTO backoff factor.
     *
     * @return updated backoff factor
     */
    public long increasePtoBackoff() {
        stateLock.lock();
        try {
            // limit to make sure we don't accidentally overflow
            if (ptoBackoffFactor <= maxPtoBackoff || !isMinBackoffTimeoutExceeded()) {
                ptoBackoffFactor *= 2;
            }
            return ptoBackoffFactor;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Reset the PTO backoff factor to its initial value.
     */
    public void resetPtoBackoff() {
        stateLock.lock();
        try {
            ptoBackoffFactor = 1;
        } finally {
            stateLock.unlock();
        }
    }

    void resetForPath() {
        stateLock.lock();
        try {
            firstSample = null;
            latestRttMicros = 0;
            minRttMicros = 0;
            smoothedRttMicros = initialRttMicros;
            rttVarMicros = initialRttMicros / 2;
            ptoBackoffFactor = 1;
            rttSampleCount = 0;
        } finally {
            stateLock.unlock();
        }
    }

    Duration maxPtoBackoffTimeout() {
        return maxPtoBackoffTimeout;
    }

    Duration minPtoBackoffTimeout() {
        return minPtoBackoffTimeout;
    }

    long maxPtoBackoff() {
        return maxPtoBackoff;
    }

    /**
     * Snapshot of the current RTT estimator state.
     */
    public static final class QuicRttEstimatorState {
        private final long latestRttMicros;
        private final long minRttMicros;
        private final long smoothedRttMicros;
        private final long rttVarMicros;
        private final long rttSampleCount;

        private QuicRttEstimatorState(long latestRttMicros,
                                      long minRttMicros,
                                      long smoothedRttMicros,
                                      long rttVarMicros,
                                      long rttSampleCount) {
            this.latestRttMicros = latestRttMicros;
            this.minRttMicros = minRttMicros;
            this.smoothedRttMicros = smoothedRttMicros;
            this.rttVarMicros = rttVarMicros;
            this.rttSampleCount = rttSampleCount;
        }

        static QuicRttEstimatorState create(long latestRttMicros,
                                            long minRttMicros,
                                            long smoothedRttMicros,
                                            long rttVarMicros,
                                            long rttSampleCount) {
            return new QuicRttEstimatorState(latestRttMicros,
                                             minRttMicros,
                                             smoothedRttMicros,
                                             rttVarMicros,
                                             rttSampleCount);
        }

        /**
         * Returns the most recent RTT sample in microseconds.
         *
         * @return the most recent RTT sample in microseconds
         */
        public long latestRttMicros() {
            return latestRttMicros;
        }

        /**
         * Returns the minimum RTT observed in microseconds.
         *
         * @return the minimum RTT observed in microseconds
         */
        public long minRttMicros() {
            return minRttMicros;
        }

        /**
         * Returns the smoothed RTT estimate in microseconds.
         *
         * @return the smoothed RTT estimate in microseconds
         */
        public long smoothedRttMicros() {
            return smoothedRttMicros;
        }

        /**
         * Returns the RTT variation estimate in microseconds.
         *
         * @return the RTT variation estimate in microseconds
         */
        public long rttVarMicros() {
            return rttVarMicros;
        }

        /**
         * Returns the number of RTT samples consumed by the estimator.
         *
         * @return the number of RTT samples consumed by the estimator
         */
        public long rttSampleCount() {
            return rttSampleCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof QuicRttEstimatorState other)) {
                return false;
            }
            return latestRttMicros == other.latestRttMicros
                    && minRttMicros == other.minRttMicros
                    && smoothedRttMicros == other.smoothedRttMicros
                    && rttVarMicros == other.rttVarMicros
                    && rttSampleCount == other.rttSampleCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(latestRttMicros, minRttMicros, smoothedRttMicros, rttVarMicros, rttSampleCount);
        }

        @Override
        public String toString() {
            return "QuicRttEstimatorState[latestRttMicros="
                    + latestRttMicros
                    + ", minRttMicros="
                    + minRttMicros
                    + ", smoothedRttMicros="
                    + smoothedRttMicros
                    + ", rttVarMicros="
                    + rttVarMicros
                    + ", rttSampleCount="
                    + rttSampleCount
                    + ']';
        }
    }

}
