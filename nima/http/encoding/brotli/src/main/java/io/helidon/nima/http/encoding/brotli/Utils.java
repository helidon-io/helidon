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

import java.io.IOException;

/**
 * Utility class which gather mathematics operation as well as buffer management
 */
class Utils {

    public static void writeToOutputStream(State state) {
        if (state.outputOffset == 0) {
            return;
        }
        try {
            state.outputStream.write(state.output, 0, state.outputOffset);
            state.streamOffset += state.outputOffset;
            state.availableOut = state.output.length;
            state.outputOffset = 0;
            state.output = new byte[state.output.length];
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static void writeBuffer(int[] source,
                                   int sourceOffSet,
                                   int length,
                                   byte[] dest,
                                   int destOffset) throws BrotliException {
        if (source == null || dest == null) {
            throw new BrotliException("Error: source or dest is null");
        } else if (length < 0) {
            throw new BrotliException("Error: copyLength is negative: " + length);
        } else if (dest.length < length + destOffset) {
            throw new BrotliException("Error: IndexOutOfBounds Exception detected");
        } else if (source.length < length + sourceOffSet) {
            throw new BrotliException("Error: IndexOutOfBounds Exception detected");
        }

        for (int i = sourceOffSet; i < (sourceOffSet + length); i++) {
            if (destOffset < dest.length && i < source.length) {
                dest[destOffset++] = (byte) source[i];
            }
        }

    }

    public static void writeBuffer(int[] source,
                                   int sourceOffSet,
                                   int length,
                                   int[] dest,
                                   int destOffset) throws BrotliException {

        if (source == null || dest == null) {
            throw new BrotliException("Error: source or dest is null");
        } else if (length < 0) {
            throw new BrotliException("Error: copyLength is negative: " + length);
        } else if (dest.length < length + destOffset) {
            throw new BrotliException("Error: IndexOutOfBounds Exception detected");
        } else if (source.length < length + sourceOffSet) {
            throw new BrotliException("Error: IndexOutOfBounds Exception detected");
        }

        for (int i = sourceOffSet; i < (sourceOffSet + length); i++) {
            dest[destOffset++] = source[i];
        }

    }

    public static void copyBytes(int[] dest, int[] src, int nbElement) throws BrotliException {
        copyBytes(dest, 0, src, 0, nbElement);
    }

    public static void copyBytes(int[] dest, int destIndex, int[] src, int srcIndex, int nbElement) throws BrotliException {

        if (src == null || dest == null) {
            throw new BrotliException("Error: source or dest is null");
        } else if (nbElement < 0) {
            throw new BrotliException("Error: copyLength is negative: " + nbElement);
        } else if (dest.length < nbElement + destIndex) {
            throw new BrotliException("Error: IndexOutOfBounds Exception detected");
        }

        for (int i = srcIndex; i < (srcIndex + nbElement); i++) {
            dest[destIndex++] = src[i];
        }
    }

    public static void copyCommands(Command[] dest, int destOff, Command[] source, int sourceOff, int length)
            throws BrotliException {
        if (dest.length < destOff + length) {
            throw new BrotliException("Copy Command : no enough space into destination");
        }
        if (source.length < sourceOff + length) {
            throw new BrotliException("Copy Command : no enough space into source");
        }
        if (length < 0) {
            throw new BrotliException("Copy Command : length is negative");
        }

        for (int i = destOff; i < destOff + length; i++) {
            dest[i] = source[sourceOff++];
        }
    }

    public static double fastLog2(int i) {
        if (i < 256) {
            return Tables.kBrotliLog2Table[i];
        }
        return (Math.log(i) / Math.log(2) + 1e-10);
    }

    public static int log2FloorNonZero(int n) {
        int result = 0;
        while ((n >>= 1) != 0) {
            result++;
        }
        return result;
    }

    public static void put(State state, int b) {
        state.inputBuffer[state.availableIn++] = b & 0xFF;
        state.inputLength++;
    }

    public static long get64Bits(int[] buffer, int index) {
        int position = 0;
        long result = 0;
        for (int i = index; i < index + 8; i++) {
            if (i < buffer.length) {
                result |= ((long) buffer[i] << position);
            } else {
                result |= 0L;
            }
            position += 8;
        }
        return result;
    }

    public static int get32Bits(int[] buffer, int index) {
        int position = 0;
        int result = 0;
        for (int i = index; i < index + 4; i++) {
            if (i < buffer.length) {
                result |= (buffer[i] << position);
            } else {
                result |= 0L;
            }
            position += 8;
        }
        return result;
    }

    public static int get16Bits(int[] buffer, int index) {
        int position = 0;
        int result = 0;
        for (int i = index; i < index + 2; i++) {
            if (i < buffer.length) {
                result |= (buffer[i] << position);
            } else {
                result |= 0L;
            }
            position += 8;
        }
        return result;
    }

    public static boolean brotliIsMostlyUTF8(int[] data, int position, int mask,
                                             long length, double min_fraction) {
        int sizeUtf8 = 0;
        int i = 0;
        while (i < length) {
            int[] symbol = new int[1];
            int bytesRead = brotliParseAsUTF8(symbol, data, (position + i) & mask, length - i);
            i += bytesRead;
            if (symbol[0] < 0x110000) {
                sizeUtf8 += bytesRead;
            }
        }
        return ((double) sizeUtf8 > min_fraction * (double) length);
    }

    public static int brotliParseAsUTF8(int[] symbol, int[] input, int index, long size) {
        /* ASCII */
        if ((input[index] & 0x80) == 0) {
            symbol[0] = input[0];
            if (symbol[0] > 0) {
                return 1;
            }
        }
        /* 2-byte UTF8 */
        if (size > 1 &&
                (input[0] & 0xE0) == 0xC0 &&
                (input[1] & 0xC0) == 0x80) {
            symbol[0] = (
                    ((input[0] & 0x1F) << 6) |
                            (input[1] & 0x3F));
            if (symbol[0] > 0x7F) {
                return 2;
            }
        }
        /* 3-byte UFT8 */
        if (size > 2 &&
                (input[index] & 0xF0) == 0xE0 &&
                (input[index + 1] & 0xC0) == 0x80 &&
                (input[index + 2] & 0xC0) == 0x80) {
            symbol[0] = (
                    ((input[0] & 0x0F) << 12) |
                            ((input[1] & 0x3F) << 6) |
                            (input[2] & 0x3F));
            if (symbol[0] > 0x7FF) {
                return 3;
            }
        }
        /* 4-byte UFT8 */
        if (size > 3 &&
                (input[index] & 0xF8) == 0xF0 &&
                (input[index + 1] & 0xC0) == 0x80 &&
                (input[index + 2] & 0xC0) == 0x80 &&
                (input[index + 3] & 0xC0) == 0x80) {
            symbol[0] = (
                    ((input[index] & 0x07) << 18) |
                            ((input[index + 1] & 0x3F) << 12) |
                            ((input[index + 2] & 0x3F) << 6) |
                            (input[index + 3] & 0x3F));
            if (symbol[0] > 0xFFFF && symbol[0] <= 0x10FFFF) {
                return 4;
            }
        }
        /* Not UTF8, emit a special symbol above the UTF8-code space */
        symbol[0] = 0x110000 | input[0];
        return 1;
    }

    public static void swap(int[] array, int j, int i) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    public static int brotliMaxBackwardLimit(int value) {
        return ((1 << value) - Constant.BROTLI_WINDOW_GAP);
    }
}
