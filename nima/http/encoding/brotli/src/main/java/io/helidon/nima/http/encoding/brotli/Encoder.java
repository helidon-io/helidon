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
import java.lang.System.Logger.Level;

class Encoder {

    private static final System.Logger LOGGER = System.getLogger(Encoder.class.getName());

    /**
     * Compress the data contained in internal buffer.
     *
     * @param state  Current encoder state.
     * @param isLast Is it the last block to be emitted ?
     * @throws BrotliException Throws BrotliException if error detected.
     */
    public static void compress(State state, boolean isLast) throws BrotliException {
        boolean hasNewInput = false;

        if (!checkInitialization(state)) {
            throw new BrotliException("Error: Not initialized properly");
        }

        if (state.quality == 0) {
            state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_PROCESSING;
            brotliCompressStreamFast(state, isLast);
            return;
        }

        if (state.quality == 10) {
            state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_PROCESSING;
            Compress.compressQuality10(state, isLast);
            return;
        }

        while (true) {
            int remainingBlockSize = remainingInputBlockSize(state);

            if (state.flint >= 0 && remainingBlockSize > state.flint) {
                remainingBlockSize = state.flint;
            }

            if (remainingBlockSize != 0 & state.availableIn != 0) {
                int copyInputSize = Math.min(remainingBlockSize, state.availableIn);
                copyInputToRingBuffer(state, copyInputSize, state.inputOffset); //next_in
                state.inputOffset += copyInputSize;
                state.availableIn -= copyInputSize;
                if (state.flint > 0) {
                    state.flint = state.flint - copyInputSize;
                }
                hasNewInput = true;
                continue;
            }

            if (injectFlushOrPushOutput(state)) {
                if (state.flint == Constant.BROTLI_FLINT_WAITING_FOR_FLUSHING) {
                    checkFlushComplete(state);
                    if (state.streamState == State.BrotliEncoderStreamState.BROTLI_STREAM_PROCESSING) {
                        state.flint = Constant.BROTLI_FLINT_DONE;
                    }
                }
                continue;
            }

            if (state.availableOut != 0 && hasNewInput) {
                isLast = state.availableIn == 0;
                boolean forceFlush = state.availableIn == 0;  // and some stream state
                if (!isLast && state.flint == 0) {
                    state.flint = Constant.BROTLI_FLINT_WAITING_FOR_FLUSHING;
                    forceFlush = true;
                }
                updateSizeHint(state);
                Compress.encodeData(state, isLast, forceFlush);
                hasNewInput = false;
                continue;
            }
            break;
        }
        flushToOutputStream(state);
        checkFlushComplete(state);
    }

    public static int inputBlockSize(State state) {
        return 1 << state.lgBlock;
    }

    public static int unprocessedInputSize(State state) {
        return state.inputOffset - state.lastProcessedPosition;
    }

    /**
     * Injects padding bits or pushes compressed data to output.
     * Returns false if nothing is done.
     */
    public static boolean injectFlushOrPushOutput(State state) throws BrotliException {

        if (state.availableOut == 0) {
            flushToOutputStream(state);
            return true;
        }

        if (state.lastFlushPosition < (state.storageBit >> 3) && state.availableOut > 0) {
            int copyLength = Math.min(state.availableOut, (state.storageBit >> 3) - state.lastFlushPosition);
            Utils.writeBuffer(state.storage, state.lastFlushPosition, copyLength, state.output, state.outputOffset);
            state.availableOut -= copyLength;
            state.outputOffset += copyLength;
            state.totalOut += copyLength;
            state.lastFlushPosition += copyLength;
            return true;
        }

        if (state.streamState == State.BrotliEncoderStreamState.BROTLI_STREAM_FINISHED
                && state.lastBytesBits != 0) {
            if (state.availableIn != 0) {
                LOGGER.log(Level.WARNING, "Flush : Stream finish state but still input data available");
            }
            state.output[state.outputOffset++] = (byte) state.lastBytes;
            state.availableOut--;
            state.lastBytesBits = 0;
            state.lastBytes = 0;
            return true;
        }

        return false;
    }

    /**
     * Choose which hash table will be used for compression.
     *
     * @param state     Current state.
     * @param inputSize Input data size.
     */
    public static void getHashTable(State state, int inputSize) {
        int maxTableSize = 1 << 15; //32768
        int htsize = hashTableSize(maxTableSize, inputSize);

        if (state.quality == 0) {
            if ((htsize & 0xAAAAA) == 0) {
                htsize <<= 1;
            }
        }

        if (htsize <= state.smallTable.length) {
            state.table = state.smallTable;
        } else {
            if (htsize > state.largeTableSize) {
                state.largeTableSize = htsize;
                state.largeTable = new int[htsize];
            }
            state.table = state.largeTable;
        }
        state.tableSize = htsize;
    }

    public static int hashTableSize(int maxTableSize, int inputSize) {
        int htsize = 256;
        while (htsize < maxTableSize && htsize < inputSize) {
            htsize <<= 1;
        }
        return htsize;
    }

    public static int findMatchLengthWithLimit(int[] input, int s1, int s2, int limit) {
        int matched = 0;
        int s2_limit = s2 + limit;
        int s2_ptr = s2;
        //TODO: Optimisation -- instead of looking byte by byte, read 4 by 4 {1 byte,2,3,4} == {1 byte,2,3,4}.
        while (s2_ptr < s2_limit && input[s1 + matched] == input[s2_ptr]) {
            ++s2_ptr;
            ++matched;
        }
        return matched;
    }

    public static int findMatchLengthWithLimit(int[] input1, int s1, int[] input2, int s2, int limit) {
        int matched = 0;
        int s2_limit = s2 + limit;
        if (s2_limit > input2.length) {
            LOGGER.log(Level.WARNING, "Out of bound detected, Compressor adapting itself");
            s2_limit = input2.length;
        }
        if (s1 > input1.length) {
            return 0;
        }
        while (s2 < s2_limit && input1[s1 + matched] == input2[s2]) {
            ++s2;
            ++matched;
            if ((s1 + matched) >= input1.length) {
                return matched;
            }
        }
        return matched;
    }

    /**
     * Write a meta-block header into Brotli stream.
     *
     * @param state          Current state
     * @param length         Meta-block length.
     * @param isUncompressed Is the data compressed into this block.
     * @throws BrotliException Exception if issue detected.
     */
    public static void brotliStoreMetaBlockHeader(State state,
                                                  int length,
                                                  boolean isUncompressed) throws BrotliException {
        int nibbles = 6;
        state.storageBit = BitWriter.writeBit(1, 0, state.storageBit, state.storage);
        if (length <= (1 << 16)) {
            nibbles = 4;
        } else if (length <= (1 << 20)) {
            nibbles = 5;
        }

        state.storageBit = BitWriter.writeBit(2, nibbles - 4, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(nibbles * 4, length - 1, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(1, isUncompressed, state.storageBit, state.storage);
    }

    /**
     * Builds a literal prefix code into "depths" and "bits" based on the statistics
     * of the "input" string and stores it into the bit stream.
     *
     * @param state     Current state.
     * @param input     Source data to build prefixe code from.
     * @param index     Index into input buffer.
     * @param inputSize Input size.
     * @param depths    Table containing depths from Huffman tree (prefix code length).
     * @param bits      Table containing bits of prefix codes.
     * @return Returns the literal ratio for the given input.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static int buildAndStoreLiteralPrefixCode(State state,
                                                     int[] input,
                                                     int index,
                                                     int inputSize,
                                                     int[] depths,
                                                     int[] bits) throws BrotliException {
        int[] histogram = new int[256];
        int histogram_total, i;

        if (inputSize < (1 << 15)) {
            for (i = 0; i < inputSize; ++i) {
                ++histogram[input[index + i]];
            }
            histogram_total = inputSize;
            for (i = 0; i < 256; ++i) {
                /* We weigh the first 11 samples with weight 3 to account for the
                   balancing effect of the LZ77 phase on the histogram. */
                int adjust = 2 * Math.min(histogram[i], 11);
                histogram[i] += adjust;
                histogram_total += adjust;
            }
        } else {
            int kSampleRate = 29;
            for (i = 0; i < inputSize; i += kSampleRate) {
                ++histogram[input[index + i]];
            }
            histogram_total = (inputSize + kSampleRate - 1) / kSampleRate;
            for (i = 0; i < 256; ++i) {
                /* We add 1 to each population count to avoid 0 bit depths (since this is
                   only a sample and we don't know if the symbol appears or not), and we
                   weigh the first 11 samples with weight 3 to account for the balancing
                   effect of the LZ77 phase on the histogram (more frequent symbols are
                   more likely to be in backward references instead as literals). */
                int adjust = 1 + 2 * Math.min(histogram[i], 11);
                histogram[i] += adjust;
                histogram_total += adjust;
            }
        }

        BitStreamManager.brotliBuildAndStoreHuffmanTreeFast(state, histogram, histogram_total,
                /* max_bits = */ 8,
                                                            depths, bits);

        int literal_ratio = 0;
        for (i = 0; i < 256; ++i) {
            if (histogram[i] != 0) {
                literal_ratio += histogram[i] * depths[i];
            }
        }
        /* Estimated encoding ratio, millibytes per symbol. */
        return (literal_ratio * 125) / histogram_total;
    }

    /**
     * Write the insert length code into the brotli stream.
     *
     * @param state     Current state.
     * @param insertlen Insert length value.
     * @param depth     Length of the prefix code.
     * @param bits      Actual prefix code bits.
     * @param histo     Histogram of command.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static void emitInsertLen(State state,
                                     int insertlen,
                                     int[] depth,
                                     int[] bits,
                                     int[] histo) throws BrotliException {
        if (insertlen < 6) {
            int code = insertlen + 40;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            ++histo[code];
        } else if (insertlen < 130) {
            int tail = insertlen - 2;
            int nbits = Utils.log2FloorNonZero(tail) - 1;
            int prefix = tail >> nbits;
            int inscode = (nbits << 1) + prefix + 42;
            state.storageBit = BitWriter.writeBit(depth[inscode], bits[inscode], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, (tail - (prefix << nbits)), state.storageBit, state.storage);
            ++histo[inscode];
        } else if (insertlen < 2114) {
            int tail = insertlen - 66;
            int nbits = Utils.log2FloorNonZero(tail);
            int code = nbits + 50;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, (tail - (1 << nbits)), state.storageBit, state.storage);
            ++histo[code];
        } else {
            state.storageBit = BitWriter.writeBit(depth[61], bits[61], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(12, (insertlen - 2114), state.storageBit, state.storage);
            ++histo[61];
        }
    }

    /**
     * Defines if encoder should emit uncompressed data.
     *
     * @param metaBlockStart Starting point of the meta-block.
     * @param nextEmit       Pointer to the next byte not covered by a copy.
     * @param insertLen      Insert length value.
     * @param literal_ratio  Literal ratio for current block.
     * @return true if the data should be uncompressed, false otherwise.
     */
    public static boolean shouldUseUncompressedMode(int metaBlockStart,
                                                    int nextEmit,
                                                    int insertLen,
                                                    int literal_ratio) {
        int compressed = nextEmit - metaBlockStart;
        if ((compressed * 50) > insertLen) {
            return false;
        } else {
            return literal_ratio > Constant.MIN_RATIO;
        }
    }

    /**
     * Emit an uncompressed meta-block into the brotli stream.
     *
     * @param state            Current state.
     * @param begin            Pointer to the beginning of the block.
     * @param end              Pointer to the end of the block.
     * @param storage_ix_start Pointer to the starting bit of the block.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static void emitUncompressedMetaBlock(State state,
                                                 int begin,
                                                 int end,
                                                 int storage_ix_start) throws BrotliException {
        int len = end - begin;
        rewindBitPosition(state, storage_ix_start);
        brotliStoreMetaBlockHeader(state, len, true);
        state.storageBit = (state.storageBit + 7) & ~7;
        Utils.writeBuffer(state.inputBuffer, begin, len, state.storage, state.storageBit >> 3);
        state.storageBit += len << 3;
        state.storage[state.storageBit >> 3] = 0;
    }

    /**
     * Moves the storage pointer to the new destination.
     *
     * @param state          Current state.
     * @param new_storage_ix Pointer destination.
     */
    public static void rewindBitPosition(State state, int new_storage_ix) {
        int bitpos = new_storage_ix & 7;
        int mask = (1 << bitpos) - 1;
        state.storage[new_storage_ix >> 3] &= mask;
        state.storageBit = new_storage_ix;
    }

    /**
     * Write the long insert length code into the brotli stream.
     *
     * @param state     Current state.
     * @param insertlen Insert length value.
     * @param depth     Length of the prefix code.
     * @param bits      Actual prefix code bits.
     * @param histo     Histogram of command.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static void emitLongInsertLen(State state,
                                         int insertlen,
                                         int[] depth,
                                         int[] bits,
                                         int[] histo) throws BrotliException {
        if (insertlen < 22594) {
            state.storageBit = BitWriter.writeBit(depth[62], bits[62], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(14, (insertlen - 6210), state.storageBit, state.storage);
            ++histo[62];
        } else {
            state.storageBit = BitWriter.writeBit(depth[63], bits[63], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(24, (insertlen - 22594), state.storageBit, state.storage);
            ++histo[63];
        }
    }

    /**
     * Emit literal bytes into the brotli stream.
     *
     * @param state       Current state.
     * @param start_index Offset into input data buffer.
     * @param input       Input data buffer.
     * @param len         Length to be emitted.
     * @param depths      Table containing length of the prefix code.
     * @param bits        Table containing prefix code bits.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static void emitLiterals(State state, int start_index, int[] input, int len, int[] depths,
                                    int[] bits) throws BrotliException {
        for (int j = start_index; j < (start_index + len); j++) {
            int lit = input[j];
            state.storageBit = BitWriter.writeBit(depths[lit], bits[lit], state.storageBit, state.storage);
        }
    }

    /**
     * Write distance prefix code into brotli stream.
     *
     * @param state    Current state.
     * @param distance Distance value.
     * @param depth    Length of distance prefix code.
     * @param bits     Prefix code value.
     * @param histo    Command histogram.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static void emitDistance(State state, int distance, int[] depth, int[] bits,
                                    int[] histo) throws BrotliException {
        int d = distance + 3;
        int nbits = Utils.log2FloorNonZero(d) - 1;
        int prefix = (d >> nbits) & 1;
        int offset = (2 + prefix) << nbits;
        int distcode = 2 * (nbits - 1) + prefix + 80;
        state.storageBit = BitWriter.writeBit(depth[distcode], bits[distcode], state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(nbits, (d - offset), state.storageBit, state.storage);
        ++histo[distcode];
    }

    public static void emitCopyLenLastDistance(State state, int copylen, int[] depth, int[] bits,
                                               int[] histo) throws BrotliException {
        if (copylen < 12) {
            state.storageBit = BitWriter.writeBit(depth[copylen - 4], bits[copylen - 4], state.storageBit, state.storage);
            ++histo[copylen - 4];
        } else if (copylen < 72) {
            int tail = copylen - 8;
            int nbits = Utils.log2FloorNonZero(tail) - 1;
            int prefix = tail >> nbits;
            int code = (nbits << 1) + prefix + 4;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, (tail - (prefix << nbits)), state.storageBit, state.storage);
            ++histo[code];
        } else if (copylen < 136) {
            int tail = copylen - 8;
            int code = (tail >> 5) + 30;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(5, (tail & 31), state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(depth[64], bits[64], state.storageBit, state.storage);
            ++histo[code];
            ++histo[64];
        } else if (copylen < 2120) {
            int tail = copylen - 72;
            int nbits = Utils.log2FloorNonZero(tail);
            int code = nbits + 28;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, (tail - (1 << nbits)), state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(depth[64], bits[64], state.storageBit, state.storage);
            ++histo[code];
            ++histo[64];
        } else {
            state.storageBit = BitWriter.writeBit(depth[39], bits[39], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(24, (copylen - 2120), state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(depth[64], bits[64], state.storageBit, state.storage);
            ++histo[39];
            ++histo[64];
        }
    }

    public static int hashBytesAtOffset(long v, int offset, int shift) {
        if (offset > 3 || offset < 0) {
            LOGGER.log(Level.WARNING, "Hash bytes at offset : Offset corrupted");
        }
        long h = ((v >> (8 * offset)) << 24) * Constant.kHashMul32;
        return (int) (h >> shift);
    }

    public static void emitCopyLen(State state, int copylen, int[] depth, int[] bits, int[] histo) throws BrotliException {
        if (copylen < 10) {
            state.storageBit = BitWriter.writeBit(depth[copylen + 14], bits[copylen + 14], state.storageBit, state.storage);
            ++histo[copylen + 14];
        } else if (copylen < 134) {
            int tail = copylen - 6;
            int nbits = Utils.log2FloorNonZero(tail) - 1;
            int prefix = tail >> nbits;
            int code = (nbits << 1) + prefix + 20;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, (tail - (prefix << nbits)), state.storageBit, state.storage);
            ++histo[code];
        } else if (copylen < 2118) {
            int tail = copylen - 70;
            int nbits = Utils.log2FloorNonZero(tail);
            int code = nbits + 28;
            state.storageBit = BitWriter.writeBit(depth[code], bits[code], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, (tail - (1 << nbits)), state.storageBit, state.storage);
            ++histo[code];
        } else {
            state.storageBit = BitWriter.writeBit(depth[39], bits[39], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(24, (copylen - 2118), state.storageBit, state.storage);
            ++histo[39];
        }
    }

    public static boolean shouldMergeBlock(int[] data, int index, int len, int[] depths) {

        int[] histo = new int[256];
        int kSampleRate = 43;
        int i;
        for (i = 0; i < len; i += kSampleRate) {
            ++histo[data[index + i]];
        }
        int total = (len + kSampleRate - 1) / kSampleRate;
        double r = (Utils.fastLog2(total) + 0.5) * (double) total + 200;
        for (i = 0; i < 256; ++i) {
            r -= (double) histo[i] * (depths[i] + Utils.fastLog2(histo[i]));
        }
        return r >= 0.0;
    }

    public static void updateBits(int n_bits, int bits, int pos, int[] array) {
        while (n_bits > 0) {
            int byte_pos = pos >> 3;
            int n_unchanged_bits = pos & 7;
            int n_changed_bits = Math.min(n_bits, 8 - n_unchanged_bits);
            int total_bits = n_unchanged_bits + n_changed_bits;
            int mask = (-(1 << total_bits)) | ((1 << n_unchanged_bits) - 1);
            int unchanged_bits = array[byte_pos] & mask;
            int changed_bits = bits & ((1 << n_changed_bits) - 1);
            array[byte_pos] = ((changed_bits << n_unchanged_bits) | unchanged_bits);
            n_bits -= n_changed_bits;
            bits >>= n_changed_bits;
            pos += n_changed_bits;
        }
    }

    /**
     * Builds a command and distance prefix code (each 64 symbols) into "depth" and
     * "bits" based on "histogram" and stores it into the bit stream.
     *
     * @param histogram Command histogram.
     * @param depth     Table containing length of prefix code.
     * @param bits      Table containing bits of prefix code.
     * @param indexBit  Pointer to storage bit.
     * @param storage   storage buffer further copied to brotli stream.
     * @return indexBit + number of bit written.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    public static int buildAndStoreCommandPrefixCode(int[] histogram, int[] depth, int[] bits,
                                                     int indexBit, int[] storage) throws BrotliException {

        /* Tree size for building a tree over 64 symbols is 2 * 64 + 1. */
        HuffmanTree[] tree = new HuffmanTree[129];
        for (int i = 0; i < 129; i++) {
            tree[i] = new HuffmanTree();
        }
        int[] cmd_depth = new int[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
        int[] cmd_bits = new int[64];

        Huffman.brotliCreateHuffmanTree(histogram, 0, 64, 15, tree, depth, 0);
        Huffman.brotliCreateHuffmanTree(histogram, 64, 64, 14, tree, depth, 64);
        /* We have to jump through a few hoops here in order to compute
           the command bits because the symbols are in a different order than in
           the full alphabet. This looks complicated, but having the symbols
           in this order in the command bits saves a few branches in the Emit*
           functions. */
        Utils.copyBytes(cmd_depth, depth, 24);
        Utils.copyBytes(cmd_depth, 24, depth, 40, 8);
        Utils.copyBytes(cmd_depth, 32, depth, 24, 8);
        Utils.copyBytes(cmd_depth, 40, depth, 48, 8);
        Utils.copyBytes(cmd_depth, 48, depth, 32, 8);
        Utils.copyBytes(cmd_depth, 56, depth, 56, 8);
        Huffman.brotliConvertBitDepthsToSymbols(cmd_depth, 64, cmd_bits, 0);
        Utils.copyBytes(bits, cmd_bits, 48);
        Utils.copyBytes(bits, 24, cmd_bits, 32, 8);
        Utils.copyBytes(bits, 32, cmd_bits, 48, 8);
        Utils.copyBytes(bits, 40, cmd_bits, 24, 8);
        Utils.copyBytes(bits, 48, cmd_bits, 40, 8);
        Utils.copyBytes(bits, 56, cmd_bits, 56, 8);
        Huffman.brotliConvertBitDepthsToSymbols(depth, 64, bits, 64);

        /* Create the bit length array for the full command alphabet. */
        Utils.copyBytes(cmd_depth, new int[64], 64);  /* only 64 first values were used */
        Utils.copyBytes(cmd_depth, depth, 8);
        Utils.copyBytes(cmd_depth, 64, depth, 8, 8);
        Utils.copyBytes(cmd_depth, 128, depth, 16, 8);
        Utils.copyBytes(cmd_depth, 192, depth, 24, 8);
        Utils.copyBytes(cmd_depth, 384, depth, 32, 8);
        for (int i = 0; i < 8; ++i) {
            cmd_depth[128 + 8 * i] = depth[40 + i];
            cmd_depth[256 + 8 * i] = depth[48 + i];
            cmd_depth[448 + 8 * i] = depth[56 + i];
        }
        indexBit = BitStreamManager.brotliStoreHuffmanTree(
                cmd_depth, Constant.BROTLI_NUM_COMMAND_SYMBOLS, tree, 0, indexBit, storage);
        indexBit = BitStreamManager.brotliStoreHuffmanTree(
                depth, 64, tree, 64, indexBit, storage);

        return indexBit;
    }

    //TODO: Does it really belong here ?
    public static void close(State state) throws IOException {
        state.outputStream.close();
        if (state.inputOffset != 0 &&
                state.streamState != State.BrotliEncoderStreamState.BROTLI_STREAM_FINISHED) {
            LOGGER.log(Level.WARNING, "Add waiting loop, still input uncompressed");
        }
        state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_FINISHED;
    }

    static int hash(State state, int p, int shift) {
        long h = (Utils.get64Bits(state.inputBuffer, p) << 24) * Constant.kHashMul32;
        return (int) (h >> shift) & 0xFF;
    }

    private static void updateSizeHint(State state) {
        if (state.sizeHint == 0) {
            int delta = unprocessedInputSize(state);
            int tail = state.availableIn;
            int limit = 1 << 30;
            int total;
            if ((delta >= limit) || (tail >= limit) || ((delta + tail) >= limit)) {
                total = limit;
            } else {
                total = (delta + tail);
            }
            state.sizeHint = total;
        }
    }

    private static void copyInputToRingBuffer(State state, int inputSize, int offset) throws BrotliException {
        RingBuffer.writeRingBuffer(state.inputBuffer, offset, inputSize);
        int[] buffer = RingBuffer.getBuffer();
        if (RingBuffer.getPosition() <= RingBuffer.getMask()) {
            for (int i = 0; i < 7; i++) {
                buffer[RingBuffer.getPosition() + 2 + i] = 0;
            }
        }
    }

    private static int remainingInputBlockSize(State state) {
        int delta = unprocessedInputSize(state);
        int blockSize = inputBlockSize(state);
        if (delta >= blockSize) {
            return 0;
        }
        return blockSize - delta;
    }

    /**
     * Compression for quality 0.
     *
     * @param state     Current encoder state.
     * @param lastBlock Is it the last block to be emitted ?
     * @throws BrotliException Throws BrotliException if error detected.
     */
    private static void brotliCompressStreamFast(State state, boolean lastBlock) throws BrotliException {

        if (state.quality != 0) {
            throw new BrotliException("Error: wrong quality");
        }

        if (state.availableIn == 0) {
            state.storage = new int[1];
            state.storageBit = BitWriter.writeBit(8, 51, state.storageBit, state.storage);
            injectFlushOrPushOutput(state);
            flushToOutputStream(state);
            return;
        }

        while (true) {
            if (injectFlushOrPushOutput(state)) {
                continue;
            }

            if (state.availableIn != 0) {
                int blockSize = Math.min(1 << state.window, state.availableIn);
                boolean isLast = state.availableIn == blockSize && lastBlock;
                boolean forceFlush = state.availableIn == blockSize;
                int maxOutputSize = 2 * blockSize + 503;
                state.storage = new int[maxOutputSize];
                state.availableStorage = maxOutputSize;
                state.storage[0] = state.lastBytes & 255;
                state.storage[1] = state.lastBytes >> 8;
                state.storageBit = state.lastBytesBits;
                state.lastFlushPosition = 0;
                if (forceFlush && blockSize == 0) {
                    state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_FLUSH_REQUESTED;
                    continue;
                }
                getHashTable(state, blockSize);

                brotliCompressFragmentFast(state,
                                           blockSize,
                                           isLast);

                if (blockSize != 0) {
                    state.inputOffset += blockSize;
                    state.availableIn -= blockSize;
                }
                state.lastBytes = Utils.get16Bits(state.storage, (state.storageBit >> 3));
                state.lastBytesBits = state.storageBit & 7;

                if (forceFlush) {
                    state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_FLUSH_REQUESTED;
                }
                if (isLast) {
                    state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_FINISHED;
                }
                continue;
            }
            break;
        }
        flushToOutputStream(state);
        checkFlushComplete(state);
    }

    /**
     * Check that state is properly initialized.
     *
     * @param state state instance to be verified.
     * @return true if everything is correct.
     * @throws BrotliException Throws BrotliException if issue detected.
     */
    private static boolean checkInitialization(State state) throws BrotliException {
        if (state.isInitialized) {
            return true;
        }

        state.lastBytesBits = 0;
        state.lastBytes = 0;
        state.flint = Constant.BROTLI_FLINT_DONE;
        state.remainingMetadataBytes = Integer.MAX_VALUE;

        if (state.streamOffset != 0) {
            state.flint = Constant.BROTLI_FLINT_NEEDS_2_BYTES;
            state.distCache[0] = -16;
            state.distCache[1] = -16;
            state.distCache[2] = -16;
            state.distCache[3] = -16;
            Utils.copyBytes(state.savedDistCache, state.distCache, 4);
        }

        defineWindowBit(state);

        if (state.outputOffset == 0) {
            if (state.quality == 0) {
                state.window = Math.min(Constant.BROTLI_MAX_WINDOW_BITS,
                                        Math.max(state.window, 18));
                initCommandPrefixCodes(state);
            }

            if (state.quality == 10) {
                state.lgBlock = computeLgBlock(state);
                chooseDistanceParam(state);
                RingBuffer.ringBufferSetUp(state);
            }

            encodeWindowBit(state);
        }
        state.isInitialized = true;
        return true;
    }

    private static void chooseDistanceParam(State state) {
        int distance_postfix_bits = 0;
        int num_direct_distance_codes = 0;
        int alphabet_size_max;
        /*
        if (Mode font) {
            distance_postfix_bits = 1;
            num_direct_distance_codes = 12;
        }
         */
        state.distance.distancePostfixBits = distance_postfix_bits;
        state.distance.numDirectDistanceCodes = num_direct_distance_codes;
        alphabet_size_max = Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + num_direct_distance_codes +
                (Constant.BROTLI_MAX_DISTANCE_BITS << (distance_postfix_bits + 1));
        state.distance.alphabetSizeMax = alphabet_size_max;
        state.distance.alphabetSizeLimit = alphabet_size_max;
        state.distance.maxDistance =
                num_direct_distance_codes + (1 << (Constant.BROTLI_MAX_DISTANCE_BITS + distance_postfix_bits + 2))
                        - (1 << (distance_postfix_bits + 2));
    }

    private static int computeLgBlock(State state) {
        return state.window > state.lgBlock ? Math.min(18, state.window) : 16;
    }

    /**
     * Choose the window size to be used during compression.
     * If the user chose it, make sure it has valid value.
     * Otherwise, defined by encoder.
     *
     * @param state Current state.
     */
    private static void defineWindowBit(State state) {
        //Window chosen by user
        if (state.window > 0) {
            state.window = Math.min(Constant.BROTLI_MAX_WINDOW_BITS,
                                    Math.max(Constant.BROTLI_MIN_WINDOW_BITS, state.window));
            return;
        }
        //Computation by encoder
        state.window = 10;
        if (state.availableIn >= 0) {
            while (Utils.brotliMaxBackwardLimit(state.window) < state.availableIn) {
                state.window++;
                if (state.window == Constant.BROTLI_MAX_WINDOW_BITS) {
                    break;
                }
            }
        }
    }

    private static void initCommandPrefixCodes(State state) throws BrotliException {
        int[] kDefaultCommandDepths = {
                0, 4, 4, 5, 6, 6, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8,
                0, 0, 0, 4, 4, 4, 4, 4, 5, 5, 6, 6, 6, 6, 7, 7,
                7, 7, 10, 10, 10, 10, 10, 10, 0, 4, 4, 5, 5, 5, 6, 6,
                7, 8, 8, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
                5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                6, 6, 6, 6, 6, 6, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4,
                4, 4, 4, 5, 5, 5, 5, 5, 5, 6, 6, 7, 7, 7, 8, 10,
                12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
        };
        int[] kDefaultCommandBits = {
                0, 0, 8, 9, 3, 35, 7, 71,
                39, 103, 23, 47, 175, 111, 239, 31,
                0, 0, 0, 4, 12, 2, 10, 6,
                13, 29, 11, 43, 27, 59, 87, 55,
                15, 79, 319, 831, 191, 703, 447, 959,
                0, 14, 1, 25, 5, 21, 19, 51,
                119, 159, 95, 223, 479, 991, 63, 575,
                127, 639, 383, 895, 255, 767, 511, 1023,
                14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                27, 59, 7, 39, 23, 55, 30, 1, 17, 9, 25, 5, 0, 8, 4, 12,
                2, 10, 6, 21, 13, 29, 3, 19, 11, 15, 47, 31, 95, 63, 127, 255,
                767, 2815, 1791, 3839, 511, 2559, 1535, 3583, 1023, 3071, 2047, 4095,
        };
        int[] kDefaultCommandCode = {
                0xff, 0x77, 0xd5, 0xbf, 0xe7, 0xde, 0xea, 0x9e, 0x51, 0x5d, 0xde, 0xc6,
                0x70, 0x57, 0xbc, 0x58, 0x58, 0x58, 0xd8, 0xd8, 0x58, 0xd5, 0xcb, 0x8c,
                0xea, 0xe0, 0xc3, 0x87, 0x1f, 0x83, 0xc1, 0x60, 0x1c, 0x67, 0xb2, 0xaa,
                0x06, 0x83, 0xc1, 0x60, 0x30, 0x18, 0xcc, 0xa1, 0xce, 0x88, 0x54, 0x94,
                0x46, 0xe1, 0xb0, 0xd0, 0x4e, 0xb2, 0xf7, 0x04, 0x00,
        };
        Utils.writeBuffer(kDefaultCommandDepths, 0, kDefaultCommandDepths.length, state.cmdDepth, 0);
        Utils.writeBuffer(kDefaultCommandBits, 0, kDefaultCommandBits.length, state.cmdBits, 0);
        Utils.writeBuffer(kDefaultCommandCode, 0, kDefaultCommandCode.length, state.cmdCode, 0);

        state.cmdCodeNumbits = 448;
    }

    /**
     * Encode window bit into brotli stream.
     *
     * @param state Current state.
     */
    private static void encodeWindowBit(State state) {
        if (state.window == 16) {
            state.lastBytes = 0;
            state.lastBytesBits = 1;
        } else if (state.window == 17) {
            state.lastBytes = 1;
            state.lastBytesBits = 7;
        } else if (state.window > 17) {
            state.lastBytes = (((state.window - 17) << 1) | 0x01);
            state.lastBytesBits = 4;
        } else {
            state.lastBytes = (((state.window - 8) << 4) | 0x01);
            state.lastBytesBits = 7;
        }
    }

    //TODO: Make sure it is correct behavior for quality 10.
    private static void checkFlushComplete(State state) {
        if (state.availableIn != 0) {
            LOGGER.log(Level.ERROR, "Flush is uncomplete");
            return;
        }
        state.loadIx = 0;
        state.inputOffset = 0;
        state.streamState = State.BrotliEncoderStreamState.BROTLI_STREAM_FINISHED;
    }

    private static void flushToOutputStream(State state) {
        Utils.writeToOutputStream(state);
    }

    /**
     * Compress the inputBuffer data.
     * If the data compressed if bigger than the a brotli stream with uncompressed data,
     * than fallback to uncompressed stream.
     *
     * @param state     Current state.
     * @param blockSize Block size to be compressed.
     * @param isLast    true if it is the last block to be compressed.
     * @throws BrotliException throws BrotliException if issue detected.
     */
    private static void brotliCompressFragmentFast(State state, int blockSize, boolean isLast) throws BrotliException {
        int initialStorageBit = state.storageBit;
        int initialInputPosition = state.inputOffset;
        boolean triedCompression = false;
        int[] tableValues = {9, 11, 13, 15};

        if (blockSize == 0) {
            state.storageBit = BitWriter.writeBit(2, 3, state.storageBit, state.storage);
            return;
        }

        for (int tableValue : tableValues) {
            if (tableValue == Utils.log2FloorNonZero(state.tableSize)) {
                brotliCompressFragmentFastImpl(state, blockSize, isLast);
                triedCompression = true;
                break;
            }
        }

        if (state.storageBit - initialStorageBit > 31 + (blockSize << 3) || !triedCompression) {
            emitUncompressedMetaBlock(
                    state, initialInputPosition, initialInputPosition + blockSize, initialStorageBit);
        }

        if (isLast) {
            state.storageBit = BitWriter.writeBit(2, 3, state.storageBit, state.storage);
        }

    }

    /**
     * Actual Brotli compression for quality 0.
     *
     * @param state     Current state.
     * @param inputSize Block size to be compressed.
     * @param isLast    true if it is the last block to be compressed.
     * @throws BrotliException throws BrotliException if issue detected.
     */
    private static void brotliCompressFragmentFastImpl(State state, int inputSize, boolean isLast) throws BrotliException {

        final int kFirstBlockSize = 3 << 15; //98304
        final int kMergeBlockSize = 1 << 16; //65536

        int[] cmdHisto = new int[128];
        int input = state.inputOffset;
        int ip = input;
        int ipEnd = input;
        int baseIp = input;
        int nextEmit = input;
        int metaBlockStart = input;

        int kInputMarginBytes = Constant.BROTLI_WINDOW_GAP;
        int kMinMatchLen = 5;

        int blockSize = Math.min(inputSize, kFirstBlockSize);
        int totalBlockSize = blockSize;
        int mlenStorageIx = state.storageBit + 3;

        int[] litDepth = new int[256];
        int[] litBits = new int[256];

        final int shift = 64 - Utils.log2FloorNonZero(state.tableSize);

        brotliStoreMetaBlockHeader(state, blockSize, false);
        state.storageBit = BitWriter.writeBit(13, 0, state.storageBit, state.storage);

        int literalRatio = buildAndStoreLiteralPrefixCode(state, state.inputBuffer, input, blockSize,
                                                          litDepth, litBits);

        for (int i = 0; i + 7 < state.cmdCodeNumbits; i += 8) {
            state.storageBit = BitWriter.writeBit(8, state.cmdCode[i >> 3], state.storageBit, state.storage);
        }

        state.storageBit = BitWriter.writeBit(state.cmdCodeNumbits & 7,
                                              state.cmdCode[state.cmdCodeNumbits >> 3], state.storageBit, state.storage);

        int lenLimit;
        int ipLimit = 0;
        int nextHash = 0;
        int nextIp = 0;
        int skip = 0;
        int candidate;
        int lastDistance = 0;

        state.compressState = State.BrotliEncoderCompressState.EMIT_COMMANDS;
        main_loop:
        while (true) {
            switch (state.compressState) {
            case EMIT_COMMANDS:
                System.arraycopy(Tables.kCmdHistoSeed, 0, cmdHisto, 0, Tables.kCmdHistoSeed.length);
                lastDistance = -1;
                ip = input;
                ipEnd = input + blockSize;

                if (blockSize >= kInputMarginBytes) {
                    lenLimit = Math.min(blockSize - kMinMatchLen, inputSize - kInputMarginBytes);
                    ipLimit = input + lenLimit;
                    nextHash = hash(state, ++ip, shift);

                    state.compressState = State.BrotliEncoderCompressState.FOR;
                    continue main_loop;
                }
                state.compressState = State.BrotliEncoderCompressState.EMIT_REMAINDER;
                continue main_loop;

            case FOR:
                skip = 32;
                nextIp = ip;
                if (nextEmit >= ip) {
                    LOGGER.log(Level.WARNING, "FOR : nextEmit is higher than ip");
                }
                state.compressState = State.BrotliEncoderCompressState.TRAWL;
                continue main_loop;

            case TRAWL:
                do {
                    int hash = nextHash;
                    int bytesBetweenHashLookups = (skip++ >> 5);

                    if (hash != hash(state, nextIp, shift)) {
                        LOGGER.log(Level.WARNING, "Hash does not match");
                    }

                    ip = nextIp;
                    nextIp = ip + bytesBetweenHashLookups;
                    if (!(nextIp > ipLimit)) {
                        state.compressState = State.BrotliEncoderCompressState.EMIT_REMAINDER;
                        continue main_loop;
                    }
                    nextHash = hash(state, nextIp, shift);
                    candidate = ip - lastDistance;
                    if (isMatch(state, ip, candidate)) {
                        if (candidate < ip) {
                            state.table[hash] = ip - baseIp;
                            break;
                        }
                    }
                    candidate = baseIp + state.table[hash];

                    if ((candidate >= baseIp) && (candidate < ip)) {
                        LOGGER.log(Level.WARNING, "TRAWL : Candidate should be after baseIp and before ip");
                        state.compressState = State.BrotliEncoderCompressState.EMIT_REMAINDER;
                        continue main_loop;
                    }

                    state.table[hash] = ip - baseIp;
                } while (!isMatch(state, ip, candidate));

                if (ip - candidate > Constant.MAX_DISTANCE) {
                    state.compressState = State.BrotliEncoderCompressState.TRAWL;
                    continue main_loop;
                }

                int base = ip;
                int matched = 5 + findMatchLengthWithLimit(state.inputBuffer, candidate + 5, ip + 5, ipEnd - ip - 5);
                int distance = base - candidate;
                int insert = (base - nextEmit);
                ip += matched;

                if (insert < 6210) {
                    emitInsertLen(state, insert, state.cmdDepth, state.cmdBits, cmdHisto);
                } else if (shouldUseUncompressedMode(metaBlockStart, nextEmit, insert, literalRatio)) {
                    emitUncompressedMetaBlock(state, metaBlockStart, base, mlenStorageIx - 3);
                    inputSize -= base - input;
                    input = base;
                    nextEmit = input;
                    state.compressState = State.BrotliEncoderCompressState.NEXT_BLOCK;
                    continue main_loop;
                } else {
                    emitLongInsertLen(state, insert, state.cmdDepth, state.cmdBits, cmdHisto);
                }
                emitLiterals(state, nextEmit, state.inputBuffer, insert, litDepth, litBits);
                if (distance == lastDistance) {
                    state.storageBit = BitWriter.writeBit(state.cmdDepth[64], state.cmdBits[64],
                                                          state.storageBit, state.storage);
                    ++cmdHisto[64];
                } else {
                    emitDistance(state, distance, state.cmdDepth, state.cmdBits, cmdHisto);
                    lastDistance = distance;
                }
                emitCopyLenLastDistance(state, matched, state.cmdDepth, state.cmdBits, cmdHisto);

                nextEmit = ip;
                if (!(ip >= ipLimit)) {
                    state.compressState = State.BrotliEncoderCompressState.EMIT_REMAINDER;
                    continue main_loop;
                }

                long inputBytes64 = Utils.get64Bits(state.inputBuffer, ip - 3);
                int prev_hash = hashBytesAtOffset(inputBytes64, 0, shift);
                int cur_hash = hashBytesAtOffset(inputBytes64, 3, shift);
                if ((ip - baseIp - 3) < 0) {
                    LOGGER.log(Level.WARNING, "table contains negative value");
                }
                state.table[prev_hash] = (ip - baseIp - 3);
                prev_hash = hashBytesAtOffset(inputBytes64, 1, shift);
                state.table[prev_hash] = (ip - baseIp - 2);
                prev_hash = hashBytesAtOffset(inputBytes64, 2, shift);
                state.table[prev_hash] = (ip - baseIp - 1);

                candidate = baseIp + state.table[cur_hash];
                state.table[cur_hash] = (ip - baseIp);

                while (isMatch(state, ip, candidate)) {

                    base = ip;
                    matched = 5 + findMatchLengthWithLimit(
                            state.inputBuffer, candidate + 5, ip + 5, (ipEnd - ip) - 5);
                    if (ip - candidate > Constant.MAX_DISTANCE) {
                        break;
                    }
                    ip += matched;
                    lastDistance = (base - candidate);

                    emitCopyLen(state, matched, state.cmdDepth, state.cmdBits, cmdHisto);
                    emitDistance(state, lastDistance, state.cmdDepth, state.cmdBits,
                                 cmdHisto);
                    nextEmit = ip;
                    if (!(ip >= ipLimit)) {
                        state.compressState = State.BrotliEncoderCompressState.EMIT_REMAINDER;
                        continue main_loop;
                    }

                    inputBytes64 = Utils.get64Bits(state.inputBuffer, ip - 3);
                    prev_hash = hashBytesAtOffset(inputBytes64, 0, shift);
                    cur_hash = hashBytesAtOffset(inputBytes64, 3, shift);
                    state.table[prev_hash] = (ip - baseIp - 3);
                    prev_hash = hashBytesAtOffset(inputBytes64, 1, shift);
                    state.table[prev_hash] = (ip - baseIp - 2);
                    prev_hash = hashBytesAtOffset(inputBytes64, 2, shift);
                    state.table[prev_hash] = (ip - baseIp - 1);

                    candidate = baseIp + state.table[cur_hash];
                    state.table[cur_hash] = (ip - baseIp);
                }
                state.compressState = State.BrotliEncoderCompressState.FOR;
                continue main_loop;

            case EMIT_REMAINDER:
                if (nextEmit > ipEnd) {
                    LOGGER.log(Level.WARNING, "EMIT REMAINDER: next emit is higher then ip end");
                }
                input += blockSize;
                inputSize -= blockSize;
                blockSize = Math.min(inputSize, kMergeBlockSize);

                if (inputSize > 0 && totalBlockSize + blockSize <= (1 << 20) &&
                        shouldMergeBlock(state.inputBuffer, input, blockSize, litDepth)) {
                    if (totalBlockSize <= (1 << 16)) {
                        LOGGER.log(Level.WARNING, "CompressData : total block size too small, should not merge");
                    }
                    totalBlockSize += blockSize;
                    updateBits(20, totalBlockSize - 1, mlenStorageIx, state.storage);

                    state.compressState = State.BrotliEncoderCompressState.EMIT_COMMANDS;
                    continue main_loop;
                }
                if (nextEmit < ipEnd) {
                    int insert_ = ipEnd - nextEmit;
                    if (insert_ < 6210) {
                        emitInsertLen(state, insert_, state.cmdDepth, state.cmdBits, cmdHisto);
                        emitLiterals(state, nextEmit, state.inputBuffer, insert_, litDepth, litBits);
                    } else if (shouldUseUncompressedMode(metaBlockStart, nextEmit, insert_, literalRatio)) {
                        emitUncompressedMetaBlock(state, metaBlockStart, ipEnd, mlenStorageIx - 3);
                    } else {
                        emitLongInsertLen(state, insert_, state.cmdDepth, state.cmdBits, cmdHisto);
                        emitLiterals(state, nextEmit, state.inputBuffer, insert_, litDepth, litBits);
                    }
                }
                nextEmit = ipEnd;
                state.compressState = State.BrotliEncoderCompressState.NEXT_BLOCK;
                continue main_loop;

            case NEXT_BLOCK:
                if (inputSize > 0) {
                    metaBlockStart = input;
                    blockSize = Math.min(inputSize, kFirstBlockSize);
                    totalBlockSize = blockSize;
                    mlenStorageIx = state.storageBit + 3;
                    brotliStoreMetaBlockHeader(state, blockSize, false);
                    state.storageBit = BitWriter.writeBit(13, 0, state.storageBit, state.storage);
                    literalRatio = buildAndStoreLiteralPrefixCode(state,
                                                                  state.inputBuffer, input, blockSize, litDepth, litBits);
                    state.storageBit = buildAndStoreCommandPrefixCode(cmdHisto,
                                                                      state.cmdDepth,
                                                                      state.cmdBits,
                                                                      state.storageBit,
                                                                      state.storage);
                    state.compressState = State.BrotliEncoderCompressState.EMIT_COMMANDS;
                    continue main_loop;
                }
                break main_loop;

            default:
                throw new BrotliException("Wrong compress state");
            }
        }

        if (!isLast) {
            state.cmdCode[0] = 0;
            state.cmdCodeNumbits = 0;
            state.cmdCodeNumbits = buildAndStoreCommandPrefixCode(cmdHisto, state.cmdDepth,
                                                                  state.cmdBits, state.cmdCodeNumbits, state.cmdCode);
        }
    }

    private static boolean isMatch(State state, int ip, int candidate) {
        if ((ip + 4) < state.inputBuffer.length && (candidate + 4) < state.inputBuffer.length) {
            return Utils.get32Bits(state.inputBuffer, ip) == Utils.get32Bits(state.inputBuffer, candidate)
                    && state.inputBuffer[ip + 4] == state.inputBuffer[candidate + 4];
        } else {
            LOGGER.log(Level.WARNING, "isMatch : Candidate + 4 out of bound");
            return Utils.get32Bits(state.inputBuffer, ip) == Utils.get32Bits(state.inputBuffer, candidate);
        }
    }

}
