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

import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.packet.QuicPacket;

/**
 * Implementation of the common parts of a QUIC congestion controller based on RFC 9002.
 *
 * This class implements the common parts of a congestion controller:
 * - slow start
 * - loss recovery
 * - cooperation with pacer
 *
 * Subclasses implement congestion window growth in congestion avoidance phase.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9002
 *        RFC 9002: QUIC Loss Detection and Congestion Control
 */
abstract class QuicBaseCongestionController implements QuicCongestionController {
    private static final System.Logger LOGGER = System.getLogger(QuicBaseCongestionController.class.getName());

    private final TimeLine timeSource;
    private final String dbgTag;
    private final Lock lock = new ReentrantLock();
    private final long maxBytesInFlightLimit;
    private final QuicPacer pacer;
    private long congestionWindow;
    private int maxDatagramSize;
    private int minimumWindow;
    private long bytesInFlight;
    // maximum bytes in flight seen since the last congestion event
    private long maxBytesInFlight;
    private Deadline congestionRecoveryStartTime;
    private long ssThresh = Long.MAX_VALUE;

    QuicBaseCongestionController(QuicRuntimeConfig runtimeConfig,
                                 String dbgTag,
                                 QuicRttEstimator rttEstimator,
                                 int initialDatagramSize) {
        this.dbgTag = dbgTag;
        this.timeSource = TimeSource.source();
        this.maxBytesInFlightLimit = runtimeConfig.userConfig().maxBytesInFlight();
        this.maxDatagramSize = initialDatagramSize;
        this.minimumWindow = 2 * maxDatagramSize;
        this.congestionWindow = initialWindow(maxDatagramSize);
        this.pacer = new QuicPacer(runtimeConfig.recovery(), () -> dbgTag, rttEstimator, this);
    }

    @Override
    public boolean canSendPacket() {
        lock.lock();
        try {
            if (bytesInFlight >= maxBytesInFlightLimit) {
                return false;
            }
            if (isCwndLimited() || isPacerLimited()) {
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateMaxDatagramSize(int newSize) {
        lock.lock();
        try {
            if (minimumWindow != newSize * 2) {
                minimumWindow = newSize * 2;
                maxDatagramSize = newSize;
                congestionWindow = Math.max(congestionWindow, minimumWindow);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public final void resetForPath(int maxDatagramSize) {
        lock.lock();
        try {
            this.maxDatagramSize = maxDatagramSize;
            minimumWindow = 2 * maxDatagramSize;
            congestionWindow = initialWindow(maxDatagramSize);
            bytesInFlight = 0;
            maxBytesInFlight = 0;
            congestionRecoveryStartTime = null;
            ssThresh = Long.MAX_VALUE;
            pacer.reset();
            resetAlgorithmState();
        } finally {
            lock.unlock();
        }
    }

    void resetAlgorithmState() {
    }

    private static long initialWindow(int maxDatagramSize) {
        return Math.min(10L * maxDatagramSize, Math.max(2L * maxDatagramSize, 14720L));
    }

    @Override
    public void packetSent(int packetBytes) {
        lock.lock();
        try {
            bytesInFlight += packetBytes;
            if (bytesInFlight > maxBytesInFlight) {
                maxBytesInFlight = bytesInFlight;
            }
            pacer.packetSent(packetBytes);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetAcked(int packetBytes, Deadline sentTime) {
        lock.lock();
        try {
            long oldWindow = congestionWindow;
            bytesInFlight -= packetBytes;
            // RFC 9002 says we should not increase cwnd when application limited.
            // The concept itself is poorly defined.
            // Here we limit cwnd growth based on the maximum bytes in flight
            // observed since the last congestion event
            if (inCongestionRecovery(sentTime)) {
                if (isLoggable(System.Logger.Level.TRACE)) {
                    log(System.Logger.Level.TRACE, "Acked, in recovery: bytes: %s, in flight: %s",
                              packetBytes, bytesInFlight);
                }
                return;
            }
            boolean isAppLimited;
            if (congestionWindow < ssThresh) {
                isAppLimited = congestionWindow >= 2 * maxBytesInFlight;
                if (!isAppLimited) {
                    congestionWindow += packetBytes;
                }
            } else {
                isAppLimited = congestionAvoidanceAcked(packetBytes, sentTime);
            }
            if (isLoggable(System.Logger.Level.TRACE)) {
                if (isAppLimited) {
                    log(System.Logger.Level.TRACE, "Acked, not blocked: bytes: %s, in flight: %s",
                              packetBytes, bytesInFlight);
                } else {
                    log(System.Logger.Level.TRACE,
                              "Acked, increased: bytes: %s, in flight: %s, new cwnd:%s",
                              packetBytes, bytesInFlight, congestionWindow);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetLost(Collection<QuicPacket> lostPackets, Deadline sentTime, boolean persistent) {
        lock.lock();
        try {
            for (QuicPacket packet : lostPackets) {
                if (inFlight(packet)) {
                    bytesInFlight -= packet.size();
                }
            }
            onCongestionEvent(sentTime);
            if (persistent) {
                congestionWindow = minimumWindow;
                congestionRecoveryStartTime = null;
                if (isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                              "Persistent congestion: ssThresh: %s, in flight: %s, cwnd:%s",
                              ssThresh, bytesInFlight, congestionWindow);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetDiscarded(Collection<QuicPacket> discardedPackets) {
        lock.lock();
        try {
            for (QuicPacket packet : discardedPackets) {
                if (inFlight(packet)) {
                    bytesInFlight -= packet.size();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long congestionWindow() {
        lock.lock();
        try {
            return congestionWindow;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long initialWindow() {
        lock.lock();
        try {
            return initialWindow(maxDatagramSize);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long maxDatagramSize() {
        lock.lock();
        try {
            return maxDatagramSize;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isSlowStart() {
        lock.lock();
        try {
            return congestionWindow < ssThresh;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updatePacer(Deadline now) {
        lock.lock();
        try {
            pacer.updateQuota(now);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isPacerLimited() {
        lock.lock();
        try {
            return !pacer.canSend();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isCwndLimited() {
        lock.lock();
        try {
            return congestionWindow - bytesInFlight < maxDatagramSize;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Deadline pacerDeadline() {
        lock.lock();
        try {
            return pacer.twoPacketDeadline();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void appLimited() {
        lock.lock();
        try {
            pacer.appLimited();
        } finally {
            lock.unlock();
        }
    }

    boolean inCongestionRecovery(Deadline sentTime) {
        return (
                congestionRecoveryStartTime != null
                        && !sentTime.isAfter(congestionRecoveryStartTime));
    }

    /**
     * Returns the lock guarding congestion-control state.
     *
     * @return congestion-control lock
     */
    protected final Lock congestionLock() {
        return lock;
    }

    /**
     * Returns the time source used by this controller.
     *
     * @return time source
     */
    protected final TimeLine timeSource() {
        return timeSource;
    }

    /**
     * Returns whether the supplied level is currently loggable.
     *
     * @param level log level
     * @return {@code true} when the level is loggable
     */
    protected final boolean isLoggable(System.Logger.Level level) {
        return LOGGER.isLoggable(level);
    }

    /**
     * Logs a message for this congestion controller.
     *
     * @param level log level
     * @param format message format
     * @param arguments format arguments
     */
    protected final void log(System.Logger.Level level, String format, Object... arguments) {
        LOGGER.log(level,
                   () -> "[" + dbgTag + "] "
                           + (arguments.length == 0 ? format : format.formatted(arguments)));
    }

    /**
     * Returns the current congestion window.
     *
     * @return congestion window in bytes
     */
    protected final long congestionWindowValue() {
        return congestionWindow;
    }

    /**
     * Updates the current congestion window.
     *
     * @param congestionWindow congestion window in bytes
     */
    protected final void congestionWindow(long congestionWindow) {
        this.congestionWindow = congestionWindow;
    }

    /**
     * Increases the current congestion window.
     *
     * @param delta increment in bytes
     */
    protected final void increaseCongestionWindow(long delta) {
        this.congestionWindow += delta;
    }

    /**
     * Returns the maximum datagram size.
     *
     * @return maximum datagram size in bytes
     */
    protected final int maxDatagramSizeValue() {
        return maxDatagramSize;
    }

    /**
     * Returns the minimum congestion window.
     *
     * @return minimum congestion window in bytes
     */
    protected final int minimumWindow() {
        return minimumWindow;
    }

    /**
     * Returns bytes currently in flight.
     *
     * @return bytes in flight
     */
    protected final long bytesInFlight() {
        return bytesInFlight;
    }

    /**
     * Returns the maximum bytes in flight observed since the last congestion event.
     *
     * @return maximum bytes in flight
     */
    protected final long maxBytesInFlight() {
        return maxBytesInFlight;
    }

    /**
     * Updates the maximum bytes in flight observed since the last congestion event.
     *
     * @param maxBytesInFlight maximum bytes in flight
     */
    protected final void maxBytesInFlight(long maxBytesInFlight) {
        this.maxBytesInFlight = maxBytesInFlight;
    }

    /**
     * Returns the congestion recovery start time.
     *
     * @return congestion recovery start time, or {@code null}
     */
    protected final Deadline congestionRecoveryStartTime() {
        return congestionRecoveryStartTime;
    }

    /**
     * Updates the congestion recovery start time.
     *
     * @param congestionRecoveryStartTime congestion recovery start time
     */
    protected final void congestionRecoveryStartTime(Deadline congestionRecoveryStartTime) {
        this.congestionRecoveryStartTime = congestionRecoveryStartTime;
    }

    /**
     * Returns the slow-start threshold.
     *
     * @return slow-start threshold in bytes
     */
    protected final long slowStartThreshold() {
        return ssThresh;
    }

    /**
     * Updates the slow-start threshold.
     *
     * @param ssThresh slow-start threshold in bytes
     */
    protected final void slowStartThreshold(long ssThresh) {
        this.ssThresh = ssThresh;
    }

    abstract void onCongestionEvent(Deadline sentTime);

    abstract boolean congestionAvoidanceAcked(int packetBytes, Deadline sentTime);

    private static boolean inFlight(QuicPacket packet) {
        // packet is in flight if it contains anything other than a single ACK frame
        // specifically, a packet containing padding is considered to be in flight.
        return packet.frames().size() != 1
                || !(packet.frames().get(0) instanceof AckFrame);
    }
}
