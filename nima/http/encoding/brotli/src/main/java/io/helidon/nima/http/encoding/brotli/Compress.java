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

class Compress {

    private static final System.Logger LOGGER = System.getLogger(Compress.class.getName());

    /**
     * Entrance method for encoding data with quality 10.
     *
     * @param state current state.
     */
    public static void encodeData(State state, boolean isLast, boolean forceFlush) throws BrotliException {
        int delta = Encoder.unprocessedInputSize(state);
        int wrappedLastProcessedPosition = wrapPosition(state.lastProcessedPosition);
        int[] data = RingBuffer.getBuffer();
        int mask = RingBuffer.getMask();
        ContextType literalContextMode;
        int literalContextLut;

        if (state.isLastBlockEmitted) {
            return;
        }

        if (isLast) {
            state.isLastBlockEmitted = true;
        }

        if (delta > Encoder.inputBlockSize(state)) {
            return;
        }

        int newSize = state.numCommands + delta / 2 + 1;
        if (newSize > state.cmdAllocSize) {
            Command[] commands = new Command[newSize];
            if (state.cmdAllocSize > 0) {
                Utils.copyCommands(commands, 0, state.commands, 0, state.cmdAllocSize);
            }
            state.commands = commands;
        }

        initOrStitchToPreviousBlock(state, state.hasher, data, mask, wrappedLastProcessedPosition, delta, isLast);
        literalContextMode = chooseContextMode(state, data, wrapPosition(state.lastFlushPosition),
                                               mask, (state.inputOffset - state.lastFlushPosition));
        literalContextLut = contextLut(literalContextMode);

        if (state.numCommands != 0 && state.lastInsertLength == 0) {
            long result = extendLastCommand(state, delta, wrappedLastProcessedPosition);
            delta = (int) (result & Integer.MAX_VALUE);
            wrappedLastProcessedPosition = (int) (result >> 32);
        }

        if (state.quality == 10) {
            if (state.hasherType != 10) {
                LOGGER.log(Level.WARNING, "EncodeData : Wrong Hasher type");
            }
            brotliCreateZopfliBackwardReferences(state, delta, wrappedLastProcessedPosition,
                                                 data, mask, literalContextLut);
        }

        int maxLength = maxMetablockSize(state);
        int maxLiterals = maxLength / 8;
        int maxCommands = maxLength / 8;
        int processedBytes = state.inputOffset - state.lastFlushPosition;
        boolean nextInputFitsMetablock = processedBytes + Encoder.inputBlockSize(state) <= maxLength;
        if (!isLast && !forceFlush && nextInputFitsMetablock &&
                state.numLiterals < maxLiterals && state.numCommands < maxCommands) {
            if (updateLastProcessedPosition(state)) {
                Hasher.hasherReset(state.hasher);
            }
            state.availableOut = 0;
            return;
        }

        if (state.lastInsertLength > 0) {
            Command.initInsertCommand(state);
            state.numLiterals += state.lastInsertLength;
            state.lastInsertLength = 0;
        }

        if (!isLast && state.inputOffset == state.lastFlushPosition) {
            state.availableOut = 0;
            return;
        }

        if (state.inputOffset < state.lastFlushPosition) {
            LOGGER.log(Level.WARNING, "Encode Data : input position is less than last flush position");
        }
        if (state.inputOffset <= state.lastFlushPosition || !isLast) {
            LOGGER.log(Level.WARNING, "Encode Data : wrong data setting ?");
        }
        if (state.inputOffset - state.lastFlushPosition > 1 << 24) {
            LOGGER.log(Level.WARNING, "Encode Data : wrong data setting ?");
        }

        int metaBlockSize = state.inputOffset - state.lastFlushPosition;
        state.storage = new int[2 * metaBlockSize + 503];
        state.storageBit = state.lastBytesBits;
        state.storage[0] = state.lastBytes & 255;
        state.storage[1] = state.lastBytes >> 8;
        writeMetaBlockInternal(state, data, mask, metaBlockSize, isLast, literalContextMode);
        state.lastBytes = Utils.get16Bits(state.storage, state.storageBit >> 3);
        state.lastBytesBits = state.storageBit & 7;

        if (updateLastProcessedPosition(state)) {
            Hasher.hasherReset(state.hasher);
        }

        if (state.lastFlushPosition > 0) {
            state.prevByte = data[((int) state.lastFlushPosition - 1) & mask];
        }

        if (state.lastFlushPosition > 1) {
            state.prevByte = data[((int) state.lastFlushPosition - 2) & mask];
        }
        state.numCommands = 0;
        state.numLiterals = 0;
        Utils.copyBytes(state.savedDistCache, state.distCache, state.savedDistCache.length);
        Encoder.injectFlushOrPushOutput(state);
        state.availableOut = state.storageBit >> 3;
    }

    public static int contextLut(ContextType literalContextMode) {
        switch (literalContextMode) {
        case CONTEXT_MSB6:
            return 1 << 9;
        case CONTEXT_UTF8:
            return 2 << 9;
        case CONTEXT_SIGNED:
            return 3 << 9;
        default:
            return 0;
        }
    }

    public static ContextType chooseContextMode(State state, int[] data, int position, int mask, long length) {
        if (state.quality >= Constant.MIN_QUALITY_FOR_HQ_BLOCK_SPLITTING &&
                !Utils.brotliIsMostlyUTF8(data, position, mask, length, Constant.kMinUTF8Ratio)) {
            return ContextType.CONTEXT_SIGNED;
        }
        return ContextType.CONTEXT_UTF8;
    }

    public static void initOrStitchToPreviousBlock(State state, Hasher hasher, int[] data, int mask,
                                                   int position, int inputSize, boolean isLast) {
        Hasher.hasherSetup(hasher, state, data, position, inputSize, isLast);
        StitchToPreviousBlockH10(hasher.tree, inputSize, position, data, mask);
    }

    public static void StitchToPreviousBlockH10(HashToBinaryTree tree, int inputSize, int position, int[] data,
                                                int mask) {
        if (inputSize >= Hasher.hashTypeLength() - 1 && position >= Hasher.MAX_TREE_COMP_LENGTH) {
            int i_start = position - Hasher.MAX_TREE_COMP_LENGTH + 1;
            int i_end = Math.min(position, i_start + inputSize);
            int i;
            for (i = i_start; i < i_end; ++i) {
                int max_backward = tree.windowMask - Math.max(Constant.BROTLI_WINDOW_GAP - 1, position - i);
                storeAndFindMatches(tree, data, i, mask,
                                    Hasher.MAX_TREE_COMP_LENGTH, max_backward, 0, null, 0);
            }
        }
    }

    //Todo: check params
    public static int storeAndFindMatches(HashToBinaryTree tree, int[] data, int cur_ix, int mask, int maxLength,
                                          int max_backward, int bestLen, BackwardMatch[] matches, int matchesIndex) {
        int cur_ix_masked = cur_ix & mask;
        int max_comp_len = Math.min(maxLength, Hasher.MAX_TREE_COMP_LENGTH);
        boolean should_reroot_tree = maxLength >= Hasher.MAX_TREE_COMP_LENGTH;
        int key = Hasher.hashBytes(data, cur_ix_masked);
        int[] buckets = tree.buckets;
        int[] forest = tree.forest;
        int prev_ix = buckets[key];
        /* The forest index of the rightmost node of the left subtree of the new
            root, updated as we traverse and re-root the tree of the hash bucket. */
        int node_left = HashToBinaryTree.leftChildIndex(tree, cur_ix);
        /* The forest index of the leftmost node of the right subtree of the new
            root, updated as we traverse and re-root the tree of the hash bucket. */
        int node_right = HashToBinaryTree.rightChildIndex(tree, cur_ix);
        /* The match length of the rightmost node of the left subtree of the new
        root, updated as we traverse and re-root the tree of the hash bucket. */
        int best_len_left = 0;
        /* The match length of the leftmost node of the right subtree of the new
            root, updated as we traverse and re-root the tree of the hash bucket. */
        int best_len_right = 0;
        int depth_remaining;
        if (should_reroot_tree) {
            buckets[key] = cur_ix;
        }
        for (depth_remaining = Hasher.MAX_TREE_SEARCH_DEPTH; ; --depth_remaining) {
            int backward = cur_ix - prev_ix;
            int prev_ix_masked = prev_ix & mask;
            if (backward == 0 || backward > max_backward || depth_remaining == 0) {
                if (should_reroot_tree) {
                    forest[node_left] = tree.invalidPos;
                    forest[node_right] = tree.invalidPos;
                }
                break;
            }

            int cur_len = Math.min(best_len_left, best_len_right);
            int len;
            if (cur_len > Hasher.MAX_TREE_COMP_LENGTH) {
                LOGGER.log(Level.WARNING, "store and find matches: Weird value");
            }

            len = cur_len + Encoder.findMatchLengthWithLimit(data, cur_ix_masked + cur_len,
                                                             prev_ix_masked + cur_len, maxLength - cur_len);
            if (matches != null && len > bestLen) {
                bestLen = len;
                matches[matchesIndex++] = BackwardMatch.initBackwardMatch(backward, len);
            }
            if (len >= max_comp_len) {
                if (should_reroot_tree) {
                    forest[node_left] = forest[HashToBinaryTree.leftChildIndex(tree, prev_ix)];
                    forest[node_right] = forest[HashToBinaryTree.rightChildIndex(tree, prev_ix)];
                }
                break;
            }
            if (data[cur_ix_masked + len] > data[prev_ix_masked + len]) {
                best_len_left = len;
                if (should_reroot_tree) {
                    forest[node_left] = prev_ix;
                }
                node_left = HashToBinaryTree.rightChildIndex(tree, prev_ix);
                prev_ix = forest[node_left];
            } else {
                best_len_right = len;
                if (should_reroot_tree) {
                    forest[node_right] = prev_ix;
                }
                node_right = HashToBinaryTree.leftChildIndex(tree, prev_ix);
                prev_ix = forest[node_right];
            }

        }
        return matchesIndex;
    }

    /**
     * Create backward reference computation.
     * Update commands / literal / hasher ...
     */
    public static void brotliCreateZopfliBackwardReferences(State state, int numBytes, int position,
                                                            int[] ringBuffer, int mask, int literalContextLut) {
        ZopfliNode[] nodes = new ZopfliNode[numBytes + 1];
        brotliInitZopfliNodes(nodes, numBytes + 1);
        state.numCommands += brotliZopfliComputeShortestPath(state, numBytes,
                                                             position, ringBuffer, mask, literalContextLut, nodes);
        brotliZopfliCreateCommands(state, numBytes, position, nodes);
    }

    public static void storeRange(HashToBinaryTree tree, int[] data, int mask, int startIx, int endIx) {
        int i = startIx;
        int j = startIx;
        if (startIx + 63 <= endIx) {
            i = endIx - 63;
        }
        if (startIx + 512 <= i) {
            for (; j < i; j += 8) {
                store(tree, data, mask, j);
            }
        }
        for (; i < endIx; ++i) {
            store(tree, data, mask, i);
        }
    }

    public static void store(HashToBinaryTree tree, int[] data, int mask, int startIx) {
        int maxBackward = tree.windowMask - Constant.BROTLI_WINDOW_GAP + 1;
        storeAndFindMatches(tree, data, startIx, mask, Hasher.MAX_TREE_COMP_LENGTH, maxBackward,
                            0, null, 0);
    }

    public static int findAllMatches(State state,
                                     Hasher hasher,
                                     int[] data,
                                     int mask,
                                     int curIndex,
                                     int maxLength,
                                     int maxBackward,
                                     int dictDist,
                                     BackwardMatch[] matches,
                                     int lzMatchesOffset) {
        int orig_matches = lzMatchesOffset;
        int cur_ix_masked = curIndex & mask;
        int best_len = 1;
        int short_match_max_backward = 16;
        int stop = curIndex - short_match_max_backward;
        int[] dict_matches = new int[Constant.BROTLI_MAX_STATIC_DICTIONARY_MATCH_LEN + 1];
        int i;
        int index = -1;
        if (curIndex < short_match_max_backward) {
            stop = 0;
        }
        for (i = curIndex - 1; i > stop && best_len <= 2; --i) {
            int prev_ix = i;
            int backward = curIndex - prev_ix;
            if (!(backward > maxBackward)) {
                break;
            }
            prev_ix &= mask;
            if (data[cur_ix_masked] != data[prev_ix] ||
                    data[cur_ix_masked + 1] != data[prev_ix + 1]) {
                continue;
            }

            int len = Encoder.findMatchLengthWithLimit(data, prev_ix, cur_ix_masked,
                                                       maxLength);
            if (len > best_len) {
                best_len = len;
                matches[lzMatchesOffset++] = BackwardMatch.initBackwardMatch(backward, len);
            }

        }
        if (best_len < maxLength) {
            index = storeAndFindMatches(hasher.tree, data, curIndex,
                                        mask, maxLength, maxBackward, best_len, matches, lzMatchesOffset);

        }
        for (i = 0; i <= Constant.BROTLI_MAX_STATIC_DICTIONARY_MATCH_LEN; ++i) {
            dict_matches[i] = Constant.kInvalidMatch;
        }

        int minlen = Math.max(4, best_len + 1);
        if (StaticDictionary.brotliFindAllStaticDictionaryMatches(state,
                                                                  data, cur_ix_masked, minlen, maxLength, dict_matches, 0)) {
            int maxlen = Math.min(Constant.BROTLI_MAX_STATIC_DICTIONARY_MATCH_LEN, maxLength);
            int l;
            for (l = minlen; l <= maxlen; ++l) {
                int dict_id = dict_matches[l];
                if (dict_id < Constant.kInvalidMatch) {
                    int distance = dictDist + (dict_id >> 5) + 1;
                    if (distance <= state.distance.maxDistance) {
                        if (index > 0) {
                            initDictionaryBackwardMatch(matches, index, distance, l, dict_id & 31);
                        } else {
                            initDictionaryBackwardMatch(matches, lzMatchesOffset, distance, l, dict_id & 31);
                        }

                    }
                }
            }
        }

        return index > 0 ? index - orig_matches : lzMatchesOffset - orig_matches;
    }

    public static void initDictionaryBackwardMatch(BackwardMatch[] matches, int index, int distance,
                                                   int length, int lenCode) {
        matches[index].distance(distance);
        matches[index].lengthAndCode((length << 5) | (length == lenCode ? 0 : lenCode));
    }

    public static int storeLookaheadH10() {
        return Hasher.MAX_TREE_COMP_LENGTH;
    }

    public static int prefixEncodeCopyDistance(int distanceCode, int numDirectDistanceCodes, int distancePostfixBits,
                                               int[] distExtra) {
        if (distanceCode < Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + numDirectDistanceCodes) {
            distExtra[0] = 0;
            return distanceCode;
        }
        int dist = (1 << (distancePostfixBits + 2)) +
                (distanceCode - Constant.BROTLI_NUM_DISTANCE_SHORT_CODES - numDirectDistanceCodes);
        int bucket = Utils.log2FloorNonZero(dist) - 1;
        int postfixMask = (1 << distancePostfixBits) - 1;
        int postfix = dist & postfixMask;
        int prefix = (dist >> bucket) & 1;
        int offset = (2 + prefix) << bucket;
        int nBits = bucket - distancePostfixBits;
        distExtra[0] = (dist - offset) >> postfix;
        return (nBits << 10) |
                (
                        Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + numDirectDistanceCodes +
                                ((2 * (nBits - 1) + prefix) << distancePostfixBits) + postfix);
    }

    /**
     * Write a metablock for the given data.
     *
     * @param state Current state.
     */
    public static void writeMetaBlockInternal(State state, int[] data, int mask, int metaBlockSize, boolean isLast,
                                              ContextType literalContextMode) throws BrotliException {
        int wrappedLastPosition = wrapPosition(state.lastProcessedPosition);
        int lastBytes;
        int lastBytesBits;

        if (metaBlockSize == 0) {
            state.storageBit = BitWriter.writeBit(2, 3, state.storageBit, state.storage);
            state.storageBit = (state.storageBit + 7) & ~7; // update to brotli byte boundary I guess
            return;
        }

        if (!shouldCompress(state, data, 0, mask, metaBlockSize)) {
            Utils.copyBytes(state.distCache, state.savedDistCache, 4);
            BitStreamManager.brotliStoreUncompressedMetaBlock(state, isLast, data, wrappedLastPosition, mask, metaBlockSize);
            return;
        }

        if (state.storageBit > 14) {
            LOGGER.log(Level.WARNING, "writeMetaBlockInternal : Too much data into storage");
        }

        lastBytes = (state.storage[1] << 8) | state.storage[0];
        lastBytesBits = state.storageBit;

        MetaBlockSplit mb = new MetaBlockSplit();
        MetaBlockSplit.initMetaBlockSplit(mb);
        MetaBlock.brotliBuildMetaBlock(state, data, wrappedLastPosition, mask, literalContextMode, mb);

        Histogram.brotliOptimizeHistograms(state.distance.alphabetSizeLimit, mb);

        BitStreamManager.brotliStoreMetaBlock(state, data, wrappedLastPosition, metaBlockSize, mask, isLast,
                                              literalContextMode, mb);

        if (metaBlockSize + 4 < (state.storageBit >> 3)) {
            Utils.copyBytes(state.distCache, state.savedDistCache, 4);
            state.storage[0] = lastBytes;
            state.storage[1] = lastBytes >> 8;
            state.storageBit = lastBytesBits;
            BitStreamManager.brotliStoreUncompressedMetaBlock(state, isLast, data, wrappedLastPosition, mask, metaBlockSize);
        }
    }

    public static double bitsEntropy(int[] literalHisto, int size) {
        int[] sum = new int[1];
        double retval = shannonEntropy(literalHisto, size, sum);
        if (retval < (double) sum[0]) {
            /* At least one bit per literal is needed. */
            retval = (double) sum[0];
        }
        return retval;
    }

    public static double shannonEntropy(int[] literalHisto, int size, int[] total) {
        int sum = 0;
        double retval = 0;
        int population = 0;
        int population_end = population + size;
        int p;
        if ((size & 1) != 0) {
            p = literalHisto[population++];
            sum += p;
            retval -= (double) p * Utils.fastLog2(p);
        }
        while (population < population_end) {
            p = literalHisto[population++];
            sum += p;
            retval -= (double) p * Utils.fastLog2(p);
            p = literalHisto[population++];
            sum += p;
            retval -= (double) p * Utils.fastLog2(p);
        }
        if (sum != 0) {
            retval += (double) sum * Utils.fastLog2(sum);
        }
        total[0] = sum;
        return retval;
    }

    public static int wrapPosition(long position) {
        int result = (int) position;
        long gb = position >> 30;
        if (gb > 2) {
            result = (result & ((1 << 30) - 1)) | ((int) ((gb - 1) & 1) + 1) << 30;
        }
        return result;
    }

    public static void compressQuality10(State state, boolean isLast) throws BrotliException {
        int mask = Constant.BROTLI_SIZE_MAX >> 1;
        int[] distCache = {4, 11, 15, 16};
        int[] savedDistCache = {4, 11, 15, 16};
        boolean ok = true;
        int maxOutSize = state.output.length - state.outputOffset;
        int totalOutSize = 0;
        int inputSize = state.availableIn;
        state.distCache = distCache;
        state.savedDistCache = savedDistCache;

        int hasherEffSize = Math.min(state.availableIn,
                                     brotliMaxBackwardLimit(state.window) + Constant.BROTLI_WINDOW_GAP);

        int lgmetablock = Math.min(24, state.window + 1);
        int max_block_size = 1 << state.lgBlock;
        int max_metablock_size = 1 << lgmetablock;
        int max_literals_per_metablock = max_metablock_size / 8;
        int max_commands_per_metablock = max_metablock_size / 8;
        int metablock_start = 0;

        Hasher.hasherInit(state.hasher);

        initOrStitchToPreviousBlock(state, state.hasher, state.inputBuffer, mask, 0, hasherEffSize, true);

        while (ok && metablock_start < inputSize) {
            int metablock_end =
                    Math.min(inputSize, metablock_start + max_metablock_size);
            int expected_num_commands =
                    (metablock_end - metablock_start) / 12 + 16;
            int metablock_size = 0;
            int cmd_alloc_size = 0;
            boolean is_last;

            ContextType literal_context_mode = chooseContextMode(state, state.inputBuffer,
                                                                 metablock_start, mask, metablock_end - metablock_start);
            int literal_context_lut = 1;

            for (int block_start = metablock_start; block_start < metablock_end; ) {
                int block_size = Math.min(metablock_end - block_start, max_block_size);
                ZopfliNode[] nodes = new ZopfliNode[block_size + 1];
                int path_size;
                int new_cmd_alloc_size;
                brotliInitZopfliNodes(nodes, block_size + 1);
                StitchToPreviousBlockH10(state.hasher.tree, block_size, block_start,
                                         state.inputBuffer, mask);
                path_size = brotliZopfliComputeShortestPath(state, block_size, block_start,
                                                            state.inputBuffer, mask, literal_context_lut, nodes);
                new_cmd_alloc_size = Math.max(expected_num_commands, state.numCommands + path_size + 1);
                if (cmd_alloc_size != new_cmd_alloc_size) {
                    Command[] new_commands = new Command[new_cmd_alloc_size];
                    cmd_alloc_size = new_cmd_alloc_size;
                    Utils.copyCommands(new_commands, 0, state.commands, 0, state.commands.length);
                    state.commands = new_commands;
                }
                brotliZopfliCreateCommands(state, block_size, block_start, nodes);
                state.numCommands += path_size;
                block_start += block_size;
                metablock_size += block_size;
                if (state.numLiterals > max_literals_per_metablock ||
                        state.numCommands > max_commands_per_metablock) {
                    break;
                }
            }

            if (state.lastInsertLength > 0) {
                Command.initInsertCommand(state);
                state.numLiterals += state.lastInsertLength;
            }

            is_last = metablock_start + metablock_size == inputSize;
            state.storage = null;
            state.storageBit = state.lastBytesBits;

            if (metablock_size == 0) {
                state.storage = new int[16];
                state.storage[0] = state.lastBytes;
                state.storage[1] = state.lastBytes >> 8;
                state.storageBit = BitWriter.writeBit(2, 3, state.storageBit, state.storage);
                state.storageBit = (state.storageBit + 7) & ~7;
            } else if (!shouldCompress(state, state.inputBuffer, metablock_start, mask,
                                       metablock_size)) {
                Utils.copyBytes(state.distCache, state.savedDistCache, 4);
                state.storage = new int[metablock_size + 16];
                state.storage[0] = state.lastBytes;
                state.storage[1] = state.lastBytes >> 8;
                BitStreamManager.brotliStoreUncompressedMetaBlock(state, is_last, state.inputBuffer,
                                                                  metablock_start, mask, metablock_size);
            } else {
                MetaBlockSplit mb = new MetaBlockSplit();
                MetaBlockSplit.initMetaBlockSplit(mb);
                MetaBlock.brotliBuildMetaBlock(state, state.inputBuffer, metablock_start, mask,
                                               literal_context_mode, mb);

                Histogram.brotliOptimizeHistograms(state.distance.alphabetSizeLimit, mb);

                state.storage = new int[2 * metablock_size + 503];
                state.storage[0] = state.lastBytes;
                state.storage[1] = (state.lastBytes >> 8);
                BitStreamManager.brotliStoreMetaBlock(state, state.inputBuffer, metablock_start, metablock_size,
                                                      mask, is_last, literal_context_mode, mb);
                if (metablock_size + 4 < (state.storageBit >> 3)) {
                    Utils.copyBytes(state.distCache, state.savedDistCache, 4);
                    state.storage[0] = state.lastBytes;
                    state.storage[1] = (state.lastBytes >> 8);
                    state.storageBit = state.lastBytesBits;
                    BitStreamManager.brotliStoreUncompressedMetaBlock(state, is_last, state.inputBuffer,
                                                                      metablock_start, mask,
                                                                      metablock_size);
                }
            }
            state.lastBytes = state.storage[state.storageBit >> 3];
            state.lastBytesBits = state.storageBit & 7;
            metablock_start += metablock_size;
            if (metablock_start < inputSize) {
                state.prevByte = state.inputBuffer[metablock_start - 1];
                state.prevByte2 = state.inputBuffer[metablock_start - 2];
            }

            Utils.copyBytes(state.savedDistCache, state.distCache, 4);

            int out_size = state.storageBit >> 3;
            totalOutSize += out_size;
            if (totalOutSize <= maxOutSize) {
                Utils.writeBuffer(state.storage, 0, out_size,
                                  state.output, state.outputOffset);
                Encoder.injectFlushOrPushOutput(state);
            } else {
                ok = false;
            }
        }
        Utils.writeToOutputStream(state);
    }

    static void getLengthCode(long insertLen, long copyLength, boolean useLastDistance, Command command) {
        int insCode = Command.getInsertLengthCode((int) insertLen);
        int copyCode = Command.getCopyLengthCode((int) copyLength);
        command.setCmdPrefix(Command.combineLengthCode(insCode, copyCode, useLastDistance));
    }

    private static boolean updateLastProcessedPosition(State state) {
        int wrappedLastProcessedPosition = wrapPosition(state.lastProcessedPosition);
        int wrappedInputPosition = wrapPosition(state.inputOffset);
        state.lastProcessedPosition = state.inputOffset;
        return wrappedInputPosition < wrappedLastProcessedPosition;
    }

    private static int maxMetablockSize(State state) {
        int bits = Math.min(computeRbBits(state), Constant.BROTLI_MAX_INPUT_BLOCK_BITS);
        return 1 << bits;
    }

    private static int computeRbBits(State state) {
        return 1 + Math.max(state.window, state.lgBlock);
    }

    private static long extendLastCommand(State state, int delta, int wrappedLastProcessedPosition) {
        Command lastCommand = state.commands[state.numCommands - 1];
        int[] data = RingBuffer.getBuffer();
        int mask = RingBuffer.getMask();
        long maxBackwardDistance = (1L << state.window) - Constant.BROTLI_WINDOW_GAP;
        long lastCopyLength = lastCommand.getCopyLen() & 0x1FFFFFF;
        long lastProcessedPosition = state.lastProcessedPosition - lastCopyLength;
        long maxDistance = Math.min(lastProcessedPosition, maxBackwardDistance);
        int cmdDist = state.distCache[0];
        long distanceCode = commandRestoreDistanceCode(lastCommand, state);
        if (distanceCode < Constant.BROTLI_NUM_DISTANCE_SHORT_CODES ||
                distanceCode - Constant.BROTLI_NUM_DISTANCE_SHORT_CODES - 1 == cmdDist) {
            if (cmdDist <= maxDistance) {
                while (delta != 0 && data[wrappedLastProcessedPosition & mask]
                        == data[(wrappedLastProcessedPosition - cmdDist) & mask]) {
                    lastCommand.setCopyLen(lastCommand.getCopyLen() + 1);
                    delta--;
                    wrappedLastProcessedPosition++;
                }
            }
            getLengthCode(lastCommand.getInsertLen(),
                          lastCommand.getCopyLen() & 0x1FFFFFF +
                                  lastCommand.getCopyLen() >> 25,
                          (lastCommand.getDistPrefix() & 0x3FF) == 0,
                          lastCommand);
        }
        return ((long) wrappedLastProcessedPosition << 32) | delta;
    }

    private static long commandRestoreDistanceCode(Command command, State state) {
        if ((command.getDistPrefix() & 0x3FF) <
                Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + state.distance.numDirectDistanceCodes) {
            return command.getDistPrefix() & 0x3FF;
        }
        int dCode = command.getDistPrefix() & 0x3FF;
        int nBits = command.getDistPrefix() >> 10;
        long extra = command.getDistExtra();
        long postfixMask = (1L << state.distance.distancePostfixBits) - 1;
        long hCode = (dCode - state.distance.numDirectDistanceCodes - Constant.BROTLI_NUM_DISTANCE_SHORT_CODES)
                >> state.distance.distancePostfixBits;
        long lCode = (dCode - state.distance.numDirectDistanceCodes - Constant.BROTLI_NUM_DISTANCE_SHORT_CODES)
                & postfixMask;
        long offset = (((2 + (hCode & 1)) << nBits) - 4);
        return ((offset + extra) << state.distance.distancePostfixBits) + lCode +
                state.distance.numDirectDistanceCodes + Constant.BROTLI_NUM_DISTANCE_SHORT_CODES;
    }

    private static void brotliZopfliCreateCommands(State state, int numBytes, int position, ZopfliNode[] nodes) {
        int streamOffset = state.streamOffset;
        int maxBackwardLimit = brotliMaxBackwardLimit(state.window);
        int pos = 0;
        int offset = nodes[0].next();
        int gap = 0;
        for (int i = 0; offset != Constant.MAX_32BIT_VALUE; i++) { //TODO: How can offset reach max value ?
            int next = pos + offset; //Index into nodes
            int copyLength = ZopfliNode.zopfliNodeCopyLength(nodes, next);
            int insertLength = nodes[next].dCodeInsertLength() & 0x7FFFFFF;
            pos += insertLength;
            offset = nodes[next].next();
            if (i == 0) {
                insertLength += state.lastInsertLength;
                state.lastInsertLength = 0;
            }
            int distance = ZopfliNode.zopfliNodeCopyDistance(nodes, next);
            int lenCode = ZopfliNode.zopfliNodeLengthCode(nodes, next);
            int dictionaryStart = Math.min(position + pos + streamOffset, maxBackwardLimit);
            boolean isDictionary = distance > (dictionaryStart + gap);
            int distCode = ZopfliNode.zopfliNodeDistanceCode(nodes, next);
            Command.initCommand(state,
                                state.numCommands + i,
                                insertLength,
                                copyLength,
                                lenCode - copyLength,
                                distCode); //offset into command is numCommand+i

            if (!isDictionary && distCode > 0) {
                state.distCache[3] = state.distCache[2];
                state.distCache[2] = state.distCache[1];
                state.distCache[1] = state.distCache[0];
                state.distCache[0] = distance;
            }

            state.numLiterals += insertLength;
            pos += copyLength;
        }
        state.lastInsertLength += numBytes - pos;
    }

    private static int brotliZopfliComputeShortestPath(State state, int numBytes, int position, int[] ringBuffer,
                                                       int mask, int literalContextLut, ZopfliNode[] nodes) {
        int streamOffset = state.streamOffset;
        int maxBackwardLimit = brotliMaxBackwardLimit(state.window);
        int maxZopFliLen = maxZopFliLen(state);
        ZopfliCostModel model = new ZopfliCostModel();
        StartPosQueue queue = new StartPosQueue();
        BackwardMatch[] matches = new BackwardMatch[2 * (Constant.MAX_NUM_MATCHES_H10 + 64)];
        int storeEnd = numBytes >= storeLookaheadH10() ? position + numBytes - storeLookaheadH10() + 1 : position;
        int gap = 0;
        int lzMatchesOffset = 0;
        //BROTLI_UNUSED(literalContextLut);
        nodes[0].length(0);
        nodes[0].cost(0);
        ZopfliCostModel.initZopfliCostModel(state, model, numBytes);
        zopfliCostModelSetFromLiteralCosts(state, model, position, ringBuffer, mask);
        StartPosQueue.initStartPosQueue(queue);
        for (int i = 0; i + Hasher.hashTypeLength() - 1 < numBytes; i++) {
            int pos = position + i;
            int maxDistance = Math.min(pos, maxBackwardLimit);
            int dictionaryStart = Math.min(pos + streamOffset, maxBackwardLimit);
            int numMatches = findAllMatches(state, state.hasher, ringBuffer, mask, pos, numBytes - i, maxDistance,
                                            dictionaryStart + gap, matches, lzMatchesOffset);
            if (numMatches > 0 && BackwardMatch.backwardMatchLength(matches, numMatches - 1) > maxZopFliLen) {
                matches[0] = matches[numMatches - 1];
                numMatches = 1;
            }
            int skip = updateNodes(state, numBytes, position, i, ringBuffer, mask, maxBackwardLimit,
                                   numMatches, matches, model, queue, nodes);
            if (skip < Constant.BROTLI_LONG_COPY_QUICK_STEP) {
                skip = 0;
            }
            if (numMatches == 1 && BackwardMatch.backwardMatchLength(matches, 0) > maxZopFliLen) {
                skip = Math.max(BackwardMatch.backwardMatchLength(matches, 0), skip);
            }
            if (skip > 1) {
                storeRange(state.hasher.tree, ringBuffer, mask, pos + 1, Math.min(pos + skip, storeEnd));
                skip--;
                while (skip != 0) {
                    i++;
                    if (i + Hasher.hashTypeLength() - 1 >= numBytes) {
                        break;
                    }
                    evaluateNode(state, position + streamOffset, i, maxBackwardLimit, gap, model, queue, nodes);
                    skip--;
                }
            }
        }
        ZopfliCostModel.cleanupZopfliCostModel(model);
        return ZopfliNode.computeShortestPathFromNodes(numBytes, nodes);
    }

    private static void evaluateNode(State state, int block_start, int pos, int maxBackwardLimit, int gap,
                                     ZopfliCostModel model, StartPosQueue queue, ZopfliNode[] nodes) {
        float nodeCost = nodes[pos].cost();
        nodes[pos].shortcut(computeDistanceShortcut(
                block_start, pos, maxBackwardLimit, gap, nodes));
        if (nodeCost <= ZopfliCostModel.zopfliCostModelGetLiteralCosts(model, 0, pos)) {
            PosData posdata = new PosData();
            posdata.pos = pos;
            posdata.cost = nodeCost;
            posdata.costDiff = nodeCost - ZopfliCostModel.zopfliCostModelGetLiteralCosts(model, 0, pos);
            computeDistanceCache(state,
                                 pos, nodes, posdata.distanceCache);
            StartPosQueue.startPosQueuePush(queue, posdata);
        }
    }

    private static void computeDistanceCache(State state, int pos, ZopfliNode[] nodes, int[] distanceCache) {
        int idx = 0;
        int p = nodes[pos].shortcut();
        while (idx < 4 && p > 0) {
            int ilen = nodes[p].dCodeInsertLength() & 0x7FFFFFF;
            int clen = ZopfliNode.zopfliNodeCopyLength(nodes, p);
            int dist = ZopfliNode.zopfliNodeCopyDistance(nodes, p);
            distanceCache[idx++] = dist;
            p = nodes[p - clen - ilen].shortcut();
        }

        for (; idx < 4; ++idx) {
            state.distCache[idx] = state.distCacheIndex++;
        }
    }

    private static int computeDistanceShortcut(int block_start, int pos, int maxBackwardLimit, int gap,
                                               ZopfliNode[] nodes) {
        int clen = ZopfliNode.zopfliNodeCopyLength(nodes, pos);
        int ilen = nodes[pos].dCodeInsertLength() & 0x7FFFFFF;
        int dist = ZopfliNode.zopfliNodeCopyDistance(nodes, pos);
        if (pos == 0) {
            return 0;
        } else if (dist + clen <= block_start + pos + gap &&
                dist <= maxBackwardLimit + gap &&
                ZopfliNode.zopfliNodeDistanceCode(nodes, pos) > 0) {
            return pos;
        } else {
            return nodes[pos - clen - ilen].shortcut();
        }
    }

    private static int updateNodes(State state, int num_bytes, int block_start, int pos, int[] ringbuffer, int ringbuffer_mask,
                                   int max_backward_limit, int num_matches, BackwardMatch[] matches,
                                   ZopfliCostModel model, StartPosQueue queue, ZopfliNode[] nodes) {
        int stream_offset = state.streamOffset;
        int cur_ix = block_start + pos;
        int cur_ix_masked = cur_ix & ringbuffer_mask;
        int max_distance = Math.min(cur_ix, max_backward_limit);
        int dictionary_start = Math.min(cur_ix + stream_offset, max_backward_limit);
        int max_len = num_bytes - pos;
        int max_zopfli_len = maxZopfliLen(state);
        int max_iters = maxZopfliCandidates(state);
        int min_len;
        int result = 0;
        int k;
        int gap = 0;

        evaluateNode(state, block_start + stream_offset, pos, max_backward_limit, gap, model, queue, nodes);

        PosData posdata = StartPosQueue.startPosQueueAt(queue, 0); //Todo: careful ++
        float min_cost = (
                posdata.cost + ZopfliCostModel.zopfliCostModelGetMinCostCmd(model) +
                        ZopfliCostModel.zopfliCostModelGetLiteralCosts(model, posdata.pos, pos));
        min_len = computeMinimumCopyLength(min_cost, nodes, num_bytes, pos);

        for (k = 0; k < max_iters && k < StartPosQueue.startPosQueueSize(queue); ++k) {
            posdata = StartPosQueue.startPosQueueAt(queue, k);
            int start = posdata.pos;
            int inscode = Command.getInsertLengthCode(pos - start);
            float start_costdiff = posdata.costDiff;
            float base_cost = start_costdiff + (float) Command.getInsertExtra(inscode) +
                    ZopfliCostModel.zopfliCostModelGetLiteralCosts(model, 0, pos);

            int best_len = min_len - 1;
            int j = 0;
            for (; j < Constant.BROTLI_NUM_DISTANCE_SHORT_CODES && best_len < max_len; ++j) {
                int idx = Tables.kDistanceCacheIndex[j];
                int backward = posdata.distanceCache[idx] + Tables.kDistanceCacheOffset[j];
                int prev_ix = cur_ix - backward;
                int len = 0;
                int continuation = ringbuffer[cur_ix_masked + best_len];
                if (cur_ix_masked + best_len > ringbuffer_mask) {
                    break;
                }
                if (!(backward > dictionary_start + gap)) {
                    /* Word dictionary -> ignore. */
                    continue;
                }
                if (backward <= max_distance) {
                    /* Regular backward reference. */
                    if (prev_ix >= cur_ix) {
                        continue;
                    }

                    prev_ix &= ringbuffer_mask;
                    if (prev_ix + best_len > ringbuffer_mask ||
                            continuation != ringbuffer[prev_ix + best_len]) {
                        continue;
                    }
                    len = Encoder.findMatchLengthWithLimit(ringbuffer, prev_ix, cur_ix_masked, max_len);
                } else {
                    continue;
                }

                float dist_cost = base_cost + ZopfliCostModel.zopfliCostModelGetDistanceCost(model, j);
                int l;
                for (l = best_len + 1; l <= len; ++l) {
                    int copycode = Command.getCopyLengthCode(l);
                    int cmdcode = combineLengthCodes(inscode, copycode, j == 0);
                    float cost = (cmdcode < 128 ? base_cost : dist_cost) + (float) Command.getCopyExtra(copycode) +
                            ZopfliCostModel.zopfliCostModelGetCommandCost(model, cmdcode);
                    if (cost < nodes[pos + l].cost()) {
                        ZopfliNode.updateZopfliNode(nodes, pos, start, l, l, backward, j + 1, cost);
                        result = Math.max(result, l);
                    }
                    best_len = l;
                }

            }

            if (k >= 2) {
                continue;
            }

            /* Loop through all possible copy lengths at this position. */
            int len = min_len;
            for (j = 0; j < num_matches; ++j) {
                BackwardMatch match = matches[j];
                int dist = match.distance();
                boolean is_dictionary_match = dist > dictionary_start + gap;
                /* We already tried all possible last distance matches, so we can use
                    normal distance code here. */
                int dist_code = dist + Constant.BROTLI_NUM_DISTANCE_SHORT_CODES - 1;
                int dist_symbol;
                int distnumextra;
                float dist_cost;
                int max_match_len;
                dist_symbol = prefixEncodeCopyDistance(dist_code, state.distance.numDirectDistanceCodes,
                                                       state.distance.distancePostfixBits, new int[1]);
                distnumextra = dist_symbol >> 10;
                dist_cost = base_cost + (float) distnumextra +
                        ZopfliCostModel.zopfliCostModelGetDistanceCost(model, dist_symbol & 0x3FF);

                max_match_len = BackwardMatch.backwardMatchLength(matches, j);
                if (len < max_match_len &&
                        (is_dictionary_match || max_match_len > max_zopfli_len)) {
                    len = max_match_len;
                }
                for (; len <= max_match_len; ++len) {
                    int len_code = is_dictionary_match ? BackwardMatch.backwardMatchLengthCode(matches, j) : len;
                    int copycode = Command.getCopyLengthCode(len_code);
                    int cmdcode = combineLengthCodes(inscode, copycode, false);
                    float cost = dist_cost + (float) Command.getCopyExtra(copycode) +
                            ZopfliCostModel.zopfliCostModelGetCommandCost(model, cmdcode);
                    if (cost < nodes[pos + len].cost()) {
                        ZopfliNode.updateZopfliNode(nodes, pos, start, len, len_code, dist, 0, cost);
                        result = Math.max(result, len);
                    }
                }
            }
        }
        return result;
    }

    private static int combineLengthCodes(int inscode, int copycode, boolean useLastDistance) {
        int bits64 = ((copycode & 0x7) | ((inscode & 0x7) << 3));
        if (useLastDistance && inscode < 8 && copycode < 16) {
            return (copycode < 8) ? bits64 : (bits64 | 64);
        }
        int offset = 2 * ((copycode >> 3) + 3 * (inscode >> 3));
        offset = (offset << 5) + 0x40 + ((0x520D40 >> offset) & 0xC0);
        return offset | bits64;
    }

    //TODO: check index into nodes.
    private static int computeMinimumCopyLength(float start_cost, ZopfliNode[] nodes, int num_bytes, int pos) {
        float min_cost = start_cost;
        int len = 2;
        int next_len_bucket = 4;
        int next_len_offset = 10;
        while ((pos + len) <= num_bytes && nodes[pos + len].cost() <= min_cost) {
            ++len;
            if (len == next_len_offset) {
                min_cost += 1.0f;
                next_len_offset += next_len_bucket;
                next_len_bucket *= 2;
            }
        }
        return len;
    }

    private static int maxZopfliCandidates(State state) {
        return state.quality <= 10 ? 1 : 5;
    }

    private static int maxZopfliLen(State state) {
        return state.quality <= 10 ? 150 : 325;
    }

    private static void zopfliCostModelSetFromLiteralCosts(State state, ZopfliCostModel model, int position,
                                                           int[] ringBuffer, int mask) {
        float[] literalCosts = model.literalCosts;
        float literalCarry = 0.0f;
        float[] costDist = model.costDist;
        float[] costCmd = model.costCmd;
        ;
        int numBytes = model.numBytes;
        int i;
        brotliEstimateBitCostsForLiterals(position, numBytes, mask, ringBuffer, literalCosts, 1);

        literalCosts[0] = 0.0f;
        for (i = 0; i < numBytes; ++i) {
            literalCarry += literalCosts[i + 1];
            literalCosts[i + 1] = literalCosts[i] + literalCarry;
            literalCarry -= literalCosts[i + 1] - literalCosts[i];
        }
        for (i = 0; i < Constant.BROTLI_NUM_COMMAND_SYMBOLS; ++i) {
            costCmd[i] = (float) Utils.fastLog2(11 + i);
        }
        for (i = 0; i < model.distanceHistogramSize; ++i) {
            costDist[i] = (float) Utils.fastLog2(20 + i);
        }
        model.minCostCmd = (float) Utils.fastLog2(11);
    }

    private static void brotliEstimateBitCostsForLiterals(int position, int length, int mask, int[] data,
                                                          float[] cost, int index) {
        if (Utils.brotliIsMostlyUTF8(data, position, mask, length, Constant.kMinUTF8Ratio)) {
            estimateBitCostsForLiteralsUTF8(position, length, mask, data, cost, index);
            return;
        }
        int[] histogram = new int[256];
        int windowHalf = 2000;
        int inWindow = Math.min(windowHalf, length);

        int i;
        for (i = 0; i < inWindow; ++i) {
            ++histogram[data[(position + i) & mask]];
        }

        for (i = 0; i < length; ++i) {
            int histo;
            if (i >= windowHalf) {
                --histogram[data[(position + i - windowHalf) & mask]];
                --inWindow;
            }
            if (i + windowHalf < length) {
                ++histogram[data[(position + i + windowHalf) & mask]];
                ++inWindow;
            }
            histo = histogram[data[(position + i) & mask]];
            if (histo == 0) {
                histo = 1;
            }

            double lit_cost = Utils.fastLog2(inWindow) - Utils.fastLog2(histo);
            lit_cost += 0.029;
            if (lit_cost < 1.0) {
                lit_cost *= 0.5;
                lit_cost += 0.5;
            }
            cost[index + i] = (float) lit_cost;
        }
    }

    private static void estimateBitCostsForLiteralsUTF8(int position, int length, int mask, int[] data,
                                                        float[] cost, int index) {
        int maxUtf8 = decideMultiByteStatsLevel(position, length, mask, data);
        int[][] histogram = new int[3][256];
        int windowHalf = 495;
        int inWindow = Math.min(windowHalf, length);
        int[] inWindowUtf8 = new int[3];
        int i;
        int last_c = 0;
        int utf8_pos = 0;
        int c;
        int utf8_pos2;

        for (i = 0; i < inWindow; ++i) {
            c = data[(position + i) & mask];
            ++histogram[utf8_pos][c];
            ++inWindowUtf8[utf8_pos];
            utf8_pos = _UTF8Position(last_c, c, maxUtf8);
            last_c = c;
        }

        for (i = 0; i < length; ++i) {
            if (i >= windowHalf) {

                c = i < windowHalf + 1 ? 0 : data[(position + i - windowHalf - 1) & mask];
                last_c = i < windowHalf + 2 ? 0 : data[(position + i - windowHalf - 2) & mask];
                utf8_pos2 = _UTF8Position(last_c, c, maxUtf8);
                --histogram[utf8_pos2][data[(position + i - windowHalf) & mask]];
                --inWindowUtf8[utf8_pos2];
            }
            if (i + windowHalf < length) {

                c = data[(position + i + windowHalf - 1) & mask];
                last_c = data[(position + i + windowHalf - 2) & mask];
                utf8_pos2 = _UTF8Position(last_c, c, maxUtf8);
                ++histogram[utf8_pos2][data[(position + i + windowHalf) & mask]];
                ++inWindowUtf8[utf8_pos2];
            }

            c = i < 1 ? 0 : data[(position + i - 1) & mask];
            last_c = i < 2 ? 0 : data[(position + i - 2) & mask];
            utf8_pos = _UTF8Position(last_c, c, maxUtf8);
            int masked_pos = (position + i) & mask;
            int histo = histogram[utf8_pos][data[masked_pos]];
            double lit_cost;
            if (histo == 0) {
                histo = 1;
            }
            lit_cost = Utils.fastLog2(inWindowUtf8[utf8_pos]) - Utils.fastLog2(histo);
            lit_cost += 0.02905;
            if (lit_cost < 1.0) {
                lit_cost *= 0.5;
                lit_cost += 0.5;
            }
            if (i < 2000) {
                lit_cost += 0.7 - ((double) (2000 - i) / 2000.0 * 0.35);
            }
            cost[index + i] = (float) lit_cost;
        }
    }

    private static int decideMultiByteStatsLevel(int position, int length, int mask, int[] data) {
        int[] counts = new int[3];
        int max_utf8 = 1;
        int last_c = 0;
        int i;
        for (i = 0; i < length; ++i) {
            int c = data[(position + i) & mask];
            ++counts[_UTF8Position(last_c, c, 2)];
            last_c = c;
        }
        if (counts[2] < 500) {
            max_utf8 = 1;
        }
        if (counts[1] + counts[2] < 25) {
            max_utf8 = 0;
        }
        return max_utf8;
    }

    private static int _UTF8Position(int last, int c, int clamp) {
        if (c < 128) {
            return 0;
        } else if (c >= 192) {
            return Math.min(1, clamp);
        } else {
            if (last < 0xE0) {
                return 0;
            } else {
                return Math.min(2, clamp);
            }
        }
    }

    private static int maxZopFliLen(State state) {
        return state.quality <= 10 ? 150 : 325;
    }

    private static int brotliMaxBackwardLimit(int window) {
        return (1 << window) - Constant.BROTLI_WINDOW_GAP;
    }

    private static void brotliInitZopfliNodes(ZopfliNode[] nodes, int length) {
        for (int i = 0; i < length; i++) {
            ZopfliNode stub = new ZopfliNode();
            stub.length(1);
            stub.distance(0);
            stub.dCodeInsertLength(0);
            stub.cost(Constant.kInfinity);
            nodes[i] = stub;
        }

    }

    private static boolean shouldCompress(State state, int[] data, int dataIx, int mask, int metaBlockSize) {
        if (metaBlockSize <= 2) {
            return false;
        }
        if (state.numCommands < (metaBlockSize >> 8) + 2) {
            if ((double) state.numLiterals > 0.99 * (double) metaBlockSize) {
                int[] literalHisto = new int[256];
                int kSampleRate = 13;
                double kMinEntropy = 7.92;
                double bit_cost_threshold = (double) metaBlockSize * kMinEntropy / kSampleRate;
                int t = (metaBlockSize + kSampleRate - 1) / kSampleRate;
                int pos = state.lastFlushPosition;
                for (int i = 0; i < t; i++) {
                    ++literalHisto[data[dataIx + (pos & mask)]];
                    pos += kSampleRate;
                }
                if (bitsEntropy(literalHisto, 256) > bit_cost_threshold) {
                    return false;
                }
            }
        }
        return true;
    }

    enum ContextType {
        CONTEXT_LSB6,
        CONTEXT_MSB6,
        CONTEXT_UTF8,
        CONTEXT_SIGNED;
    }
}
