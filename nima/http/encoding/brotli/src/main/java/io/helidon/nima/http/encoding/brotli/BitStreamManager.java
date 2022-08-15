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

class BitStreamManager {

    private static final System.Logger LOGGER = System.getLogger(BitStreamManager.class.getName());

    public static int brotliStoreHuffmanTree(int[] depths, int alphabetSize,
                                             HuffmanTree[] tree, int index, int indexBit, int[] storage) throws BrotliException {

        int[] huffmanTree = new int[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
        int[] huffmanTreeExtraBits = new int[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
        int[] codeLengthBitdepth = new int[Constant.BROTLI_CODE_LENGTH_CODES];
        int[] codeLengthBitdepthSymbols = new int[Constant.BROTLI_CODE_LENGTH_CODES];
        int[] huffmanTreeHistogram = new int[Constant.BROTLI_CODE_LENGTH_CODES];
        int i;
        int num_codes = 0;
        int code = 0;

        if (alphabetSize > Constant.BROTLI_NUM_COMMAND_SYMBOLS) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "alphabet size is too big, should be less than " + Constant.BROTLI_NUM_COMMAND_SYMBOLS);
        }

        int huffman_tree_size = Huffman.brotliWriteHuffmanTree(depths, index, alphabetSize, huffmanTree,
                                                               huffmanTreeExtraBits);

        for (i = 0; i < huffman_tree_size; ++i) {
            ++huffmanTreeHistogram[huffmanTree[i]];
        }

        for (i = 0; i < Constant.BROTLI_CODE_LENGTH_CODES; ++i) {
            if (huffmanTreeHistogram[i] != 0) {
                if (num_codes == 0) {
                    code = i;
                    num_codes = 1;
                } else if (num_codes == 1) {
                    num_codes = 2;
                    break;
                }
            }
        }

        Huffman.brotliCreateHuffmanTree(huffmanTreeHistogram, 0, Constant.BROTLI_CODE_LENGTH_CODES,
                                        5, tree, codeLengthBitdepth, 0);
        Huffman.brotliConvertBitDepthsToSymbols(codeLengthBitdepth,
                                                Constant.BROTLI_CODE_LENGTH_CODES, codeLengthBitdepthSymbols, 0);

        indexBit = brotliStoreHuffmanTreeOfHuffmanTreeToBitMask(num_codes, codeLengthBitdepth, indexBit, storage);

        if (num_codes == 1) {
            codeLengthBitdepth[code] = 0;
        }

        indexBit = brotliStoreHuffmanTreeToBitMask(
                huffman_tree_size,
                huffmanTree,
                huffmanTreeExtraBits,
                codeLengthBitdepth,
                codeLengthBitdepthSymbols,
                indexBit,
                storage);

        return indexBit;
    }

    public static int brotliStoreHuffmanTreeOfHuffmanTreeToBitMask(int numCodes, int[] codeLengthBitdepth,
                                                                   int indexBit, int[] storage) throws BrotliException {
        int[] kStorageOrder = {1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        int[] kHuffmanBitLengthHuffmanCodeSymbols = {0, 7, 3, 2, 1, 15};
        int[] kHuffmanBitLengthHuffmanCodeBitLengths = {2, 4, 3, 2, 2, 4};

        int skipSome = 0;
        int codesToStore = Constant.BROTLI_CODE_LENGTH_CODES;

        if (numCodes > 1) {
            for (; codesToStore > 0; --codesToStore) {
                if (codeLengthBitdepth[kStorageOrder[codesToStore - 1]] != 0) {
                    break;
                }
            }
        }
        if (codeLengthBitdepth[kStorageOrder[0]] == 0 &&
                codeLengthBitdepth[kStorageOrder[1]] == 0) {
            skipSome = 2;
            if (codeLengthBitdepth[kStorageOrder[2]] == 0) {
                skipSome = 3;
            }
        }

        indexBit = BitWriter.writeBit(2, skipSome, indexBit, storage);

        for (int i = skipSome; i < codesToStore; ++i) {
            int l = codeLengthBitdepth[kStorageOrder[i]];
            indexBit = BitWriter.writeBit(kHuffmanBitLengthHuffmanCodeBitLengths[l],
                                          kHuffmanBitLengthHuffmanCodeSymbols[l], indexBit, storage);
        }
        return indexBit;
    }

    public static int brotliStoreHuffmanTreeToBitMask(int huffmanTreeSize, int[] huffmanTree,
                                                      int[] huffmanTreeExtraBits, int[] codeLengthBitdepth,
                                                      int[] codeLengthBitdepthSymbols, int indexBit,
                                                      int[] storage) throws BrotliException {
        for (int i = 0; i < huffmanTreeSize; ++i) {
            int ix = huffmanTree[i];
            indexBit = BitWriter.writeBit(codeLengthBitdepth[ix],
                                          codeLengthBitdepthSymbols[ix], indexBit, storage);

            switch (ix) {
            case Constant.BROTLI_REPEAT_PREVIOUS_CODE_LENGTH:
                indexBit = BitWriter.writeBit(2, huffmanTreeExtraBits[i], indexBit, storage);
                break;
            case Constant.BROTLI_REPEAT_ZERO_CODE_LENGTH:
                indexBit = BitWriter.writeBit(3, huffmanTreeExtraBits[i], indexBit, storage);
                break;
            }
        }
        return indexBit;
    }

    public static void brotliBuildAndStoreHuffmanTreeFast(State state, int[] histogram, int histogramTotal, int maxBits,
                                                          int[] depths, int[] bits) throws BrotliException {
        int count = 0;
        int[] symbols = new int[4];
        int length = 0;
        int total = histogramTotal;
        while (total != 0) {
            if (histogram[length] != 0) {
                if (count < 4) {
                    symbols[count] = length;
                }
                ++count;
                total -= histogram[length];
            }
            ++length;
        }

        if (count <= 1) {
            state.storageBit = BitWriter.writeBit(4, 1, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(maxBits, symbols[0], state.storageBit, state.storage);
            depths[symbols[0]] = 0;
            bits[symbols[0]] = 0;
            return;
        }

        Utils.copyBytes(new int[length], depths, length);

        int maxTreeSize = 2 * length + 1;
        HuffmanTree[] tree = new HuffmanTree[maxTreeSize];
        for (int index = 0; index < maxTreeSize; index++) {
            tree[index] = new HuffmanTree();
        }

        int nodeIndex = 0;

        for (int countLimit = 1; ; countLimit *= 2) {

            for (int l = length; l != 0; ) {
                --l;
                if (histogram[l] != 0) {
                    HuffmanTree.initHuffmanTree(tree[nodeIndex], Math.max(histogram[l], countLimit), -1, l);
                    ++nodeIndex;
                }
            }

            int n = nodeIndex;
            int i = 0;
            int j = n + 1;

            Huffman.sortHuffmanTreeItems(tree, n);
            tree[nodeIndex++] = new Sentinel();
            tree[nodeIndex++] = new Sentinel();

            for (int k = n - 1; k > 0; --k) {
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
                tree[nodeIndex - 1].setTotalCount(tree[left].getTotalCount() + tree[right].getTotalCount());
                tree[nodeIndex - 1].setIndexLeft(left);
                tree[nodeIndex - 1].setIndexRightOrValue(right);
                tree[nodeIndex++] = new Sentinel();
            }

            if (Huffman.brotliSetDepth(2 * n - 1, tree, depths, 14, 0)) {
                break;
            }
        }

        Huffman.brotliConvertBitDepthsToSymbols(depths, length, bits, 0);
        if (count <= 4) {
            int i;
            state.storageBit = BitWriter.writeBit(2, 1, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(2, (count - 1), state.storageBit, state.storage); /* NSYM - 1 */

            /* Sort */
            for (i = 0; i < count; i++) {
                int j;
                for (j = i + 1; j < count; j++) {
                    if (depths[symbols[j]] < depths[symbols[i]]) {
                        Utils.swap(symbols, j, i);
                    }
                }
            }

            if (count == 2) {
                state.storageBit = BitWriter.writeBit(maxBits, symbols[0], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(maxBits, symbols[1], state.storageBit, state.storage);
            } else if (count == 3) {
                state.storageBit = BitWriter.writeBit(maxBits, symbols[0], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(maxBits, symbols[1], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(maxBits, symbols[2], state.storageBit, state.storage);
            } else {
                state.storageBit = BitWriter.writeBit(maxBits, symbols[0], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(maxBits, symbols[1], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(maxBits, symbols[2], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(maxBits, symbols[3], state.storageBit, state.storage);

                state.storageBit = BitWriter.writeBit(1, depths[symbols[0]] == 1 ? 1 : 0, state.storageBit, state.storage);
            }
        } else {
            int previousValue = 8;

            storeStaticCodeLengthCode(state);

            for (int i = 0; i < length; ) {
                int value = depths[i];
                int reps = 1;
                for (int k = i + 1; k < length && depths[k] == value; ++k) {
                    ++reps;
                }
                i += reps;
                if (value == 0) {
                    state.storageBit = BitWriter.writeBit(StaticEntropy.kZeroRepsDepth[reps],
                                                          StaticEntropy.kZeroRepsBits[reps], state.storageBit, state.storage);
                } else {
                    if (previousValue != value) {
                        state.storageBit = BitWriter.writeBit(StaticEntropy.kCodeLengthDepth[value],
                                                              StaticEntropy.kCodeLengthBits[value],
                                                              state.storageBit,
                                                              state.storage);
                        --reps;
                    }
                    if (reps < 3) {
                        while (reps != 0) {
                            reps--;
                            state.storageBit = BitWriter.writeBit(StaticEntropy.kCodeLengthDepth[value],
                                                                  StaticEntropy.kCodeLengthBits[value],
                                                                  state.storageBit,
                                                                  state.storage);
                        }
                    } else {
                        reps -= 3;
                        state.storageBit = BitWriter.writeBit(StaticEntropy.kNonZeroRepsDepth[reps],
                                                              StaticEntropy.kNonZeroRepsBits[reps],
                                                              state.storageBit,
                                                              state.storage);
                    }
                    previousValue = value;
                }
            }
        }
    }

    public static void storeStaticCodeLengthCode(State state) throws BrotliException {
        long longValue = (0x0000FFL << 32) | 0x55555554L;
        state.storageBit = BitWriter.writeBit(40, longValue, state.storageBit, state.storage);
    }

    public static void brotliStoreUncompressedMetaBlock(State state, boolean isLast, int[] data, int position,
                                                        int mask, int length) throws BrotliException {
        int maskedPos = position & mask;
        brotliStoreUncompressedMetaBlockHeader(state, length);
        jumpToByteBoundary(state);

        if (maskedPos + length > mask + 1) {
            int len1 = mask + 1 - maskedPos;
            Utils.writeBuffer(data, maskedPos, len1, state.storage, state.storageBit >> 3);
            state.storageBit += len1 << 3;
            length -= len1;
            maskedPos = 0;
        }
        Utils.writeBuffer(data, maskedPos, length, state.storage, state.storageBit >> 3);
        state.storageBit += length << 3;

        brotliWriteBitsPrepareStorage(state);

        if (isLast) {
            state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
            jumpToByteBoundary(state);
        }
    }

    public static void brotliStoreMetaBlock(State state, int[] input, int startPosition, int length, int mask,
                                            boolean isLast, Compress.ContextType literalContextMode,
                                            MetaBlockSplit mb) throws BrotliException {

        int pos = startPosition;
        int i;
        int numDistanceSymbols = state.distance.alphabetSizeMax;
        int numEffectiveDistanceSymbols = state.distance.alphabetSizeLimit;
        HuffmanTree[] tree = new HuffmanTree[Constant.MAX_HUFFMAN_TREE_SIZE];
        for (i = 0; i < Constant.MAX_HUFFMAN_TREE_SIZE; i++) {
            tree[i] = new HuffmanTree();
        }
        int literal_context_lut = Compress.contextLut(literalContextMode);
        BlockEncoder literalEnc = new BlockEncoder();
        BlockEncoder commandEnc = new BlockEncoder();
        BlockEncoder distanceEnc = new BlockEncoder();
        Distance dist = state.distance;
        if (numEffectiveDistanceSymbols > Constant.BROTLI_NUM_HISTOGRAM_DISTANCE_SYMBOLS) {
            LOGGER.log(System.Logger.Level.WARNING, "Store Meta Block: distance symbols too big");
        }

        storeCompressedMetaBlockHeader(state, isLast, length);

        BlockEncoder.initBlockEncoder(literalEnc, Constant.BROTLI_NUM_LITERAL_SYMBOLS,
                                      mb.literalsplit.numTypes, mb.literalsplit.types,
                                      mb.literalsplit.lengths, mb.literalsplit.numBlocks);
        BlockEncoder.initBlockEncoder(commandEnc, Constant.BROTLI_NUM_COMMAND_SYMBOLS,
                                      mb.commandsplit.numTypes, mb.commandsplit.types,
                                      mb.commandsplit.lengths, mb.commandsplit.numBlocks);
        BlockEncoder.initBlockEncoder(distanceEnc, numEffectiveDistanceSymbols,
                                      mb.distancesplit.numTypes, mb.distancesplit.types,
                                      mb.distancesplit.lengths, mb.distancesplit.numBlocks);

        buildAndStoreBlockSwitchEntropyCodes(state, literalEnc, tree);
        buildAndStoreBlockSwitchEntropyCodes(state, commandEnc, tree);
        buildAndStoreBlockSwitchEntropyCodes(state, distanceEnc, tree);

        state.storageBit = BitWriter.writeBit(2, dist.distancePostfixBits, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(4, dist.numDirectDistanceCodes >> dist.distancePostfixBits,
                                              state.storageBit, state.storage);

        for (i = 0; i < mb.literalsplit.numTypes; ++i) {
            state.storageBit = BitWriter.writeBit(2, encodeContextType(literalContextMode),
                                                  state.storageBit, state.storage);
        }

        if (mb.literalContextMapSize == 0) {
            storeTrivialContextMap(state, mb.literalHistogramsSize, Constant.BROTLI_LITERAL_CONTEXT_BITS, tree);
        } else {
            encodeContextMap(state,
                             mb.literalContextMap, mb.literalContextMapSize,
                             mb.literalHistogramsSize, tree);
        }

        if (mb.distanceContextMapSize == 0) {
            storeTrivialContextMap(state, mb.distanceHistogramsSize,
                                   Constant.BROTLI_DISTANCE_CONTEXT_BITS, tree);
        } else {
            encodeContextMap(state,
                             mb.distanceContextMap, mb.distanceContextMapSize,
                             mb.distanceHistogramsSize, tree);
        }

        buildAndStoreEntropyCodes(state, literalEnc, mb.literalHistograms,
                                  mb.literalHistogramsSize, Constant.BROTLI_NUM_LITERAL_SYMBOLS, tree);
        buildAndStoreEntropyCodes(state, commandEnc, mb.commandHistograms,
                                  mb.commandHistogramsSize, Constant.BROTLI_NUM_COMMAND_SYMBOLS, tree);
        buildAndStoreEntropyCodes(state, distanceEnc, mb.distanceHistograms,
                                  mb.distanceHistogramsSize, numDistanceSymbols, tree);

        for (i = 0; i < state.numCommands; ++i) {
            Command cmd = state.commands[i];
            int cmdCode = cmd.getCmdPrefix();
            storeSymbol(state, commandEnc, cmdCode);
            Command.storeCommandExtra(state, cmd);
            if (mb.literalContextMapSize == 0) {
                int j;
                for (j = (int) cmd.getInsertLen(); j != 0; --j) {
                    storeSymbol(state, literalEnc, input[pos & mask]);
                    ++pos;
                }
            } else {
                int j;
                for (j = (int) cmd.getInsertLen(); j != 0; --j) {
                    int context = Histogram.brotliContext(state.prevByte, state.prevByte2, literal_context_lut);
                    int literal = input[pos & mask];
                    storeSymbolWithContext(state, literalEnc, literal, context,
                                           mb.literalContextMap, Constant.BROTLI_LITERAL_CONTEXT_BITS);
                    state.prevByte2 = state.prevByte;
                    state.prevByte = literal;
                    ++pos;
                }
            }
            pos += Command.commandCopyLen(cmd);
            if (Command.commandCopyLen(cmd) != 0) {
                state.prevByte2 = input[(pos - 2) & mask];
                state.prevByte = input[(pos - 1) & mask];
                if (cmd.getCmdPrefix() >= 128) {
                    int distCode = cmd.getDistPrefix() & 0x3FF;
                    int distnumextra = cmd.getDistPrefix() >> 10;
                    long distextra = cmd.getDistExtra();
                    if (mb.distanceContextMapSize == 0) {
                        storeSymbol(state, distanceEnc, distCode);
                    } else {
                        int context = Command.commandDistanceContext(cmd);
                        storeSymbolWithContext(state, distanceEnc, distCode, context,
                                               mb.distanceContextMap, Constant.BROTLI_DISTANCE_CONTEXT_BITS);
                    }
                    state.storageBit = BitWriter.writeBit(distnumextra, distextra, state.storageBit, state.storage);
                }
            }
        }
        if (isLast) {
            jumpToByteBoundary(state);
        }
    }

    public static void storeSymbolWithContext(State state, BlockEncoder self, int symbol, int context,
                                              int[] contextMap, int contextBits) throws BrotliException {
        if (self.blockLen == 0) {
            int block_ix = ++self.blockIx;
            int block_len = self.blockLengths[block_ix];
            int block_type = self.blockTypes[block_ix];
            self.blockLen = block_len;
            self.entropyIx = block_type << contextBits;
            BlockSplitCode.storeBlockSwitch(state, self.blockSplitCode, block_len, block_type, false);
        }
        --self.blockLen;

        int histo_ix = contextMap[self.entropyIx + context];
        int ix = histo_ix * self.histogramLength + symbol;
        state.storageBit = BitWriter.writeBit(self.depths[ix], self.bits[ix], state.storageBit, state.storage);

    }

    public static void storeSymbol(State state, BlockEncoder self, int symbol) throws BrotliException {
        if (self.blockLen == 0) {
            int block_ix = ++self.blockIx;
            int block_len = self.blockLengths[block_ix];
            int block_type = self.blockTypes[block_ix];
            self.blockLen = block_len;
            self.entropyIx = block_type * self.histogramLength;
            BlockSplitCode.storeBlockSwitch(state, self.blockSplitCode, block_len, block_type, false);
        }
        --self.blockLen;

        int ix = self.entropyIx + symbol;
        state.storageBit = BitWriter.writeBit(self.depths[ix], self.bits[ix], state.storageBit, state.storage);
    }

    public static void buildAndStoreEntropyCodes(State state, BlockEncoder self, Histogram[] histograms,
                                                 int histogramsSize, int alphabet_size,
                                                 HuffmanTree[] tree) throws BrotliException {
        int table_size = histogramsSize * self.histogramLength;
        self.depths = new int[table_size];
        self.bits = new int[table_size];

        int i;
        for (i = 0; i < histogramsSize; ++i) {
            int ix = i * self.histogramLength;
            Huffman.buildAndStoreHuffmanTree(state, histograms[i].data, 0, self.histogramLength,
                                             alphabet_size, tree, self.depths, ix, self.bits, ix);
        }
    }

    public static void encodeContextMap(State state, int[] context_map, int context_map_size,
                                        int num_clusters, HuffmanTree[] tree) throws BrotliException {
        int i;
        int[] rle_symbols;
        int max_run_length_prefix = 6;
        int num_rle_symbols = 0;
        int[] histogram = new int[Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS];
        int kSymbolMask = (1 << Constant.SYMBOL_BITS) - 1;
        int[] depths = new int[Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS];
        int[] bits = new int[Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS];

        BlockSplitCode.storeVarLenUint8(state, num_clusters - 1);

        if (num_clusters == 1) {
            return;
        }

        rle_symbols = new int[context_map_size];
        moveToFrontTransform(context_map, context_map_size, rle_symbols);
        int[] result = runLengthCodeZeros(context_map_size, rle_symbols, num_rle_symbols, max_run_length_prefix);
        num_rle_symbols = result[0];
        max_run_length_prefix = result[1];

        for (i = 0; i < num_rle_symbols; ++i) {
            ++histogram[rle_symbols[i] & kSymbolMask];
        }

        boolean use_rle = max_run_length_prefix > 0;
        state.storageBit = BitWriter.writeBit(1, use_rle, state.storageBit, state.storage);
        if (use_rle) {
            state.storageBit = BitWriter.writeBit(4, max_run_length_prefix - 1, state.storageBit, state.storage);
        }

        Huffman.buildAndStoreHuffmanTree(state, histogram, 0, num_clusters + max_run_length_prefix,
                                         num_clusters + max_run_length_prefix,
                                         tree, depths, 0, bits, 0);
        for (i = 0; i < num_rle_symbols; ++i) {
            int rle_symbol = rle_symbols[i] & kSymbolMask;
            int extra_bits_val = rle_symbols[i] >> Constant.SYMBOL_BITS;
            state.storageBit = BitWriter.writeBit(depths[rle_symbol], bits[rle_symbol], state.storageBit, state.storage);
            if (rle_symbol > 0 && rle_symbol <= max_run_length_prefix) {
                state.storageBit = BitWriter.writeBit(rle_symbol, extra_bits_val, state.storageBit, state.storage);
            }
        }
        state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
    }

    public static int[] runLengthCodeZeros(int in_size, int[] v, int out_size,
                                           int max_run_length_prefix) {
        int max_reps = 0;
        int i;
        int max_prefix;
        for (i = 0; i < in_size; ) {
            int reps = 0;
            for (; i < in_size && v[i] != 0; ++i)
                ;
            for (; i < in_size && v[i] == 0; ++i) {
                ++reps;
            }
            max_reps = Math.max(reps, max_reps);
        }
        max_prefix = max_reps > 0 ? Utils.log2FloorNonZero(max_reps) : 0;
        max_prefix = Math.min(max_prefix, max_run_length_prefix);
        max_run_length_prefix = max_prefix;
        out_size = 0;
        for (i = 0; i < in_size; ) {
            if (v[i] != 0) {
                v[out_size] = v[i] + max_run_length_prefix;
                ++i;
                ++(out_size);
            } else {
                int reps = 1;
                int k;
                for (k = i + 1; k < in_size && v[k] == 0; ++k) {
                    ++reps;
                }
                i += reps;
                while (reps != 0) {
                    if (reps < (2 << max_prefix)) {
                        int run_length_prefix = Utils.log2FloorNonZero(reps);
                        int extra_bits = reps - (1 << run_length_prefix);
                        v[out_size] = run_length_prefix + (extra_bits << 9);
                        ++out_size;
                        break;
                    } else {
                        int extra_bits = (1 << max_prefix) - 1;
                        v[out_size] = max_prefix + (extra_bits << 9);
                        reps -= (2 << max_prefix) - 1;
                        ++out_size;
                    }
                }
            }
        }
        int[] result = new int[2];
        result[0] = out_size;
        result[1] = max_run_length_prefix;
        return result;
    }

    public static void moveToFront(int[] v, int index) {
        int value = v[index];
        int i;
        for (i = index; i != 0; --i) {
            v[i] = v[i - 1];
        }
        v[0] = value;
    }

    public static int indexOf(int[] v, int v_size, int value) {
        int i = 0;
        for (; i < v_size; ++i) {
            if (v[i] == value) {
                return i;
            }
        }
        return i;
    }

    public static void storeTrivialContextMap(State state, int num_types, int context_bits,
                                              HuffmanTree[] tree) throws BrotliException {
        BlockSplitCode.storeVarLenUint8(state, num_types - 1);
        if (num_types > 1) {
            int repeat_code = context_bits - 1;
            int repeat_bits = (1 << repeat_code) - 1;
            int alphabet_size = num_types + repeat_code;
            int[] histogram = alphabet_size > Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS ?
                    new int[alphabet_size] : new int[Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS];
            int[] depths = new int[Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS];
            int[] bits = new int[Constant.BROTLI_MAX_CONTEXT_MAP_SYMBOLS];
            int i;
            state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
            state.storageBit = BitWriter.writeBit(4, repeat_code - 1, state.storageBit, state.storage);
            histogram[repeat_code] = num_types;
            histogram[0] = 1;
            for (i = context_bits; i < alphabet_size; ++i) {
                histogram[i] = 1;
            }
            Huffman.buildAndStoreHuffmanTree(state, histogram, 0, alphabet_size, alphabet_size,
                                             tree, depths, 0, bits, 0);
            for (i = 0; i < num_types; ++i) {
                int code = (i == 0 ? 0 : i + context_bits - 1);
                state.storageBit = BitWriter.writeBit(depths[code], bits[code], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(
                        depths[repeat_code], bits[repeat_code], state.storageBit, state.storage);
                state.storageBit = BitWriter.writeBit(repeat_code, repeat_bits, state.storageBit, state.storage);
            }
            state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
        }
    }

    public static void buildAndStoreBlockSwitchEntropyCodes(State state, BlockEncoder self,
                                                            HuffmanTree[] tree) throws BrotliException {
        BlockSplitCode.buildAndStoreBlockSplitCode(state, self.blockTypes, self.blockLengths,
                                                   self.numBlocks, self.numBlockTypes, tree, self.blockSplitCode);
    }

    public static void storeCompressedMetaBlockHeader(State state, boolean isLast, int length) throws BrotliException {
        long lenbits = length - 1;
        int nlenbits;
        long nibblesbits;

        state.storageBit = BitWriter.writeBit(1, isLast, state.storageBit, state.storage);

        if (isLast) {
            state.storageBit = BitWriter.writeBit(1, 0, state.storageBit, state.storage);
        }
        int mNibbles = brotliEncodeMlen(length);
        nibblesbits = mNibbles - 4;
        nlenbits = mNibbles * 4;
        state.storageBit = BitWriter.writeBit(2, nibblesbits, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(nlenbits, lenbits, state.storageBit, state.storage);

        if (!isLast) {
            state.storageBit = BitWriter.writeBit(1, 0, state.storageBit, state.storage);
        }
    }

    public static int encodeContextType(Compress.ContextType literalContextMode) {
        switch (literalContextMode) {
        case CONTEXT_LSB6:
            return 0;
        case CONTEXT_MSB6:
            return 1;
        case CONTEXT_UTF8:
            return 2;
        case CONTEXT_SIGNED:
            return 3;
        default:
            LOGGER.log(System.Logger.Level.WARNING, "Unknown Context Type");
        }
        return 0;
    }

    private static void brotliWriteBitsPrepareStorage(State state) {
        state.storage[state.storageBit >> 3] = 0;
    }

    private static void jumpToByteBoundary(State state) {
        state.storageBit = (state.storageBit + 7) & ~7;
        state.storage[state.storageBit >> 3] = 0;
    }

    private static void brotliStoreUncompressedMetaBlockHeader(State state, int length) throws BrotliException {
        long lenbits = length - 1;
        int nlenbits;
        long nibblesbits;

        state.storageBit = BitWriter.writeBit(1, 0, state.storageBit, state.storage);
        int mNibbles = brotliEncodeMlen(length);
        nibblesbits = mNibbles - 4;
        nlenbits = mNibbles * 4;
        state.storageBit = BitWriter.writeBit(2, nibblesbits, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(nlenbits, lenbits, state.storageBit, state.storage);
        state.storageBit = BitWriter.writeBit(1, 1, state.storageBit, state.storage);
    }

    private static int brotliEncodeMlen(int length) {
        int lg = (length == 1) ? 1 : Utils.log2FloorNonZero(length - 1) + 1;
        return (lg < 16 ? 16 : (lg + 3)) / 4;
    }

    private static void moveToFrontTransform(int[] v_in, int v_size, int[] v_out) {
        int i;
        int[] mtf = new int[256];
        int max_value;
        if (v_size == 0) {
            return;
        }
        max_value = v_in[0];
        for (i = 1; i < v_size; ++i) {
            if (v_in[i] > max_value) {
                max_value = v_in[i];
            }
        }
        if (max_value >= 256) {
            LOGGER.log(System.Logger.Level.WARNING, "MoveToFront max value too big");
        }
        for (i = 0; i <= max_value; ++i) {
            mtf[i] = i;
        }

        int mtf_size = max_value + 1;
        for (i = 0; i < v_size; ++i) {
            int index = indexOf(mtf, mtf_size, v_in[i]);
            v_out[i] = index;
            moveToFront(mtf, index);
        }

    }

}
