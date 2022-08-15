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

import java.lang.System.Logger.Level;

/**
 * Huffman computation methods.
 */
class Huffman {
    private static final System.Logger LOGGER = System.getLogger(Huffman.class.getName());

    private static boolean useRleForNonZero;
    private static boolean useRleForZero;

    public static void brotliCreateHuffmanTree(int[] data, int dataIx, int length, int treeLimit,
                                               HuffmanTree[] tree, int[] depth, int depthIx) throws BrotliException {
        for (int countLimit = 1; ; countLimit *= 2) {
            int n = 0;
            int j;
            int i;
            int k;
            for (i = length; i != 0; ) {
                --i;
                if (data[dataIx + i] != 0) {
                    int count = Math.max(data[dataIx + i], countLimit);
                    HuffmanTree.initHuffmanTree(tree[n++], count, -1, i);
                }
            }

            if (n == 1) {
                depth[depthIx + tree[0].getIndexRightOrValue()] = 1;
                break;
            }

            sortHuffmanTreeItems(tree, n);

            tree[n] = new Sentinel();
            tree[n + 1] = new Sentinel();

            i = 0;
            j = n + 1;
            for (k = n - 1; k != 0; --k) {
                int left, right;
                if (tree[i].getTotalCount() <= tree[j].getTotalCount()) {
                    left = i;
                    ++i;
                } else {
                    left = j;
                    ++j;
                }
                if (tree[i].getTotalCount() <= tree[j].getTotalCount()) {
                    right = i;
                    ++i;
                } else {
                    right = j;
                    ++j;
                }

                int jEnd = 2 * n - k;
                tree[jEnd].setTotalCount(tree[left].getTotalCount() + tree[right].getTotalCount());
                tree[jEnd].setIndexLeft(left);
                tree[jEnd].setIndexRightOrValue(right);

                tree[jEnd + 1] = new Sentinel();

            }
            if (brotliSetDepth((2 * n - 1), tree, depth, treeLimit, depthIx)) {
                break;
            }
        }
    }

    public static void sortHuffmanTreeItems(HuffmanTree[] items, int n) {
        if (n < 13) {
            //Insertion sort
            for (int i = 1; i < n; ++i) {
                HuffmanTree tmp = items[i];
                int k = i;
                int j = i - 1;
                while (sortHuffmanTree(tmp, items[j])) {
                    items[k] = items[j];
                    k = j;
                    if (j-- == 0) {
                        break;
                    }
                }
                items[k] = tmp;
            }
        } else {
            // Shell sort.
            int g = n < 57 ? 2 : 0;
            for (; g < 6; ++g) {
                int gap = Tables.kBrotliShellGaps[g];
                for (int i = gap; i < n; ++i) {
                    int j = i;
                    HuffmanTree tmp = items[i];
                    for (; j >= gap && sortHuffmanTree(tmp, items[j - gap]); j -= gap) {
                        items[j] = items[j - gap];
                    }
                    items[j] = tmp;
                }
            }
        }
    }

    public static boolean sortHuffmanTree(HuffmanTree h1, HuffmanTree h2) {
        if (h1.getTotalCount() != h2.getTotalCount()) {
            return h1.getTotalCount() < h2.getTotalCount();
        }
        return h1.getIndexRightOrValue() > h2.getIndexRightOrValue();
    }

    public static boolean brotliSetDepth(int p, HuffmanTree[] pool, int[] depth, int maxDepth, int index) throws BrotliException {
        int[] stack = new int[16];
        int level = 0;
        stack[0] = -1;

        if (maxDepth > 15) {
            throw new BrotliException("Error: max depth is more than 15");
        }

        while (true) {
            if (pool[p].getIndexLeft() >= 0) {
                level++;
                if (level > maxDepth) {
                    return false;
                }
                stack[level] = pool[p].getIndexRightOrValue();
                p = pool[p].getIndexLeft();
                continue;
            } else {
                depth[index + pool[p].getIndexRightOrValue()] = level;
            }
            while (level >= 0 && stack[level] == -1) {
                level--;
            }
            if (level < 0) {
                return true;
            }
            p = stack[level];
            stack[level] = -1;
        }
    }

    public static void brotliConvertBitDepthsToSymbols(int[] depth, int len, int[] bits, int index) throws BrotliException {

        if ((index + len) > depth.length) {
            throw new BrotliException("Error: Index out of bounds into depth table detected");
        }

        int[] blCount = new int[Constant.MAX_HUFFMAN_BITS];
        int[] next_code = new int[Constant.MAX_HUFFMAN_BITS];
        int i;
        int code = 0;
        for (i = index; i < (index + len); ++i) {
            ++blCount[depth[i]];
        }
        blCount[0] = 0;
        next_code[0] = 0;
        for (i = 1; i < Constant.MAX_HUFFMAN_BITS; ++i) {
            code = (code + blCount[(i) - 1]) << 1;
            next_code[i] = code;
        }
        for (i = index; i < (index + len); ++i) {
            if (depth[i] != 0) {
                bits[i] = brotliReverseBits(depth[i], next_code[depth[i]]++);
            }
        }
    }

    public static int brotliReverseBits(int numBits, int bits) {
        int[] kLut = {
                0x00, 0x08, 0x04, 0x0C, 0x02, 0x0A, 0x06, 0x0E,
                0x01, 0x09, 0x05, 0x0D, 0x03, 0x0B, 0x07, 0x0F
        };
        int retval = kLut[bits & 0x0F];
        for (int i = 4; i < numBits; i += 4) {
            retval <<= 4;
            bits = (bits >> 4);
            retval |= kLut[bits & 0x0F];
        }
        retval >>= ((-numBits) & 0x03);
        return retval;
    }

    public static int brotliWriteHuffmanTree(int[] depth,
                                             int index,
                                             int length,
                                             int[] tree,
                                             int[] extraBitsData) {
        int treeSize = 0;
        int previousValue = Constant.BROTLI_INITIAL_REPEATED_CODE_LENGTH;
        int i;
        useRleForNonZero = false;
        useRleForZero = false;

        int newLength = length;
        for (i = 0; i < length; ++i) {
            if (depth[index + length - i - 1] == 0) {
                --newLength;
            } else {
                break;
            }
        }

        if (length > 50) {
            decideOverRleUse(depth, index, newLength);
        }

        for (i = 0; i < newLength; ) {
            int value = depth[index + i];
            int reps = 1;
            if ((value != 0 && useRleForNonZero) || (value == 0 && useRleForZero)) {
                for (int k = i + 1; k < newLength && depth[index + k] == value; ++k) {
                    ++reps;
                }
            }
            if (value == 0) {
                treeSize = brotliWriteHuffmanTreeRepetitionsZeros(reps, tree, treeSize, extraBitsData);
            } else {
                treeSize = brotliWriteHuffmanTreeRepetitions(previousValue,
                                                             value, reps, tree, treeSize, extraBitsData);
                previousValue = value;
            }
            i += reps;
        }

        return treeSize;
    }

    public static void decideOverRleUse(int[] depth, int index, int length) {
        int totalRepsZero = 0;
        int totalRepsNonZero = 0;
        int countRepsZero = 1;
        int countRepsNonZero = 1;
        for (int i = 0; i < length; ) {
            int value = depth[index + i];
            int reps = 1;
            for (int k = i + 1; k < length && depth[index + k] == value; ++k) {
                ++reps;
            }
            if (reps >= 3 && value == 0) {
                totalRepsZero += reps;
                ++countRepsZero;
            }
            if (reps >= 4 && value != 0) {
                totalRepsNonZero += reps;
                ++countRepsNonZero;
            }
            i += reps;
        }
        useRleForNonZero = totalRepsNonZero > (countRepsNonZero * 2);
        useRleForZero = totalRepsZero > (countRepsZero * 2);
    }

    public static int brotliWriteHuffmanTreeRepetitionsZeros(int repetitions, int[] tree, int treeSize,
                                                             int[] extraBitsData) {
        if (repetitions < 0) {
            LOGGER.log(Level.WARNING, "Write Huffman Tree Repetitions : repetitions is negative");
        }
        if (repetitions == 11) {
            tree[treeSize] = 0;
            extraBitsData[treeSize] = 0;
            ++treeSize;
            --repetitions;
        }
        if (repetitions < 3) {
            for (int i = 0; i < repetitions; ++i) {
                tree[treeSize] = 0;
                extraBitsData[treeSize] = 0;
                ++treeSize;
            }
        } else {
            int start = treeSize;
            repetitions -= 3;
            while (true) {
                tree[treeSize] = Constant.BROTLI_REPEAT_ZERO_CODE_LENGTH;
                extraBitsData[treeSize] = repetitions & 0x7;
                ++treeSize;
                repetitions >>= 3;
                if (repetitions == 0) {
                    break;
                }
                --repetitions;
            }
            reverse(tree, start, treeSize);
            reverse(extraBitsData, start, treeSize);
        }
        return treeSize;
    }

    public static void reverse(int[] v, int start, int end) {
        --end;
        while (start < end) {
            int tmp = v[start];
            v[start] = v[end];
            v[end] = tmp;
            ++start;
            --end;
        }
    }

    public static int brotliWriteHuffmanTreeRepetitions(int previousValue, int value, int repetitions,
                                                        int[] tree, int treeSize, int[] extraBitsData) {
        if (repetitions < 0) {
            LOGGER.log(Level.WARNING, "Write Huffman Tree Repetitions : repetitions is negative");
        }
        if (previousValue != value) {
            tree[treeSize] = value;
            extraBitsData[treeSize] = 0;
            ++treeSize;
            --repetitions;
        }
        if (repetitions == 7) {
            tree[treeSize] = value;
            extraBitsData[treeSize] = 0;
            ++treeSize;
            --repetitions;
        }
        if (repetitions < 3) {
            for (int i = 0; i < repetitions; ++i) {
                tree[treeSize] = value;
                extraBitsData[treeSize] = 0;
                ++treeSize;
            }
        } else {
            int start = treeSize;
            repetitions -= 3;
            while (true) {
                tree[treeSize] = Constant.BROTLI_REPEAT_PREVIOUS_CODE_LENGTH;
                extraBitsData[treeSize] = repetitions & 0x3;
                ++treeSize;
                repetitions >>= 2;
                if (repetitions == 0) {
                    break;
                }
                --repetitions;
            }
            reverse(tree, start, treeSize);
            reverse(extraBitsData, start, treeSize);
        }
        return treeSize;
    }

    public static void buildAndStoreHuffmanTree(State state, int[] histogram, int histoIx, int histoLength,
                                                int alphaSize, HuffmanTree[] tree, int[] depth, int depthsIx,
                                                int[] bits, int bitsIx) throws BrotliException {
        int count = 0;
        int[] s4 = new int[4];
        int i;
        int maxBits = 0;
        for (i = 0; i < histoLength; i++) {
            if (histogram[histoIx + i] != 0) {
                if (count < 4) {
                    s4[count] = i;
                } else if (count > 4) {
                    break;
                }
                count++;
            }
        }

        int maxBitsCounter = alphaSize - 1;
        while (maxBitsCounter != 0) {
            maxBitsCounter >>= 1;
            ++maxBits;
        }

        if (count <= 1) {
            state.storageBit = BitWriter.writeBit(4, 1, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(maxBits, s4[0], state.storageBit, state.storage);
            depth[depthsIx + s4[0]] = 0;
            bits[bitsIx + s4[0]] = 0;
            return;
        }

        depth = new int[histoLength];

        Huffman.brotliCreateHuffmanTree(histogram, histoIx, histoLength, 15, tree, depth, 0);
        Huffman.brotliConvertBitDepthsToSymbols(depth, histoLength, bits, 0);

        if (count <= 4) {
            storeSimpleHuffmanTree(state, depth, s4, count, maxBits);
        } else {
            state.storageBit = BitStreamManager.brotliStoreHuffmanTree(
                    depth, histoLength, tree, 0, state.storageBit, state.storage);
        }
    }

    public static void storeSimpleHuffmanTree(State state, int[] depths, int[] symbols, int num_symbols, int max_bits)
            throws BrotliException {
        state.storageBit = BitWriter.writeBit(2, 1, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(2, num_symbols - 1, state.storageBit, state.storage);

        int i;
        for (i = 0; i < num_symbols; i++) {
            int j;
            for (j = i + 1; j < num_symbols; j++) {
                if (depths[symbols[j]] < depths[symbols[i]]) {
                    Utils.swap(symbols, j, i);
                }
            }
        }

        if (num_symbols == 2) {
            state.storageBit = BitWriter.writeBit(max_bits, symbols[0], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(max_bits, symbols[1], state.storageBit, state.storage);
        } else if (num_symbols == 3) {
            state.storageBit = BitWriter.writeBit(max_bits, symbols[0], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(max_bits, symbols[1], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(max_bits, symbols[2], state.storageBit, state.storage);
        } else {
            state.storageBit = BitWriter.writeBit(max_bits, symbols[0], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(max_bits, symbols[1], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(max_bits, symbols[2], state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(max_bits, symbols[3], state.storageBit, state.storage);

            state.storageBit = BitWriter.writeBit(1, depths[symbols[0]] == 1 ? 1 : 0, state.storageBit, state.storage);
        }
    }

}
