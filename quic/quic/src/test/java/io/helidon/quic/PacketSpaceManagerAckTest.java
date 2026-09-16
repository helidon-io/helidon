/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

import io.helidon.quic.QuicTLSEngine.HandshakeState;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.frame.AckFrame.AckFrameBuilder;
import io.helidon.quic.frame.AckFrame.AckRange;
import io.helidon.quic.frame.PingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacketSpaceManagerAckTest {

    @Test
    void doesNotArmHandshakePtoBeforeKeysAreAvailable() {
        QuicTLSEngine tlsEngine = mock(QuicTLSEngine.class);
        TestContext context = TestContext.create(true,
                                                 QuicTransportParametersConfigSupport.DEFAULT_ACK_DELAY_EXPONENT,
                                                 QuicTransportParametersConfigSupport.DEFAULT_MAX_ACK_DELAY,
                                                 PacketNumberSpace.HANDSHAKE,
                                                 tlsEngine);

        context.manager.runTransmitter();

        assertThat(context.emitter.nextScheduledDeadline(), is(Deadline.MAX));
        context.timeLine.advance(Duration.ofSeconds(2));
        context.manager.runTransmitter();
        assertThat(context.emitter.pingAttempts, is(0));
        assertThat(context.rttEstimator.ptoBackoff(), is(1L));

        when(tlsEngine.keysAvailable(KeySpace.HANDSHAKE)).thenReturn(true);
        context.manager.runTransmitter();

        assertThat(context.emitter.nextScheduledDeadline(),
                   is(context.timeLine.instant().plus(context.manager.ptoDuration())));
        assertThat(context.emitter.pingAttempts, is(0));
        context.timeLine.advance(context.manager.ptoDuration());
        context.emitter.fireTimer();
        assertThat(context.emitter.pingAttempts, is(1));
        assertThat(context.rttEstimator.ptoBackoff(), is(2L));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 128, 1024})
    void processesSparseAckRangesOncePerPacket(int rangeCount) throws Exception {
        TestContext context = TestContext.create(false);
        Deadline sent = context.timeLine.instant();
        for (int i = 0; i < 2 * rangeCount; i++) {
            context.send();
        }
        List<AckRange> ranges = new ArrayList<>();
        for (int i = 0; i < rangeCount; i++) {
            ranges.add(AckRange.of(0, 0));
        }
        AckFrame oddPackets = AckFrame.create(2L * rangeCount - 1, 0, ranges);
        List<Long> expectedOddPackets = LongStream.range(0, rangeCount).map(number -> 2 * number + 1).boxed().toList();
        context.timeLine.advance(Duration.ofMillis(10));

        context.manager.processAckFrame(oddPackets);

        assertThat(context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                   is(expectedOddPackets));
        verify(context.congestionController, times(rangeCount)).packetAcked(1200, sent);
        assertThat(context.rttEstimator.state().rttSampleCount(), is(1L));

        context.manager.processAckFrame(oddPackets);

        assertThat(context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                   is(expectedOddPackets));
        verify(context.congestionController, times(rangeCount)).packetAcked(1200, sent);
        assertThat(context.rttEstimator.state().rttSampleCount(), is(1L));

        context.manager.processAckFrame(AckFrame.create(2L * rangeCount - 2, 0, ranges));

        assertThat(context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                   is(LongStream.range(0, 2L * rangeCount).boxed().toList()));
    }

    @ParameterizedTest
    @CsvSource({
            "0, false",
            "0, true",
            "2147483648, false",
            "2147483648, true",
            "4611686018427387856, false",
            "4611686018427387856, true"
    })
    void acknowledgesOnlyPacketsWithinFragmentedRangeBounds(long firstPacketNumber, boolean reverseOrder) throws Exception {
        TestContext context = TestContext.create(false);
        List<Long> packetNumbers = LongStream.range(firstPacketNumber, firstPacketNumber + 35).boxed().toList();
        for (long packetNumber : reverseOrder ? packetNumbers.reversed() : packetNumbers) {
            context.send(packetNumber);
        }
        AckFrame frame = AckFrame.create(firstPacketNumber + 31,
                                         0,
                                         List.of(AckRange.of(0, 3),
                                                 AckRange.of(2, 0),
                                                 AckRange.of(0, 2),
                                                 AckRange.of(4, 4),
                                                 AckRange.of(0, 1)));
        List<Long> expected = List.of(7L, 8L, 10L, 11L, 12L, 13L, 14L, 20L, 21L, 22L, 24L, 28L, 29L, 30L, 31L)
                .stream()
                .map(offset -> firstPacketNumber + offset)
                .toList();

        context.manager.processAckFrame(frame);

        assertThat(context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(), is(expected));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reusesAcknowledgementRangesAcrossFrames(boolean reverseOrder) throws Exception {
        TestContext context = TestContext.create(false);
        List<Long> packetNumbers = LongStream.range(0, 36).boxed().toList();
        for (long packetNumber : reverseOrder ? packetNumbers.reversed() : packetNumbers) {
            context.send(packetNumber);
        }
        List<List<Long>> acknowledgements = List.of(List.of(30L, 32L),
                                                    LongStream.range(0, 17).map(number -> 2 * number).boxed().toList(),
                                                    List.of(3L, 7L, 11L),
                                                    List.of(17L, 18L, 19L),
                                                    List.of(1L, 5L, 9L, 13L, 21L),
                                                    packetNumbers);
        SortedSet<Long> expected = new TreeSet<>();
        for (List<Long> acknowledged : acknowledgements) {
            AckFrame frame = acknowledging(acknowledged);
            expected.addAll(acknowledged);

            context.manager.processAckFrame(frame);

            assertThat("Acknowledged packets after " + acknowledged,
                       context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                       is(List.copyOf(expected)));

            context.manager.processAckFrame(frame);

            assertThat("Duplicate acknowledgement of " + acknowledged,
                       context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                       is(List.copyOf(expected)));
        }
    }

    @ParameterizedTest
    @CsvSource({"2, 6500000", "3, 4500000"})
    void matchesFragmentedAcknowledgementsAcrossRetransmissionHistory(long largestAcknowledged,
                                                                     long expectedRttMicros) throws Exception {
        TestContext context = TestContext.create(true);
        context.send();
        context.send();
        context.manager.processAckFrame(AckFrame.create(1, 0, List.of(AckRange.of(0, 1))));
        context.send();
        context.timeLine.advance(Duration.ofSeconds(2));
        context.emitter.fireTimer();
        context.timeLine.advance(Duration.ofSeconds(4));
        context.emitter.fireTimer();
        Deadline retransmissionSent = context.timeLine.instant();
        assertThat(context.emitter.retransmissionAttempts, is(2));
        context.timeLine.advance(Duration.ofMillis(500));
        AckFrame frame = AckFrame.create(largestAcknowledged,
                                         0,
                                         List.of(AckRange.of(0, largestAcknowledged - 2), AckRange.of(0, 0)));

        context.manager.processAckFrame(frame);

        assertThat(context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                   is(List.of(0L, 1L, 4L)));
        assertThat(context.rttEstimator.state().rttSampleCount(), is(2L));
        assertThat(context.rttEstimator.state().latestRttMicros(), is(expectedRttMicros));
        assertThat(context.rttEstimator.ptoBackoff(), is(1L));
        verify(context.congestionController).packetAcked(1200, retransmissionSent);

        context.manager.processAckFrame(frame);

        assertThat(context.emitter.acknowledged.stream().map(QuicPacket::packetNumber).sorted().toList(),
                   is(List.of(0L, 1L, 4L)));
        assertThat(context.rttEstimator.state().rttSampleCount(), is(2L));
        verify(context.congestionController).packetAcked(1200, retransmissionSent);
    }

    @Test
    void samplesRetransmittedPacketFromOriginalSendTime() throws Exception {
        TestContext context = TestContext.create(true);
        long originalPacketNumber = context.send();
        context.timeLine.advance(Duration.ofSeconds(2));
        context.emitter.fireTimer();
        assertThat(context.rttEstimator.ptoBackoff(), is(2L));

        context.timeLine.advance(Duration.ofMillis(500));
        context.manager.processAckFrame(acknowledging(originalPacketNumber));

        QuicRttEstimator.QuicRttEstimatorState state = context.rttEstimator.state();
        assertThat(state.rttSampleCount(), is(1L));
        assertThat(state.latestRttMicros(), is(2_500_000L));
        assertThat(context.rttEstimator.ptoBackoff(), is(1L));
    }

    @Test
    void ignoresPreviousPathRetransmissionForCurrentPathRtt() throws Exception {
        TestContext context = TestContext.create(true);
        long previousPathPacketNumber = context.send();
        context.recoveryState.transition(1, context.rttEstimator::resetForPath);
        context.timeLine.advance(Duration.ofSeconds(2));
        context.emitter.fireTimer();
        assertThat(context.rttEstimator.ptoBackoff(), is(2L));

        context.timeLine.advance(Duration.ofMillis(500));
        context.manager.processAckFrame(acknowledging(previousPathPacketNumber));

        assertThat(context.rttEstimator.state().rttSampleCount(), is(0L));
        assertThat(context.rttEstimator.ptoBackoff(), is(2L));

        long currentPathPacketNumber = context.send();
        context.timeLine.advance(Duration.ofMillis(100));
        context.manager.processAckFrame(acknowledging(currentPathPacketNumber));

        QuicRttEstimator.QuicRttEstimatorState state = context.rttEstimator.state();
        assertThat(state.rttSampleCount(), is(1L));
        assertThat(state.latestRttMicros(), is(100_000L));
        assertThat(context.rttEstimator.ptoBackoff(), is(1L));
    }

    @Test
    void doesNotRequeuePtoProbeAcknowledgedDuringBlockedEmission() {
        TestContext context = TestContext.create(true);
        long packetNumber = context.send();
        context.emitter.failRetransmission = true;
        context.emitter.acknowledgeOnFailedRetransmission = packetNumber;
        context.timeLine.advance(Duration.ofSeconds(2));

        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(1));
        assertThat(context.emitter.acknowledged.size(), is(1));

        context.timeLine.advance(Duration.ofSeconds(4));
        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(1));
    }

    @Test
    void doesNotRequeuePtoProbeAfterCloseDuringBlockedEmission() {
        TestContext context = TestContext.create(true);
        context.send();
        context.emitter.failRetransmission = true;
        context.emitter.closeOnFailedRetransmission = true;
        context.timeLine.advance(Duration.ofSeconds(2));

        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(1));
        assertThat(context.manager.isClosed(), is(true));

        context.timeLine.advance(Duration.ofSeconds(4));
        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(1));
    }

    @Test
    void packetSentReschedulesWhilePreviousTransmitterTaskIsPending() throws Exception {
        TestContext context = TestContext.create(false);
        long firstPacketNumber = context.send();
        context.timeLine.advance(Duration.ofSeconds(2));

        Deadline expiredDeadline = context.manager.nextScheduledDeadline();
        assertThat(expiredDeadline.equals(Deadline.MAX), is(false));
        assertThat(expiredDeadline.isAfter(context.timeLine.instant()), is(false));

        // Queue the expired transmitter task without running it, then replace its
        // acknowledged packet with a new packet while the task is still pending.
        context.emitter.fireTimer();
        context.manager.processAckFrame(acknowledging(firstPacketNumber));
        long secondPacketNumber = context.send();

        Deadline nextDeadline = context.manager.nextScheduledDeadline();
        assertThat(nextDeadline.equals(Deadline.MAX), is(false));
        assertThat(nextDeadline.isAfter(context.timeLine.instant()), is(true));

        context.emitter.runDelayedTasks();
        context.manager.processAckFrame(acknowledging(secondPacketNumber));
    }

    @Test
    void retriesFailedLostStreamTail() throws Exception {
        TestContext context = TestContext.create(true);
        byte[] payload = {0x48, 0x54, 0x54, 0x50, 0x2f, 0x33};
        StreamFrame streamTail = StreamFrame.createOwned(0,
                                                        65_530,
                                                        payload.length,
                                                        true,
                                                        ByteBuffer.wrap(payload.clone()));
        long lostPacketNumber = context.send(List.of(streamTail));
        long acknowledgedPacketNumber = context.send();

        assertThat(lostPacketNumber, is(0L));
        assertThat(acknowledgedPacketNumber, is(1L));

        // ACK only packet 1. Packet 0 is below the largest acknowledged packet
        // but outside its ACK range, so the loss timer drives retransmission.
        context.manager.processAckFrame(AckFrame.create(acknowledgedPacketNumber,
                                                        0,
                                                        List.of(AckRange.of(0, 0))));
        assertThat(context.emitter.acknowledged.size(), is(1));

        Deadline lossDeadline = context.emitter.nextScheduledDeadline();
        assertThat(lossDeadline.equals(Deadline.MAX), is(false));
        assertThat(lossDeadline.isAfter(context.timeLine.instant()), is(true));
        context.emitter.failedRetransmissionsRemaining = 2;

        context.timeLine.advance(Deadline.between(context.timeLine.instant(), lossDeadline).plusMillis(1));
        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(1));
        assertThat(context.emitter.retransmitted.size(), is(1));
        Deadline firstRetryDeadline = context.emitter.nextScheduledDeadline();
        assertThat(firstRetryDeadline.equals(Deadline.MAX), is(false));
        assertThat(firstRetryDeadline.isAfter(context.timeLine.instant()), is(true));
        Duration firstRetryDelay = Deadline.between(context.timeLine.instant(), firstRetryDeadline);

        // An unrelated transmitter wakeup must not bypass the local retry delay.
        context.manager.runTransmitter();
        assertThat(context.emitter.retransmissionAttempts, is(1));
        assertThat(context.emitter.nextScheduledDeadline(), is(firstRetryDeadline));

        context.timeLine.advance(firstRetryDelay.plusMillis(1));
        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(2));
        assertThat(context.emitter.retransmitted.size(), is(2));
        Deadline secondRetryDeadline = context.emitter.nextScheduledDeadline();
        assertThat(secondRetryDeadline.equals(Deadline.MAX), is(false));
        assertThat(secondRetryDeadline.isAfter(context.timeLine.instant()), is(true));
        Duration secondRetryDelay = Deadline.between(context.timeLine.instant(), secondRetryDeadline);
        assertThat(secondRetryDelay, is(firstRetryDelay.multipliedBy(2)));

        context.timeLine.advance(secondRetryDelay.plusMillis(1));
        context.emitter.fireTimer();

        assertThat(context.emitter.retransmissionAttempts, is(3));
        assertThat(context.emitter.retransmitted.size(), is(3));
        assertThat(context.emitter.retransmitted.get(1), sameInstance(context.emitter.retransmitted.get(0)));
        assertThat(context.emitter.retransmitted.get(2), sameInstance(context.emitter.retransmitted.get(1)));
        StreamFrame retransmittedTail = (StreamFrame) context.emitter.retransmitted.get(2).frames().get(0);
        assertThat(retransmittedTail.streamId(), is(0L));
        assertThat(retransmittedTail.offset(), is(65_530L));
        assertThat(retransmittedTail.isLast(), is(true));
        ByteBuffer retransmittedPayload = retransmittedTail.payload();
        byte[] actualPayload = new byte[retransmittedPayload.remaining()];
        retransmittedPayload.get(actualPayload);
        assertThat(actualPayload, is(payload));
    }

    @Test
    void resetsFailedRetransmissionBackoffAfterAcknowledgement() throws Exception {
        TestContext context = TestContext.create(true);
        long firstLostPacket = context.send();
        long firstAcknowledgedPacket = context.send();
        context.manager.processAckFrame(AckFrame.create(firstAcknowledgedPacket,
                                                        0,
                                                        List.of(AckRange.of(0, 0))));
        context.emitter.failedRetransmissionsRemaining = 1;

        Deadline firstLossDeadline = context.emitter.nextScheduledDeadline();
        context.timeLine.advance(Deadline.between(context.timeLine.instant(), firstLossDeadline).plusMillis(1));
        context.emitter.fireTimer();

        Deadline firstRetryDeadline = context.emitter.nextScheduledDeadline();
        Duration firstRetryDelay = Deadline.between(context.timeLine.instant(), firstRetryDeadline);
        context.manager.processAckFrame(acknowledging(firstLostPacket));
        assertThat(context.manager.computeNextDeadline(), is(Deadline.MAX));

        long secondLostPacket = context.send();
        long secondAcknowledgedPacket = context.send();
        context.manager.processAckFrame(AckFrame.create(secondAcknowledgedPacket,
                                                        0,
                                                        List.of(AckRange.of(0, 0))));
        context.emitter.failedRetransmissionsRemaining = 1;

        Deadline secondLossDeadline = context.emitter.nextScheduledDeadline();
        context.timeLine.advance(Deadline.between(context.timeLine.instant(), secondLossDeadline).plusMillis(1));
        context.emitter.fireTimer();

        Deadline secondRetryDeadline = context.emitter.nextScheduledDeadline();
        Duration secondRetryDelay = Deadline.between(context.timeLine.instant(), secondRetryDeadline);
        assertThat(secondLostPacket, is(2L));
        assertThat(secondRetryDelay, is(firstRetryDelay));
    }

    @Test
    void appliesConfiguredLocalAckPolicy() {
        TestContext context = TestContext.create(false, 4, Duration.ofMillis(30));
        context.manager.packetReceived(PacketType.ONERTT, 0, true);
        context.timeLine.advance(Duration.ofNanos(160_000));

        AckFrame frame = context.manager.nextAckFrame(false).orElseThrow();

        assertThat(frame.ackDelay(), is(10L));
        assertThat(context.manager.maxAckDelay(), is(14L));
    }

    @Test
    void extendsOrderedAckAndMakesSecondElicitingPacketImmediatelyDue() {
        TestContext context = TestContext.create(false);
        context.manager.packetReceived(PacketType.ONERTT, 20, true);
        assertThat(context.manager.nextAckFrame(true).isEmpty(), is(true));

        context.manager.packetReceived(PacketType.ONERTT, 21, true);

        AckFrame frame = context.manager.nextAckFrame(true).orElseThrow();
        assertSingleRange(frame, 20, 21);
    }

    @Test
    void publishesFreshDelayedSnapshotWithoutMutatingSentSnapshot() {
        TestContext context = TestContext.create(false);
        context.manager.packetReceived(PacketType.ONERTT, 30, true);
        AckFrame first = context.manager.nextAckFrame(false).orElseThrow();
        assertSingleRange(first, 30, 30);
        assertThat(context.manager.nextAckFrame(false).isEmpty(), is(true));

        context.manager.packetReceived(PacketType.ONERTT, 31, true);

        assertThat(context.manager.nextAckFrame(true).isEmpty(), is(true));
        AckFrame second = context.manager.nextAckFrame(false).orElseThrow();
        assertThat(second, not(sameInstance(first)));
        assertSingleRange(first, 30, 30);
        assertSingleRange(second, 30, 31);
    }

    @Test
    void duplicateDoesNotPublishFreshAckSnapshot() {
        TestContext context = TestContext.create(false);
        context.manager.packetReceived(PacketType.ONERTT, 40, true);
        AckFrame sent = context.manager.nextAckFrame(false).orElseThrow();

        context.manager.packetReceived(PacketType.ONERTT, 40, true);

        assertThat(context.manager.nextAckFrame(false).isEmpty(), is(true));
        assertSingleRange(sent, 40, 40);
    }

    @Test
    void usesGenericAckCompositionForGapsAndReordering() {
        TestContext gapContext = TestContext.create(false);
        gapContext.manager.packetReceived(PacketType.ONERTT, 10, true);
        gapContext.manager.packetReceived(PacketType.ONERTT, 12, true);

        AckFrame gap = gapContext.manager.nextAckFrame(true).orElseThrow();
        assertThat(gap.ackRanges().size(), is(2));
        assertThat(gap.isAcknowledging(10), is(true));
        assertThat(gap.isAcknowledging(11), is(false));
        assertThat(gap.isAcknowledging(12), is(true));

        TestContext reorderedContext = TestContext.create(false);
        reorderedContext.manager.packetReceived(PacketType.ONERTT, 12, true);
        reorderedContext.manager.packetReceived(PacketType.ONERTT, 11, true);

        AckFrame reordered = reorderedContext.manager.nextAckFrame(true).orElseThrow();
        assertSingleRange(reordered, 11, 12);
    }

    @Test
    void prunesAckOfAckTouchingSmallestAcknowledgedPacket() throws Exception {
        TestContext context = TestContext.create(false);
        context.manager.packetReceived(PacketType.ONERTT, 60, true);
        AckFrame sentSnapshot = context.manager.nextAckFrame(false).orElseThrow();
        context.manager.packetReceived(PacketType.ONERTT, 61, true);

        long containingPacket = context.send(List.of(sentSnapshot));
        context.manager.processAckFrame(acknowledging(containingPacket));

        AckFrame pruned = context.manager.nextAckFrame(false).orElseThrow();
        assertSingleRange(sentSnapshot, 60, 60);
        assertSingleRange(pruned, 61, 61);

        context.manager.packetReceived(PacketType.ONERTT, 62, true);
        assertThat(context.manager.nextAckFrame(true).isEmpty(), is(true));
        assertSingleRange(context.manager.nextAckFrame(false).orElseThrow(), 61, 62);
    }

    @Test
    void fragmentedAckDoesNotPruneSnapshotCarriedByPacketInGap() throws Exception {
        TestContext context = TestContext.create(false);
        context.manager.packetReceived(PacketType.ONERTT, 60, true);
        AckFrame firstSnapshot = context.manager.nextAckFrame(false).orElseThrow();
        context.send(List.of(firstSnapshot));
        context.manager.packetReceived(PacketType.ONERTT, 61, true);
        AckFrame secondSnapshot = context.manager.nextAckFrame(false).orElseThrow();
        long packetInGap = context.send(List.of(secondSnapshot));
        long largestAcknowledged = context.send();
        context.manager.packetReceived(PacketType.ONERTT, 62, true);

        context.manager.processAckFrame(AckFrame.create(largestAcknowledged,
                                                        0,
                                                        List.of(AckRange.of(0, 0), AckRange.of(0, 0))));

        assertThat(context.manager.minimumPacketNumberThreshold(), is(60L));
        assertSingleRange(context.manager.nextAckFrame(false).orElseThrow(), 61, 62);
        assertSingleRange(firstSnapshot, 60, 60);
        assertSingleRange(secondSnapshot, 60, 61);

        context.manager.processAckFrame(acknowledging(packetInGap));
        context.manager.packetReceived(PacketType.ONERTT, 63, true);

        assertThat(context.manager.minimumPacketNumberThreshold(), is(61L));
        assertSingleRange(context.manager.nextAckFrame(false).orElseThrow(), 62, 63);
    }

    @Test
    void nonElicitingPacketDoesNotArmAckDeadline() {
        TestContext context = TestContext.create(false);

        context.manager.packetReceived(PacketType.ONERTT, 50, false);

        assertThat(context.manager.nextAckFrame(false).isEmpty(), is(true));
        assertThat(context.emitter.nextScheduledDeadline(), is(Deadline.MAX));

        context.manager.packetReceived(PacketType.ONERTT, 51, true);
        assertThat(context.manager.nextAckFrame(true).isEmpty(), is(true));
        assertSingleRange(context.manager.nextAckFrame(false).orElseThrow(), 50, 51);
    }

    @Test
    void retryResetsRecoveryWithoutResettingPacketNumbers() throws Exception {
        TestContext context = TestContext.create(false);
        context.rttEstimator.consumeRttSample(25_000, 0, context.timeLine.instant());
        context.rttEstimator.increasePtoBackoff();
        context.send();

        context.manager.retry();
        context.manager.processAckFrame(acknowledging(0));

        assertThat(context.manager.nextPacketNumber().get(), is(1L));
        assertThat(context.emitter.acknowledged.size(), is(0));
        assertThat(context.rttEstimator.state().rttSampleCount(), is(0L));
        assertThat(context.rttEstimator.ptoBackoff(), is(1L));
        verify(context.congestionController).resetForPath(1200);
    }

    @Test
    void acceptsAcknowledgementsForPacketsSentAfterSkippedPacketNumber() throws Exception {
        TestContext context = TestContext.create(false);

        SkippedPacket skipped = sendUntilPacketNumberGap(context);

        context.manager.packetSent(packet(skipped.actualPacketNumber()),
                                   -1,
                                   skipped.actualPacketNumber(),
                                   context.recoveryState.generation());
        context.manager.processAckFrame(acknowledging(skipped.actualPacketNumber()));
        assertThat(context.emitter.acknowledged.size(), is(1));
    }

    @Test
    void rejectsAcknowledgementForSkippedPacketNumber() throws Exception {
        TestContext context = TestContext.create(false);

        SkippedPacket skipped = sendUntilPacketNumberGap(context);

        QuicTransportException exception = assertThrows(
                QuicTransportException.class,
                () -> context.manager.processAckFrame(acknowledging(skipped.skippedPacketNumber())));
        assertThat(exception.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
    }

    private static SkippedPacket sendUntilPacketNumberGap(TestContext context) throws QuicTransportException {
        long previousPacketNumber = -1L;
        for (int i = 0; i < 512; i++) {
            long packetNumber = context.manager.allocateNextPN();
            if (previousPacketNumber >= 0 && packetNumber > previousPacketNumber + 1) {
                return new SkippedPacket(previousPacketNumber + 1, packetNumber);
            }
            previousPacketNumber = packetNumber;
        }
        throw new AssertionError("No application packet number was skipped within the configured maximum interval");
    }

    private static AckFrame acknowledging(long packetNumber) {
        return AckFrame.create(packetNumber, 0, List.of(AckRange.of(0, 0)));
    }

    private static AckFrame acknowledging(List<Long> packetNumbers) {
        var builder = AckFrameBuilder.create();
        packetNumbers.forEach(builder::addAck);
        return builder.build();
    }

    private static void assertSingleRange(AckFrame frame, long smallest, long largest) {
        assertThat(frame.largestAcknowledged(), is(largest));
        assertThat(frame.smallestAcknowledged(), is(smallest));
        assertThat(frame.ackRanges(), is(List.of(AckRange.of(0, largest - smallest))));
    }

    private static QuicPacket packet(long packetNumber) {
        return packet(packetNumber, List.of(PingFrame.create()));
    }

    private static QuicPacket packet(long packetNumber, List<QuicFrame> frames) {
        QuicPacket packet = mock(QuicPacket.class);
        when(packet.frames()).thenReturn(frames);
        when(packet.isAckEliciting()).thenReturn(true);
        when(packet.packetNumber()).thenReturn(packetNumber);
        when(packet.packetType()).thenReturn(PacketType.ONERTT);
        when(packet.numberSpace()).thenReturn(PacketNumberSpace.APPLICATION);
        when(packet.size()).thenReturn(1200);
        return packet;
    }

    private record TestContext(PacketSpaceManager manager,
                               PacketSpaceManager.PathRecoveryState recoveryState,
                               QuicRttEstimator rttEstimator,
                               QuicCongestionController congestionController,
                               TestTimeLine timeLine,
                               TestPacketEmitter emitter) {

        static TestContext create(boolean runTimers) {
            return create(runTimers,
                          QuicTransportParametersConfigSupport.DEFAULT_ACK_DELAY_EXPONENT,
                          QuicTransportParametersConfigSupport.DEFAULT_MAX_ACK_DELAY);
        }

        static TestContext create(boolean runTimers, int ackDelayExponent, Duration maxAckDelay) {
            QuicTLSEngine tlsEngine = mock(QuicTLSEngine.class);
            when(tlsEngine.handshakeState()).thenReturn(HandshakeState.HANDSHAKE_CONFIRMED);
            return create(runTimers, ackDelayExponent, maxAckDelay, PacketNumberSpace.APPLICATION, tlsEngine);
        }

        static TestContext create(boolean runTimers,
                                  int ackDelayExponent,
                                  Duration maxAckDelay,
                                  PacketNumberSpace packetNumberSpace,
                                  QuicTLSEngine tlsEngine) {
            PacketSpaceManager.PathRecoveryState recoveryState = new PacketSpaceManager.PathRecoveryState(0);
            QuicRttEstimator rttEstimator = QuicRttEstimator.create(
                    QuicRuntimeConfig.create(QuicConfig.create()).recovery());
            QuicCongestionController congestionController = mock(QuicCongestionController.class);
            when(congestionController.canSendPacket()).thenReturn(true);
            when(congestionController.maxDatagramSize()).thenReturn(1200L);
            TestTimeLine timeLine = new TestTimeLine();
            TestPacketEmitter emitter = new TestPacketEmitter(recoveryState, runTimers);
            PacketSpaceManager manager = new PacketSpaceManager(packetNumberSpace,
                                                                emitter,
                                                                timeLine,
                                                                rttEstimator,
                                                                congestionController,
                                                                tlsEngine,
                                                                () -> "ack-test",
                                                                recoveryState,
                                                                ackDelayExponent,
                                                                maxAckDelay.toMillis(),
                                                                _ -> {
                                                                });
            emitter.manager = manager;
            return new TestContext(manager, recoveryState, rttEstimator, congestionController, timeLine, emitter);
        }

        long send() {
            return send(List.of(PingFrame.create()));
        }

        long send(List<QuicFrame> frames) {
            long packetNumber = manager.nextPacketNumber().getAndIncrement();
            manager.packetSent(packet(packetNumber, frames), -1, packetNumber, recoveryState.generation());
            return packetNumber;
        }

        void send(long packetNumber) {
            manager.nextPacketNumber().accumulateAndGet(packetNumber + 1, Math::max);
            manager.packetSent(packet(packetNumber), -1, packetNumber, recoveryState.generation());
        }
    }

    private record SkippedPacket(long skippedPacketNumber, long actualPacketNumber) {
    }

    private static final class TestTimeLine implements TimeLine {
        private Deadline now = Deadline.of(Instant.EPOCH);

        @Override
        public Deadline instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private static final class TestPacketEmitter implements PacketEmitter {
        private final PacketSpaceManager.PathRecoveryState recoveryState;
        private final Executor executor;
        private final List<QuicPacket> acknowledged = new ArrayList<>();
        private final List<Runnable> delayedTasks = new ArrayList<>();
        private final List<QuicPacket> retransmitted = new ArrayList<>();
        private PacketSpaceManager manager;
        private QuicTimedEvent scheduled;
        private boolean failRetransmission;
        private int failedRetransmissionsRemaining;
        private boolean closeOnFailedRetransmission;
        private long acknowledgeOnFailedRetransmission = -1;
        private int retransmissionAttempts;
        private int pingAttempts;

        private TestPacketEmitter(PacketSpaceManager.PathRecoveryState recoveryState, boolean runTimers) {
            this.recoveryState = recoveryState;
            executor = runTimers ? Runnable::run : delayedTasks::add;
        }

        @Override
        public QuicTimerQueue timer() {
            return null;
        }

        @Override
        public boolean retransmit(PacketSpace packetSpace, QuicPacket packet, int attempts)
                throws QuicTransportException {
            retransmissionAttempts++;
            retransmitted.add(packet);
            boolean fail = failRetransmission || failedRetransmissionsRemaining > 0;
            if (failedRetransmissionsRemaining > 0) {
                failedRetransmissionsRemaining--;
            }
            if (fail) {
                if (acknowledgeOnFailedRetransmission >= 0) {
                    manager.processAckFrame(acknowledging(acknowledgeOnFailedRetransmission));
                }
                if (closeOnFailedRetransmission) {
                    manager.close();
                }
                return false;
            }
            long packetNumber = manager.nextPacketNumber().getAndIncrement();
            manager.packetSent(packet(packetNumber),
                               packet.packetNumber(),
                               packetNumber,
                               recoveryState.generation());
            return true;
        }

        @Override
        public long emitAckPacket(PacketSpace packetSpaceManager, AckFrame ackFrame, boolean sendPing) {
            if (sendPing) {
                pingAttempts++;
            }
            return -1;
        }

        @Override
        public void acknowledged(QuicPacket packet) {
            acknowledged.add(packet);
        }

        @Override
        public boolean sendData(PacketNumberSpace packetNumberSpace) {
            return false;
        }

        @Override
        public Executor executor() {
            return executor;
        }

        @Override
        public void reschedule(QuicTimedEvent event) {
            scheduled = event;
            event.refreshDeadline();
        }

        @Override
        public void reschedule(QuicTimedEvent event, Deadline deadline) {
            scheduled = event;
            event.refreshDeadline();
        }

        @Override
        public void checkAbort(PacketNumberSpace packetNumberSpace) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        void fireTimer() {
            QuicTimedEvent event = scheduled;
            scheduled = null;
            if (event != null) {
                event.handle();
            }
        }

        void runDelayedTasks() {
            List<Runnable> tasks = List.copyOf(delayedTasks);
            delayedTasks.clear();
            tasks.forEach(Runnable::run);
        }

        Deadline nextScheduledDeadline() {
            return scheduled == null ? Deadline.MAX : scheduled.deadline();
        }
    }
}
