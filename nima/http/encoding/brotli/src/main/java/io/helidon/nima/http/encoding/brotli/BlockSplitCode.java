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

class BlockSplitCode {
    BlockTypeCodeCalculator type_code_calculator;
    int[] type_depths;
    int[] type_bits;
    int[] length_depths;
    int[] length_bits;

    BlockSplitCode() {
        type_code_calculator = new BlockTypeCodeCalculator();
    }

    public static void buildAndStoreBlockSplitCode(State state, int[] types, int[] lengths, int numBlocks,
                                                   int numTypes, HuffmanTree[] tree, BlockSplitCode blockSplitCode)
            throws BrotliException {
        int[] type_histo = new int[Constant.BROTLI_MAX_BLOCK_TYPE_SYMBOLS];
        int[] length_histo = new int[Constant.BROTLI_NUM_BLOCK_LEN_SYMBOLS];
        int i;
        BlockTypeCodeCalculator type_code_calculator = new BlockTypeCodeCalculator();
        BlockTypeCodeCalculator.initBlockTypeCodeCalculator(type_code_calculator);
        for (i = 0; i < numBlocks; ++i) {
            int type_code = BlockTypeCodeCalculator.nextBlockTypeCode(type_code_calculator, types[i]);
            if (i != 0) {
                ++type_histo[type_code];
            }
            ++length_histo[blockLengthPrefixCode(lengths[i])];
        }
        storeVarLenUint8(state, numTypes - 1);
        if (numTypes > 1) {
            Huffman.buildAndStoreHuffmanTree(state, type_histo, 0, numTypes + 2, numTypes + 2, tree,
                                             blockSplitCode.type_depths, 0, blockSplitCode.type_bits, 0);
            Huffman.buildAndStoreHuffmanTree(state, length_histo, 0, Constant.BROTLI_NUM_BLOCK_LEN_SYMBOLS,
                                             Constant.BROTLI_NUM_BLOCK_LEN_SYMBOLS,
                                             tree, blockSplitCode.length_depths, 0, blockSplitCode.length_bits, 0);
            storeBlockSwitch(state, blockSplitCode, lengths[0], types[0], true);
        }
    }

    public static void storeBlockSwitch(State state, BlockSplitCode code, int blockLen, int blockType,
                                        boolean isFirstBlock) throws BrotliException {
        int typecode = BlockTypeCodeCalculator.nextBlockTypeCode(code.type_code_calculator, blockType);
        if (!isFirstBlock) {
            state.storageBit = BitWriter.writeBit(
                    code.type_depths[typecode], code.type_bits[typecode], state.storageBit, state.storage);
        }

        int[] result = getBlockLengthPrefixCode(blockLen);
        int lencode = result[0];
        int len_nextra = result[1];
        int len_extra = result[2];

        state.storageBit = BitWriter.writeBit(
                code.length_depths[lencode], code.length_bits[lencode], state.storageBit, state.storage);

        state.storageBit = BitWriter.writeBit(len_nextra, len_extra, state.storageBit, state.storage);
    }

    public static int[] getBlockLengthPrefixCode(int blockLen) {
        int[] res = new int[3];
        res[0] = blockLengthPrefixCode(blockLen);
        res[1] = Tables._kBrotliPrefixCodeRanges[res[0]].nBits;
        res[2] = blockLen - Tables._kBrotliPrefixCodeRanges[res[0]].offset;

        return res;
    }

    public static void storeVarLenUint8(State state, int n) throws BrotliException {
        if (n == 0) {
            state.storageBit = BitWriter.writeBit(1, 0, state.storageBit, state.storage);
        } else {
            int nbits = Utils.log2FloorNonZero(n);
            state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(3, nbits, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(nbits, n - (1 << nbits), state.storageBit, state.storage);
        }
    }

    public static int blockLengthPrefixCode(int length) {
        int code = (length >= 177) ? (length >= 753 ? 20 : 14) : (length >= 41 ? 7 : 0);
        while (code < (Constant.BROTLI_NUM_BLOCK_LEN_SYMBOLS - 1) &&
                length >= Tables._kBrotliPrefixCodeRanges[code + 1].offset) {
            ++code;
        }
        return code;
    }
}
