/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.frame;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import io.helidon.common.Api;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacketDiscardException;

/**
 * An ACK frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class AckFrame extends QuicFrame {

    private static final int COUNTS_PRESENT = 0x1;
    private static final List<AckRange> NO_ACK_RANGES = List.of();

    private final long largestAcknowledged;
    private final long ackDelay;
    private final int ackRangeCount;
    private final List<AckRange> ackRanges;
    private final boolean countsPresent;
    private final long ect0Count;
    private final long ect1Count;
    private final long ecnCECount;
    private final int size;

    /**
     * Reads an {@code AckFrame} from the given buffer. When entering
     * this method the buffer position is supposed to be just after the
     * frame type, which has already been read. This method moves the
     * position of the buffer to the first byte after the read ACK frame.
     *
     * @param buffer a buffer containing the ACK frame
     * @param type   the frame type read from the buffer
     * @param maxAckRangesPerFrame maximum number of packet-number ranges accepted in one ACK frame
     * @throws QuicTransportException if the ACK frame was malformed
     */
    AckFrame(ByteBuffer buffer, int type, int maxAckRangesPerFrame) throws QuicTransportException {
        super(ACK);
        countsPresent = (type & COUNTS_PRESENT) != 0;
        largestAcknowledged = decodeVLField(buffer, "largestAcknowledged");
        ackDelay = decodeVLField(buffer, "ackDelay");
        ackRangeCount = decodeVLFieldAsInt(buffer, "ackRangeCount");
        validateRangeCount(buffer, type, ackRangeCount, countsPresent);
        long firstAckRange = decodeVLField(buffer, "firstAckRange");
        long smallestAcknowledged = validateFirstRange(type, largestAcknowledged, firstAckRange);
        long totalAckRanges = (long) ackRangeCount + 1;
        boolean discard = totalAckRanges > maxAckRangesPerFrame;
        List<AckRange> decodedAckRanges = discard ? NO_ACK_RANGES : new ArrayList<>();
        if (!discard) {
            decodedAckRanges.addFirst(AckRange.of(0, firstAckRange));
        }
        decodeAdditionalRanges(buffer, type, ackRangeCount, smallestAcknowledged, decodedAckRanges, !discard);
        long decodedEct0Count;
        long decodedEct1Count;
        long decodedEcnCECount;
        if (countsPresent) {
            // packet contains ECN counts
            decodedEct0Count = decodeVLField(buffer, "ect0Count");
            decodedEct1Count = decodeVLField(buffer, "ect1Count");
            decodedEcnCECount = decodeVLField(buffer, "ecnCECount");
        } else {
            decodedEct0Count = -1;
            decodedEct1Count = -1;
            decodedEcnCECount = -1;
        }
        if (discard) {
            throw new QuicPacketDiscardException("ACK range count exceeds configured limit: "
                                                         + totalAckRanges + " > " + maxAckRangesPerFrame,
                                                 type);
        }
        ackRanges = List.copyOf(decodedAckRanges);
        ect0Count = decodedEct0Count;
        ect1Count = decodedEct1Count;
        ecnCECount = decodedEcnCECount;
        size = computeSize();
    }

    private AckFrame(long largestAcknowledged, long ackDelay, List<AckRange> ackRanges) {
        this(largestAcknowledged, ackDelay, ackRanges, -1, -1, -1);
    }

    private AckFrame(
            long largestAcknowledged,
            long ackDelay,
            List<AckRange> ackRanges,
            long ect0Count,
            long ect1Count,
            long ecnCECount) {
        super(ACK);
        this.largestAcknowledged = requireVLRange(largestAcknowledged, "largestAcknowledged");
        this.ackDelay = requireVLRange(ackDelay, "ackDelay");
        if (ackRanges.isEmpty()) {
            throw new IllegalArgumentException("insufficient ackRanges");
        }
        if (ackRanges.getFirst().gap() != 0) {
            throw new IllegalArgumentException("first range must have zero gap");
        }
        this.ackRanges = List.copyOf(ackRanges);
        this.ackRangeCount = ackRanges.size() - 1;
        this.countsPresent = ect0Count != -1 || ect1Count != -1 || ecnCECount != -1;
        if (countsPresent) {
            this.ect0Count = requireVLRange(ect0Count, "ect0Count");
            this.ect1Count = requireVLRange(ect1Count, "ect1Count");
            this.ecnCECount = requireVLRange(ecnCECount, "ecnCECount");
        } else {
            this.ect0Count = ect0Count;
            this.ect1Count = ect1Count;
            this.ecnCECount = ecnCECount;
        }
        this.size = computeSize();
    }

    private AckFrame(AckFrame source,
                     long largestAcknowledged,
                     AckRange firstAckRange,
                     int size) {
        super(ACK);
        this.largestAcknowledged = largestAcknowledged;
        this.ackDelay = source.ackDelay;
        this.ackRangeCount = 0;
        this.ackRanges = List.of(firstAckRange);
        this.countsPresent = source.countsPresent;
        this.ect0Count = source.ect0Count;
        this.ect1Count = source.ect1Count;
        this.ecnCECount = source.ecnCECount;
        this.size = size;
    }

    static void validateStructure(ByteBuffer buffer, int type) throws QuicTransportException {
        boolean countsPresent = (type & COUNTS_PRESENT) != 0;
        long largestAcknowledged = decodeVLField(buffer, "largestAcknowledged", type);
        decodeVLField(buffer, "ackDelay", type);
        int ackRangeCount = decodeVLFieldAsInt(buffer, "ackRangeCount", type);
        validateRangeCount(buffer, type, ackRangeCount, countsPresent);
        long firstAckRange = decodeVLField(buffer, "firstAckRange", type);
        long smallestAcknowledged = validateFirstRange(type, largestAcknowledged, firstAckRange);
        decodeAdditionalRanges(buffer, type, ackRangeCount, smallestAcknowledged, NO_ACK_RANGES, false);
        if (countsPresent) {
            decodeVLField(buffer, "ect0Count", type);
            decodeVLField(buffer, "ect1Count", type);
            decodeVLField(buffer, "ecnCECount", type);
        }
    }

    /**
     * Create an ACK frame without ECN counts.
     *
     * @param largestAcknowledged largest acknowledged packet number
     * @param ackDelay            encoded acknowledgement delay
     * @param ackRanges           acknowledged ranges, with the first range using a zero gap
     * @return new ACK frame
     */
    public static AckFrame create(long largestAcknowledged, long ackDelay, List<AckRange> ackRanges) {
        return new AckFrame(largestAcknowledged, ackDelay, ackRanges);
    }

    /**
     * Create an ACK frame with ECN counts.
     *
     * @param largestAcknowledged largest acknowledged packet number
     * @param ackDelay            encoded acknowledgement delay
     * @param ackRanges           acknowledged ranges, with the first range using a zero gap
     * @param ect0Count           ECT(0) count
     * @param ect1Count           ECT(1) count
     * @param ecnCECount          ECN-CE count
     * @return new ACK frame with ECN counts
     */
    public static AckFrame create(
            long largestAcknowledged,
            long ackDelay,
            List<AckRange> ackRanges,
            long ect0Count,
            long ect1Count,
            long ecnCECount) {
        return new AckFrame(largestAcknowledged, ackDelay, ackRanges, ect0Count, ect1Count, ecnCECount);
    }

    /**
     * Returns the largest packet acknowledged by an
     * {@link QuicFrame#ACK ACK} frame contained in the
     * given packet, or {@code -1L} if the packet
     * contains no {@code ACK} frame.
     *
     * @param packet a packet that may contain an {@code ACK} frame
     * @return the largest packet acknowledged by an ACK frame, or {@code -1L}
     */
    public static long largestAcknowledgedInPacket(QuicPacket packet) {
        return packet.frames().stream()
                .filter(AckFrame.class::isInstance)
                .map(AckFrame.class::cast)
                .mapToLong(AckFrame::largestAcknowledged)
                .max().orElse(-1L);
    }

    @Override
    public long typeField() {
        return ACK | (countsPresent ? COUNTS_PRESENT : 0);
    }

    @Override
    public boolean isAckEliciting() {
        return false;
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, typeField(), "type");
        encodeVLField(buffer, largestAcknowledged, "largestAcknowledged");
        encodeVLField(buffer, ackDelay, "ackDelay");
        encodeVLField(buffer, ackRangeCount, "ackRangeCount");
        encodeVLField(buffer, ackRanges.getFirst().range(), "firstAckRange");
        for (int i = 1; i <= ackRangeCount; i++) {
            AckRange ar = ackRanges.get(i);
            encodeVLField(buffer, ar.gap(), "gap");
            encodeVLField(buffer, ar.range(), "range");
        }
        if (countsPresent) {
            // encode the counts
            encodeVLField(buffer, ect0Count, "ect0Count");
            encodeVLField(buffer, ect1Count, "ect1Count");
            encodeVLField(buffer, ecnCECount, "ecnCECount");
        }
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * Returns the largest packet number acknowledged by this frame.
     *
     * @return the largest packet number acknowledged by this frame
     */
    public long largestAcknowledged() {
        return largestAcknowledged;
    }

    /**
     * Returns the encoded acknowledgement delay.
     *
     * @return the encoded acknowledgement delay
     */
    public long ackDelay() {
        return ackDelay;
    }

    /**
     * Returns the number of ack ranges.
     * This corresponds to {@code ackRanges().size() - 1}.
     *
     * @return the number of ack ranges
     */
    public long ackRangeCount() {
        return ackRangeCount;
    }

    /**
     * Returns a new {@code AckFrame} identical to this one, but
     * with the given {@code ackDelay}.
     *
     * @param ackDelay the delay sending the ACK frame
     * @return a new {@code AckFrame} with the given {@code ackDelay}
     */
    public AckFrame withAckDelay(long ackDelay) {
        if (ackDelay == this.ackDelay) {
            return this;
        }
        return new AckFrame(largestAcknowledged, ackDelay, ackRanges,
                            ect0Count, ect1Count, ecnCECount);
    }

    /**
     * Returns a new single-range {@code AckFrame} that also acknowledges the
     * packet immediately following this frame's largest acknowledged packet.
     *
     * @param packetNumber the next packet number to acknowledge
     * @return a new {@code AckFrame} extended through the given packet
     * @throws IllegalArgumentException if this frame has more than one range,
     *                                  or the packet is not the next encodable packet number
     */
    public AckFrame withNextAcknowledged(long packetNumber) {
        requireVLRange(packetNumber, "packetNumber");
        if (ackRangeCount != 0) {
            throw new IllegalArgumentException("single ACK range required");
        }
        if (packetNumber != largestAcknowledged + 1) {
            throw new IllegalArgumentException("packetNumber must immediately follow largestAcknowledged");
        }

        var oldFirstRange = ackRanges.getFirst();
        long newFirstRangeValue = requireVLRange(oldFirstRange.range() + 1, "firstAckRange");
        var newFirstRange = AckRange.of(0, newFirstRangeValue);
        int newSize = size
                - variableLengthFieldLength(largestAcknowledged)
                + variableLengthFieldLength(packetNumber)
                - variableLengthFieldLength(oldFirstRange.range())
                + variableLengthFieldLength(newFirstRangeValue);
        return new AckFrame(this, packetNumber, newFirstRange, newSize);
    }

    /**
     * Returns the ACK ranges. The first element is an actual range relative
     * to highest acknowledged packet number. The second element, if present,
     * is a gap and a range following that gap, and so on until the last.
     *
     * @return the list of {@code AckRange} where the first ACK range
     *         has a gap of {@code 0} and a range corresponding to
     *         the {@code First ACK Range}
     */
    public List<AckRange> ackRanges() {
        return ackRanges;
    }

    /**
     * Returns the ECT0 count from this frame or {@code -1} if not present.
     *
     * @return the ECT0 count from this frame or {@code -1} if not present
     */
    public long ect0Count() {
        return ect0Count;
    }

    /**
     * Returns the ECT1 count from this frame or {@code -1} if not present.
     *
     * @return the ECT1 count from this frame or {@code -1} if not present
     */
    public long ect1Count() {
        return ect1Count;
    }

    /**
     * Returns the ECN-CE count from this frame or {@code -1} if not present.
     *
     * @return the ECN-CE count from this frame or {@code -1} if not present
     */
    public long ecnCECount() {
        return ecnCECount;
    }

    /**
     * Returns true if this frame contains an acknowledgment for the
     * given packet number.
     *
     * @param packetNumber a packet number
     * @return true if this frame acknowledges the packet number
     */
    public boolean isAcknowledging(long packetNumber) {
        return isAcknowledging(largestAcknowledged, ackRanges, packetNumber);
    }

    /**
     * Returns true if the given range is acknowledged by this frame.
     *
     * @param first the first packet in the range, inclusive
     * @param last  the last packet in the range, inclusive
     * @return true if the given range is acknowledged by this frame
     */
    public boolean isRangeAcknowledged(long first, long last) {
        return isRangeAcknowledged(largestAcknowledged, ackRanges, first, last);
    }

    /**
     * Returns the smallest packet number acknowledged by this {@code AckFrame}.
     *
     * @return the smallest packet number acknowledged by this {@code AckFrame}
     */
    public long smallestAcknowledged() {
        return smallestAcknowledged(largestAcknowledged, ackRanges);
    }

    /**
     * Returns a stream of packet numbers acknowledged by this frame.
     *
     * @return acknowledged packet numbers in descending order
     */
    public LongStream acknowledged() {
        return StreamSupport.longStream(new AckFrameSpliterator(this), false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof AckFrame ackFrame
                && largestAcknowledged == ackFrame.largestAcknowledged
                && ackDelay == ackFrame.ackDelay
                && ackRangeCount == ackFrame.ackRangeCount
                && countsPresent == ackFrame.countsPresent
                && ect0Count == ackFrame.ect0Count
                && ect1Count == ackFrame.ect1Count
                && ecnCECount == ackFrame.ecnCECount
                && ackRanges.equals(ackFrame.ackRanges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(largestAcknowledged, ackDelay,
                            ackRanges, ect0Count, ect1Count, ecnCECount);
    }

    @Override
    public String toString() {
        String res = "AckFrame("
                + "largestAcknowledged=" + largestAcknowledged
                + ", ackDelay=" + ackDelay
                + ", ackRanges=[" + prettyRanges() + "]";
        if (countsPresent) {
            res = res
                    + ", ect0Count=" + ect0Count
                    + ", ect1Count=" + ect1Count
                    + ", ecnCECount=" + ecnCECount;
        }
        res += ")";
        return res;
    }

    private static void validateRangeCount(ByteBuffer buffer,
                                           int type,
                                           int ackRangeCount,
                                           boolean countsPresent) throws QuicTransportException {
        int minimumTrailingFields = 1 + (countsPresent ? 3 : 0);
        int remaining = buffer.remaining();
        if (remaining < minimumTrailingFields
                || ackRangeCount > (remaining - minimumTrailingFields) / 2) {
            throw new QuicTransportException("ACK range count exceeds remaining payload: " + ackRangeCount,
                                             type,
                                             QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
    }

    private static long validateFirstRange(int type,
                                           long largestAcknowledged,
                                           long firstAckRange) throws QuicTransportException {
        long smallestAcknowledged = largestAcknowledged - firstAckRange;
        if (smallestAcknowledged < 0) {
            throw new QuicTransportException("Negative PN acknowledged",
                                             type,
                                             QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        return smallestAcknowledged;
    }

    private static void decodeAdditionalRanges(ByteBuffer buffer,
                                               int type,
                                               int ackRangeCount,
                                               long smallestAcknowledged,
                                               List<AckRange> ackRanges,
                                               boolean captureRanges) throws QuicTransportException {
        for (int i = 1; i <= ackRangeCount; i++) {
            long gap = decodeVLField(buffer, "gap", type);
            long len = decodeVLField(buffer, "range length", type);
            if (captureRanges) {
                ackRanges.add(i, AckRange.of(gap, len));
            }
            smallestAcknowledged -= gap + len + 2;
            if (smallestAcknowledged < 0) {
                // verify after each range to avoid wrap around
                throw new QuicTransportException("Negative PN acknowledged",
                                                 type, QuicTransportErrors.FRAME_ENCODING_ERROR);
            }
        }
    }

    // This is described in RFC 9000, Section 19.3.1 ACK Ranges
    // https://www.rfc-editor.org/rfc/rfc9000#name-ack-ranges
    private static boolean isAcknowledging(long largestAcknowledged,
                                           List<AckRange> ackRanges,
                                           long packetNumber) {
        if (packetNumber > largestAcknowledged) {
            return false;
        }
        var largest = largestAcknowledged;
        long smallest = largestAcknowledged + 2;
        for (var ackRange : ackRanges) {
            largest = smallest - ackRange.gap - 2;
            if (packetNumber > largest) {
                return false;
            }
            smallest = largest - ackRange.range;
            if (packetNumber >= smallest) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRangeAcknowledged(long largestAcknowledged,
                                               List<AckRange> ackRanges,
                                               long first,
                                               long last) {
        if (last > largestAcknowledged) {
            return false;
        }
        var largest = largestAcknowledged;
        long smallest = largestAcknowledged + 2;
        for (var ackRange : ackRanges) {
            largest = smallest - ackRange.gap - 2;
            if (last > largest) {
                return false;
            }
            smallest = largest - ackRange.range;
            if (first >= smallest) {
                return true;
            }
        }
        return false;
    }

    // This is described in RFC 9000, Section 19.3.1 ACK Ranges
    // https://www.rfc-editor.org/rfc/rfc9000#name-ack-ranges
    private static long smallestAcknowledged(long largestAcknowledged,
                                             List<AckRange> ackRanges) {
        long largest = largestAcknowledged;
        long smallest = largest + 2;
        for (AckRange ackRange : ackRanges) {
            largest = smallest - ackRange.gap - 2;
            smallest = largest - ackRange.range;
        }
        return smallest;
    }

    private int computeSize() {
        int size = variableLengthFieldLength(typeField())
                + variableLengthFieldLength(largestAcknowledged)
                + variableLengthFieldLength(ackDelay)
                + variableLengthFieldLength(ackRangeCount)
                + variableLengthFieldLength(ackRanges.getFirst().range())
                + ackRanges.stream().skip(1).mapToInt(AckRange::size).sum();
        if (countsPresent) {
            size = size + variableLengthFieldLength(ect0Count)
                    + variableLengthFieldLength(ect1Count)
                    + variableLengthFieldLength(ecnCECount);
        }
        return size;
    }

    private String prettyRanges() {
        String result = null;
        long largest;
        long smallest = largestAcknowledged + 2;
        for (var ackRange : ackRanges) {
            largest = smallest - ackRange.gap - 2;
            smallest = largest - ackRange.range;
            result = smallest + ".." + largest + (result != null ? ", " + result : "");
        }
        return result;
    }

    /**
     * An ACK range, composed of a gap and a range.
     */
    public static final class AckRange {
        /**
         * Initial zero-gap zero-range value.
         */
        public static final AckRange INITIAL = new AckRange(0, 0);

        private final long gap;
        private final long range;

        private AckRange(long gap, long range) {
            requireVLRange(gap, "gap");
            requireVLRange(range, "range");
            this.gap = gap;
            this.range = range;
        }

        /**
         * Create a new ACK range.
         *
         * @param gap   gap before the range
         * @param range acknowledged range length
         * @return new ACK range
         */
        public static AckRange of(long gap, long range) {
            if (gap == 0 && range == 0) {
                return INITIAL;
            }
            return new AckRange(gap, range);
        }

        /**
         * Returns the gap before this acknowledged range.
         *
         * @return the gap before this acknowledged range.
         */
        public long gap() {
            return gap;
        }

        /**
         * Returns the size of this acknowledged range.
         *
         * @return the size of this acknowledged range.
         */
        public long range() {
            return range;
        }

        /**
         * Returns the encoded size of this range in bytes.
         *
         * @return the encoded size of this range in bytes.
         */
        public int size() {
            return variableLengthFieldLength(gap) + variableLengthFieldLength(range);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AckRange other)) {
                return false;
            }
            return gap == other.gap && range == other.range;
        }

        @Override
        public int hashCode() {
            return Objects.hash(gap, range);
        }

        @Override
        public String toString() {
            return "AckRange[gap=" + gap + ", range=" + range + ']';
        }
    }

    /**
     * A builder that allows incrementally building the {@code AckFrame}
     * that will need to be sent, as new packets are received.
     * This class is not thread-safe.
     */
    public static final class AckFrameBuilder {
        private long largestAckAcked = -1;
        private long largestAcknowledged = -1;
        private long ackDelay = 0;
        private List<AckRange> ackRanges = new ArrayList<>();
        private long ect0Count = -1;
        private long ect1Count = -1;
        private long ecnCECount = -1;

        private AckFrameBuilder() {
        }

        private AckFrameBuilder(AckFrame frame) {
            largestAckAcked = -1;
            largestAcknowledged = frame.largestAcknowledged;
            ackDelay = frame.ackDelay;
            ackRanges.addAll(frame.ackRanges);
            ect0Count = frame.ect0Count;
            ect1Count = frame.ect1Count;
            ecnCECount = frame.ecnCECount;
        }

        /**
         * An empty builder.
         *
         * @return new empty builder
         */
        public static AckFrameBuilder create() {
            return new AckFrameBuilder();
        }

        /**
         * A builder initialized from the content of an AckFrame.
         *
         * @param frame the {@code AckFrame} to initialize this builder with;
         *              must not be {@code null}
         * @return new builder initialized from the provided frame
         */
        public static AckFrameBuilder create(AckFrame frame) {
            return new AckFrameBuilder(frame);
        }

        /**
         * Return the smallest packet number that must remain acknowledged after builder pruning.
         *
         * @return lower bound for acknowledged packet numbers, or {@code -1} when unset
         */
        public long largestAckAcked() {
            return largestAckAcked;
        }

        /**
         * Drops all ACKs for packets whose number is smaller
         * than the given {@code largestAckAcked}.
         *
         * @param largestAckAcked the smallest packet number that
         *                       should be acknowledged by this
         *                       {@link AckFrame}.
         * @return this builder
         */
        public AckFrameBuilder dropAcksBefore(long largestAckAcked) {
            if (largestAckAcked > this.largestAckAcked) {
                this.largestAckAcked = largestAckAcked;
                return dropIfSmallerThan(largestAckAcked);
            } else {
                this.largestAckAcked = largestAckAcked;
            }
            return this;
        }

        /**
         * Drops all instances of {@link AckRange} after the given
         * index in the {@linkplain #ackRanges() ACK range list}, and computes
         * the new smallest packet number now acknowledged by this
         * {@link AckFrame}: this computed packet number will then be
         * returned by {@link #largestAckAcked()}.
         * This is a no-op if index is greater or equal to
         * {@code ackRanges().size() - 1}.
         *
         * @param index the index after which ranges should be dropped
         * @return this builder
         */
        public AckFrameBuilder dropAckRangesAfter(int index) {
            if (index < 0) {
                throw new IllegalArgumentException("invalid index %s for size %s"
                                                           .formatted(index, ackRanges.size()));
            }
            if (index >= ackRanges.size() - 1) {
                return this;
            }
            long newLargestAckAcked = dropRangesIfAfter(index);
            largestAckAcked = newLargestAckAcked;
            return this;
        }

        /**
         * Sets the ack delay.
         *
         * @param ackDelay the ACK delay
         * @return this builder
         */
        public AckFrameBuilder ackDelay(long ackDelay) {
            this.ackDelay = ackDelay;
            return this;
        }

        /**
         * Sets the ECT(0) count. Passing {@code -1} unsets the ECT(0) count.
         *
         * @param ect0Count the ECT(0) count
         * @return this builder
         */
        public AckFrameBuilder ect0Count(long ect0Count) {
            this.ect0Count = ect0Count;
            return this;
        }

        /**
         * Sets the ECT(1) count. Passing {@code -1} unsets the ECT(1) count.
         *
         * @param ect1Count the ECT(1) count
         * @return this builder
         */
        public AckFrameBuilder ect1Count(long ect1Count) {
            this.ect1Count = ect1Count;
            return this;
        }

        /**
         * Sets the ECN-CE count. Passing {@code -1} unsets the ECN-CE count.
         *
         * @param ecnCECount the ECN-CE count
         * @return this builder
         */
        public AckFrameBuilder ecnCECount(long ecnCECount) {
            this.ecnCECount = ecnCECount;
            return this;
        }

        /**
         * Adds the given packet number to the list of ack ranges.
         * If the packet is already being acknowledged by this frame,
         * do nothing.
         *
         * @param packetNumber the packet number
         * @return this builder
         */
        public AckFrameBuilder addAck(long packetNumber) {
            // check if we need to acknowledge this packet
            if (packetNumber <= largestAckAcked) {
                return this;
            }
            if (ackRanges.isEmpty()) {
                // easy case: we only have one packet to acknowledge!
                return acknowledgeFirstPacket(packetNumber);
            } else if (packetNumber > largestAcknowledged) {
                return acknowledgeLargerPacket(packetNumber);
            } else if (packetNumber < largestAcknowledged) {
                // now is the complex case: we need to find out:
                //   - whether this packet is already acknowledged, in which case,
                //     there is nothing to do (great)
                //   - or whether we can extend an existing range
                //   - or whether we need to create a new range (if the packet falls
                //     within a gap whose value is > 0).
                //   - or whether we should merge two ranges if the packet falls
                //     on a gap whose value is 0
                ListIterator<AckRange> iterator = ackRanges.listIterator();
                long largest = largestAcknowledged;
                long smallest = largest + 2;
                int index = -1;
                while (iterator.hasNext()) {
                    var ackRange = iterator.next();
                    // index of the current ackRange element
                    index++;
                    // largest packet number acknowledged by this ackRange
                    largest = smallest - ackRange.gap - 2;
                    // smallest packet number acknowledged by this ackRange
                    smallest = largest - ackRange.range;

                    // if the packet number we want to acknowledge is greater
                    // than the largest packet acknowledged by this ackRange
                    // there are two cases:
                    if (packetNumber > largest) {
                        // the packet number is just above the largest packet
                        if (packetNumber - 1 == largest) {
                            // the current ackRange must have a gap, and we can simply
                            // reduce that gap by 1, and extend the range by 1.
                            // the case where the current ackrange doesn't have a gap
                            // and the packet number is the largest + 1 should have
                            // been handled when processing the previous ackRange.
                            var gap = ackRange.gap - 1;
                            var range = ackRange.range + 1;
                            var replaced = AckRange.of(gap, range);
                            ackRanges.set(index, replaced);
                            return this;
                        } else {
                            // the packet falls within the gap of this ack range.
                            // we need to split the ackRange in two...
                            //
                            // in the case where we have
                            //   [31,31] [27,27] -> 31, AckRange[g=0, r=0], AckRange[g=2, r=0]
                            // and we want to acknowledge 29.
                            // we should end up with:
                            //   [31,31] [29,29] [27,27] ->
                            //      31, AckRange[g=0, r=0], AckRange[g=0, r=0], AckRange[g=0, r=0]

                            // compute the smallest packet that was acknowledged by the
                            // previous ackRange. This should be:
                            var previousSmallest = largest + ackRange.gap + 2;

                            // compute the point at which we should split the current ackRange
                            // the current ackRange will be split in two: first, and second
                            // - first will replace the current ackRange
                            // - second will be inserted after first
                            var firstgap = previousSmallest - packetNumber - 2;
                            AckRange first = AckRange.of(firstgap, 0);
                            AckRange second = AckRange.of(ackRange.gap - firstgap - 2, ackRange.range);
                            ackRanges.set(index, first);
                            iterator.add(second);
                            return this;
                        }
                    } else if (packetNumber < smallest) {
                        // otherwise, if the packet number is smaller than
                        // the smallest packet acknowledged by the current ackRange,
                        // there are two cases:

                        // If the current ackRange is the last: it's simple!
                        // But there are again two cases:
                        if (!iterator.hasNext()) {
                            // If the packet number we want to acknowledge is just below
                            // the smallest packet number acknowledge by the current
                            // ackRange, there is no gap between the packet number and
                            // the current range, so we can simply extend the current range
                            // Otherwise, we need to append a new ackRange.
                            if (packetNumber == smallest - 1) {
                                // no gap: we can extend the current range
                                AckRange replaced = AckRange
                                        .of(ackRange.gap, ackRange.range + 1);
                                ackRanges.set(index, replaced);
                            } else {
                                // gap: we need to add a new AckRange
                                AckRange last = AckRange.of(smallest - packetNumber - 2, 0);
                                iterator.add(last);
                            }
                            return this;
                        } else if (packetNumber == smallest - 1) {
                            // Otherwise, if the packet number to be acknowledged is
                            // just below the smallest packet acknowledged by the current
                            // range, there are again two cases, depending on
                            // whether the next ackRange has a gap that can be reduced,
                            // or not
                            AckRange next = ackRanges.get(index + 1);
                            // if the gap of the next packet can be reduced, that's great!
                            // just do it! We need to reduce that gap by one, and extend
                            // the range of the current ackRange
                            if (next.gap > 0) {
                                // reduce the gap in the next ackrange, and increase
                                // the range in the current ackrange.
                                AckRange first = AckRange.of(ackRange.gap, ackRange.range + 1);
                                AckRange second = AckRange.of(next.gap - 1, next.range);
                                ackRanges.set(index, first);
                                ackRanges.set(index + 1, second);
                                return this;
                            } else {
                                // Otherwise, that's the complex case again.
                                // we have a gap of 1 packet between 2 ackranges.
                                // our packet number falls exactly in that gap.
                                // We need to merge the two ranges!
                                // merge with next ackRange: remove the current ackRange,
                                // the ackRange at the current index is now the next ackRange,
                                // replace it with a merged ACK range.
                                var mergedRanges = ackRange.range + next.range + 2;
                                iterator.remove();
                                ackRanges.set(index, AckRange.of(ackRange.gap, mergedRanges));
                                return this;
                            }
                        }
                    } else {
                        // Otherwise, the packet is already acknowledged!
                        // nothing to do.
                        return this;
                    }
                }
            } else {
                // already acknowledged!
                return this;
            }
            return this;
        }

        /**
         * Returns true if this builder contains no ACK yet.
         *
         * @return true if this builder contains no ACK yet
         */
        public boolean isEmpty() {
            return ackRanges.isEmpty();
        }

        /**
         * Returns the number of ACK ranges in this builder, including the fake
         * first ACK range.
         *
         * @return the number of ACK ranges in this builder
         */
        public int length() {
            return ackRanges.size();
        }

        /**
         * Returns true if the given packet number is already acknowledged
         * by this builder.
         *
         * @param packetNumber a packet number
         * @return true if the packet number is already acknowledged
         */
        public boolean isAcknowledging(long packetNumber) {
            if (isEmpty()) {
                return false;
            }
            return AckFrame.isAcknowledging(largestAcknowledged, ackRanges, packetNumber);
        }

        /**
         * Returns the smallest packet number acknowledged by this builder.
         *
         * @return the smallest packet number acknowledged by this builder
         */
        public long smallestAcknowledged() {
            if (largestAcknowledged == -1L) {
                return -1L;
            }
            return AckFrame.smallestAcknowledged(largestAcknowledged, ackRanges);
        }

        /**
         * Builds an {@code AckFrame} from this builder's content.
         *
         * @return a new {@code AckFrame}
         */
        public AckFrame build() {
            return new AckFrame(largestAcknowledged, ackDelay, ackRanges,
                                ect0Count, ect1Count, ecnCECount);
        }

        // drop acknowledgement of all packet numbers acknowledged
        // by AckRange instances coming after the given index, and
        // return the smallest packet number now acked by this
        // AckFrame.
        private long dropRangesIfAfter(int ackIndex) {
            long largest = largestAcknowledged;
            long smallest = largest + 2;
            ListIterator<AckRange> iterator = ackRanges.listIterator();
            int index = -1;
            boolean removeRemainings = false;
            long newLargestAckAcked = -1;
            while (iterator.hasNext()) {
                if (index == ackIndex) {
                    newLargestAckAcked = smallest;
                    removeRemainings = true;
                }
                AckRange ackRange = iterator.next();
                if (removeRemainings) {
                    iterator.remove();
                    continue;
                }
                index++;
                largest = smallest - ackRange.gap - 2;
                smallest = largest - ackRange.range;
            }
            return newLargestAckAcked;
        }

        // drop acknowledgement of all packet numbers less than or equal
        // to largestAckAcked.
        private AckFrameBuilder dropIfSmallerThan(long largestAckAcked) {
            if (largestAckAcked >= largestAcknowledged) {
                largestAcknowledged = -1;
                ackRanges.clear();
                return this;
            }
            long largest = largestAcknowledged;
            long smallest = largest + 2;
            ListIterator<AckRange> iterator = ackRanges.listIterator();
            int index = -1;
            boolean removeRemainings = false;
            while (iterator.hasNext()) {
                AckRange ackRange = iterator.next();
                if (removeRemainings) {
                    iterator.remove();
                    continue;
                }
                index++;
                largest = smallest - ackRange.gap - 2;
                smallest = largest - ackRange.range;
                if (largest <= largestAckAcked) {
                    iterator.remove();
                    removeRemainings = true;
                } else if (smallest <= largestAckAcked) {
                    long removed = largestAckAcked - smallest + 1;
                    long gap = ackRange.gap;
                    long range = ackRange.range - removed;
                    ackRanges.set(index, AckRange.of(gap, range));
                    removeRemainings = true;
                }
            }
            return this;
        }

        private AckFrameBuilder acknowledgeFirstPacket(long packetNumber) {
            largestAcknowledged = packetNumber;
            ackRanges.add(AckRange.INITIAL);
            return this;
        }

        private AckFrameBuilder acknowledgeLargerPacket(long largerThanLargest) {
            var packetNumber = largerThanLargest;
            // the new packet is larger than the largest acknowledged
            var firstAckRange = ackRanges.getFirst();
            if (largestAcknowledged == packetNumber - 1) {
                // if packetNumber is largestAcknowledged + 1, we can simply
                // extend the first ack range by 1
                firstAckRange = AckRange.of(0, firstAckRange.range + 1);
                ackRanges.set(0, firstAckRange);
            } else {
                // otherwise - we have a gap - we need to acknowledge the new packetNumber,
                // and then add the gap that separate it from the previous largestAcknowledged...
                ackRanges.addFirst(AckRange.INITIAL); // acknowledge packetNumber only
                long gap = packetNumber - largestAcknowledged - 2;
                var secondAckRange = AckRange.of(gap, firstAckRange.range);
                ackRanges.set(1, secondAckRange); // add the gap
            }
            largestAcknowledged = packetNumber;
            return this;
        }

    }

    private static class AckFrameSpliterator implements Spliterator.OfLong {

        private final AckFrame ackFrame;
        private final Iterator<AckRange> ackRangeIterator;
        private long largest;
        private long smallest;
        private long pn;  // the current packet number

        AckFrameSpliterator(AckFrame ackFrame) {
            this.ackFrame = ackFrame;
            this.largest = ackFrame.largestAcknowledged();
            this.smallest = largest + 2;
            this.ackRangeIterator = ackFrame.ackRanges.iterator();
        }

        @Override
        public long estimateSize() {
            // It is costly to compute an estimate, so we just
            // return Long.MAX_VALUE instead
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            // NONNULL - nulls are not expected to be returned by this long spliterator
            // IMMUTABLE - ackFrame.ackRanges() returns unmodifiable list, which cannot be
            //             structurally modified
            return NONNULL | IMMUTABLE;
        }

        @Override
        public OfLong trySplit() {
            // null - this spliterator cannot be split
            return null;
        }

        @Override
        public boolean tryAdvance(LongConsumer action) {
            // First call will see pn == 0 and smallest >= 2,
            // which guarantees we will not enter the `if` below
            // before pn has been initialized from the
            // first ackRange value
            if (pn >= smallest) {
                return ackAndDecId(action);
            }
            if (ackRangeIterator.hasNext()) {
                var ackRange = ackRangeIterator.next();
                largest = smallest - ackRange.gap() - 2;
                smallest = largest - ackRange.range;
                pn = largest;
                return ackAndDecId(action);
            }
            return false;
        }

        // The stream returns packet number in decreasing order
        // (largest packet number is returned first)
        private boolean ackAndDecId(LongConsumer action) {
            action.accept(pn--);
            return true;
        }
    }
}
