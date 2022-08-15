/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.encoding.brotli;

/**
 * BitWriter is the class used to write bit by bit into an array.
 */
class BitWriter {
    private static final System.Logger LOGGER = System.getLogger(BitWriter.class.getName());

    public static int writeBit(int nBit, long bits, int position, int[] array) throws BrotliException {
        if (nBit == 64) {
            LOGGER.log(System.Logger.Level.WARNING, "Writing 64 bits : Possible data corruption");
        }
        checkParams(nBit, 64);
        return write(nBit, bits, position, array);
    }

    public static int writeBit(int nBit, int bits, int position, int[] array) throws BrotliException {
        checkParams(nBit, 32);
        return write(nBit, bits, position, array);
    }

    public static int writeBit(int nBit, byte bits, int position, int[] array) throws BrotliException {
        checkParams(nBit, 8);
        return write(nBit, bits, position, array);
    }

    public static int writeBit(int nBit, boolean bit, int position, int[] array) throws BrotliException {
        checkParams(nBit, 1);
        return write(nBit, bit ? 1 : 0, position, array);
    }

    private static int write(int nBit, long bits, int position, int[] array) {
        int offset = position >> 3;
        int bitsReservedInFirstByte = position & 7;
        bits <<= bitsReservedInFirstByte;
        array[offset++] |= (int) (bits & 0xFF);
        for (int bitsLeftToWrite = nBit + bitsReservedInFirstByte;
                bitsLeftToWrite >= 9;
                bitsLeftToWrite -= 8) {
            bits >>= 8;
            array[offset++] = (int) (bits & 0xFF);
        }
        if (offset < array.length) {
            array[offset] = 0;
        }
        position += nBit;
        return position;
    }

    /**
     * Check writeBit parameters.
     *
     * @param numberOfBits Number of bits required
     * @param limit        Maximum number of bits available.
     * @throws BrotliException Throws BrotliException if trying to write more bits than provided.
     */
    private static void checkParams(int numberOfBits, int limit) throws BrotliException {
        if (numberOfBits > limit) {
            throw new BrotliException("Trying to write too many bits");
        }
    }

}
