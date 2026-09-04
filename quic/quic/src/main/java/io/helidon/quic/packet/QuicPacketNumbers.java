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

package io.helidon.quic.packet;

import java.nio.ByteBuffer;

import io.helidon.common.Api;

/**
 * QUIC packet number encoding/decoding routines.
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc9000">
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
 */
@Api.Internal
public class QuicPacketNumbers {

    /**
     * Utility class.
     */
    private QuicPacketNumbers() {
    }

    /**
     * Returns the number of bytes needed to encode a packet number
     * given the full packet number and the largest ACK'd packet.
     *
     * @param fullPN       the full packet number
     * @param largestAcked the largest ACK'd packet, or -1 if none so far
     * @return the number of bytes required to encode the packet
     * @throws IllegalArgumentException if number can't represented in 4 bytes
     */
    public static int computePacketNumberLength(long fullPN, long largestAcked) {

        long numUnAcked;

        if (largestAcked == -1) {
            numUnAcked = fullPN + 1;
        } else {
            numUnAcked = fullPN - largestAcked;
        }

        /*
         * log(n, 2) + 1; ceil(minBits / 8);
         *
         * value will never be non-positive, so don't need to worry about the
         * special cases.
         */
        int minBits = 64 - Long.numberOfLeadingZeros(numUnAcked) + 1;
        int numBytes = (minBits + 7) / 8;

        if (numBytes > 4) {
            throw new IllegalArgumentException(
                    "Encoded packet number needs %s bytes for pn=%s, ack=%s"
                            .formatted(numBytes, fullPN, largestAcked));
        }

        return numBytes;
    }

    /**
     * Encode the full packet number against the largest ACK'd packet.
     *
     * Follows the algorithm outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-sample-packet-number-encodi">
     * RFC 9000. Appendix A.2</a>
     *
     * @param fullPN       the full packet number
     * @param largestAcked the largest ACK'd packet, or -1 if none so far
     * @return byte array containing fullPN
     * @throws IllegalArgumentException if number can't be represented in 4 bytes
     */
    public static byte[] encodePacketNumber(
            long fullPN, long largestAcked) {

        // throws IAE if more than 4 bytes are needed
        int numBytes = computePacketNumberLength(fullPN, largestAcked);
        return truncatePacketNumber(fullPN, numBytes);
    }

    /**
     * Truncate the full packet number to fill into {@code numBytes}.
     *
     * Follows the algorithm outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-sample-packet-number-encodi">
     * RFC 9000, Appendix A.2</a>
     *
     * @param fullPN   the full packet number
     * @param numBytes the number of bytes in which to encode
     *                the packet number
     * @return byte array containing fullPN
     * @throws IllegalArgumentException if numBytes is out of range
     * <p>Note: {@code numBytes} should have been computed using
     *        {@link #computePacketNumberLength(long, long)}
     */
    public static byte[] truncatePacketNumber(
            long fullPN, int numBytes) {

        if (numBytes <= 0 || numBytes > 4) {
            throw new IllegalArgumentException(
                    "Invalid packet number length: " + numBytes);
        }

        // Fill in the array.
        byte[] retval = new byte[numBytes];
        for (int i = numBytes - 1; i >= 0; i--) {
            retval[i] = (byte) (fullPN & 0xff);
            fullPN = fullPN >>> 8;
        }

        return retval;
    }

    /**
     * Decode the packet numbers against the largest ACK'd packet after header
     * protection has been removed.
     *
     * Follows the algorithm outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-sample-packet-number-decodi">
     * RFC 9000, Appendix A.3</a>
     *
     * @param largestPN the largest packet number that has been successfully
     *                 processed in the current packet number space
     * @param buf       a {@code ByteBuffer} containing the value of the
     *                 Packet Number field
     * @param pnNBytes  the number of <b>bytes</b> indicated by the Packet
     *                 Number Length field
     * @return the decoded packet number
     * @throws java.nio.BufferUnderflowException if there is not enough data in the
     *                                          buffer
     */
    public static long decodePacketNumber(
            long largestPN, ByteBuffer buf, int pnNBytes) {


        long truncatedPN = 0;
        for (int i = 0; i < pnNBytes; i++) {
            truncatedPN = (truncatedPN << 8) | (buf.get() & 0xffL);
        }

        int pnNBits = pnNBytes * 8;

        long expectedPN = largestPN + 1L;
        long pnWin = 1L << pnNBits;
        long pnHWin = pnWin / 2L;
        long pnMask = pnWin - 1L;

        // The incoming packet number should be greater than
        // expectedPN - pn_HWin and less than or equal to
        // expectedPN + pn_HWin
        //
        // This means we cannot just strip the trailing bits from
        // expectedPN and add the truncatedPN because that might
        // yield a value outside the window.
        //
        // The following code calculates a candidate value and
        // makes sure it's within the packet number window.
        // Note the extra checks to prevent overflow and underflow.
        long candidatePN = (expectedPN & ~pnMask) | truncatedPN;

        if ((candidatePN <= (expectedPN - pnHWin))
                && (candidatePN < ((1L << 62) - pnWin))) {
            return candidatePN + pnWin;
        }

        if ((candidatePN - pnHWin > expectedPN)
                && (candidatePN >= pnWin)) {
            return candidatePN - pnWin;
        }
        return candidatePN;
    }
}
