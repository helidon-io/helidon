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

class MetaBlockSplit {
    private static final System.Logger LOGGER = System.getLogger(MetaBlockSplit.class.getName());

    public BlockSplit literalsplit;
    public BlockSplit commandsplit;
    public BlockSplit distancesplit;
    public int[] literalContextMap;
    public int literalContextMapSize;
    public int[] distanceContextMap;
    public int distanceContextMapSize;
    public Histogram[] literalHistograms;
    public int literalHistogramsSize;
    public Histogram[] commandHistograms;
    public int commandHistogramsSize;
    public Histogram[] distanceHistograms;
    public int distanceHistogramsSize;

    public static void initMetaBlockSplit(MetaBlockSplit mb) {
        mb.literalsplit = new BlockSplit();
        mb.commandsplit = new BlockSplit();
        mb.distancesplit = new BlockSplit();
        BlockSplit.brotliInitBlockSplit(mb.literalsplit);
        BlockSplit.brotliInitBlockSplit(mb.commandsplit);
        BlockSplit.brotliInitBlockSplit(mb.distancesplit);
        mb.literalContextMap = new int[1];
        mb.literalContextMapSize = 1;
        mb.distanceContextMap = new int[1];
        mb.distanceContextMapSize = 1;
        mb.literalHistograms = new Histogram[1];
        mb.literalHistogramsSize = 1;
        mb.commandHistograms = new Histogram[1];
        mb.commandHistogramsSize = 1;
        mb.distanceHistograms = new Histogram[1];
        mb.distanceHistogramsSize = 1;
    }

    public static void brotliSplitBlock(Command[] commands, int numCommands, int[] data, int position, int mask,
                                        State state, BlockSplit literalsplit, BlockSplit commandsplit,
                                        BlockSplit distancesplit) throws BrotliException {

        int literals_count = countLiterals(commands, numCommands);
        int[] literals = new int[literals_count];

        copyLiteralsToByteArray(commands, numCommands, data, position, mask, literals);

        splitByteVector(
                literals, literals_count,
                Constant.kSymbolsPerLiteralHistogram, Constant.kMaxLiteralHistograms,
                Constant.kLiteralStrideLength, Constant.kLiteralBlockSwitchCost, state,
                literalsplit);

        int[] insert_and_copy_codes = new int[numCommands];
        int i;

        for (i = 0; i < numCommands; ++i) {
            insert_and_copy_codes[i] = commands[i].getCmdPrefix();
        }

        splitByteVector(insert_and_copy_codes, numCommands,
                        Constant.kSymbolsPerCommandHistogram, Constant.kMaxCommandHistograms,
                        Constant.kCommandStrideLength, Constant.kCommandBlockSwitchCost, state,
                        commandsplit);

        int[] distance_prefixes = new int[numCommands];
        int j = 0;

        for (i = 0; i < numCommands; ++i) {
            Command cmd = commands[i];
            if (Command.commandCopyLen(cmd) != 0 && cmd.getCmdPrefix() >= 128) {
                distance_prefixes[j++] = cmd.getDistPrefix() & 0x3FF;
            }
        }

        splitByteVector(
                distance_prefixes, j,
                Constant.kSymbolsPerDistanceHistogram, Constant.kMaxCommandHistograms,
                Constant.kDistanceStrideLength, Constant.kDistanceBlockSwitchCost, state,
                distancesplit);

    }

    public static void splitByteVector(int[] data, int length, int symbols_per_histogram,
                                       int max_histograms, int sampling_stride_length,
                                       double block_switch_cost, State state, BlockSplit split) throws BrotliException {

        Histogram[] histograms;
        int num_histograms = length / symbols_per_histogram + 1;
        if (num_histograms > max_histograms) {
            num_histograms = max_histograms;
        }

        if (length == 0) {
            split.numTypes = 1;
            return;
        }

        if (length < Constant.kMinLengthForBlockSplitting) {
            split.numTypes = 1;
            split.types[split.numBlocks] = 0;
            split.lengths[split.numBlocks] = length;
            split.numBlocks++;
            return;
        }
        histograms = new Histogram[num_histograms];
        for (int i = 0; i < num_histograms; i++) {
            histograms[i] = new Histogram();
        }
        initialEntropyCodes(data, length,
                            sampling_stride_length,
                            num_histograms, histograms);
        refineEntropyCodes(data, length,
                           sampling_stride_length,
                           num_histograms, histograms);

        int[] block_ids = new int[length];
        int num_blocks = 0;
        int bitmaplen = (num_histograms + 7) >> 3;
        double[] insert_cost = new double[num_histograms];
        double[] cost = new double[num_histograms];
        int[] switch_signal = new int[length * bitmaplen]; // Todo: probably less.
        int[] new_id = new int[num_histograms];
        int iters = state.quality < Constant.HQ_ZOPFLIFICATION_QUALITY ? 3 : 10;
        int i;
        for (i = 0; i < iters; ++i) {
            num_blocks = findBlocks(data, length,
                                    block_switch_cost,
                                    num_histograms, histograms,
                                    insert_cost, cost, switch_signal,
                                    block_ids);
            num_histograms = remapBlockIds(block_ids, length,
                                           new_id, num_histograms);
            buildBlockHistograms(data, length, block_ids,
                                 num_histograms, histograms);
        }
        clusterBlocks(data, length, num_blocks, block_ids, split);
    }

    public static void clusterBlocks(int[] data, int length, int num_blocks, int[] block_ids, BlockSplit split)
            throws BrotliException {
        int[] histogram_symbols = new int[num_blocks];
        int[] block_lengths = new int[num_blocks];
        int expected_num_clusters = Constant.CLUSTERS_PER_BATCH *
                (num_blocks + Constant.HISTOGRAMS_PER_BATCH - 1) / Constant.HISTOGRAMS_PER_BATCH;
        int all_histograms_size = 0;
        int all_histograms_capacity = expected_num_clusters;
        Histogram[] all_histograms = new Histogram[all_histograms_capacity];
        for (int i = 0; i < all_histograms_capacity; i++) {
            all_histograms[i] = new Histogram();
        }
        int cluster_size_size = 0;
        int cluster_size_capacity = expected_num_clusters;
        int[] cluster_size = new int[cluster_size_capacity];
        int num_clusters = 0;
        Histogram[] histograms = new Histogram[Math.min(num_blocks, Constant.HISTOGRAMS_PER_BATCH)];
        for (int i = 0; i < Math.min(num_blocks, Constant.HISTOGRAMS_PER_BATCH); i++) {
            histograms[i] = new Histogram();
        }
        int max_num_pairs = Constant.HISTOGRAMS_PER_BATCH * Constant.HISTOGRAMS_PER_BATCH / 2;
        int pairs_capacity = max_num_pairs + 1;
        HistogramPair[] pairs = new HistogramPair[pairs_capacity];
        for (int i = 0; i < pairs_capacity; i++) {
            pairs[i] = new HistogramPair();
        }
        int pos = 0;
        int[] clusters;
        int num_final_clusters;
        int kInvalidIndex = Integer.MAX_VALUE;
        int[] new_index;
        int i;
        int[] sizes = new int[Constant.HISTOGRAMS_PER_BATCH];
        int[] new_clusters = new int[Constant.HISTOGRAMS_PER_BATCH];
        int[] symbols = new int[Constant.HISTOGRAMS_PER_BATCH];
        int[] remap = new int[Constant.HISTOGRAMS_PER_BATCH];


        /* Calculate block lengths (convert repeating values -> series length). */
        int block_idx = 0;
        for (i = 0; i < length; ++i) {
            if (block_idx >= num_blocks) {
                LOGGER.log(Level.WARNING, "clusterBlocks : block_idx higher than number of blocks");
            }
            ++block_lengths[block_idx];
            if (i + 1 == length || block_ids[i] != block_ids[i + 1]) {
                ++block_idx;
            }
        }

        if (block_idx != num_blocks) {
            LOGGER.log(Level.WARNING, "clusterBlocks : block_idx different than number of blocks");
        }


        /* Pre-cluster blocks (cluster batches). */
        for (i = 0; i < num_blocks; i += Constant.HISTOGRAMS_PER_BATCH) {
            int num_to_combine = Math.min(num_blocks - i, Constant.HISTOGRAMS_PER_BATCH);
            int num_new_clusters;
            int j;
            for (j = 0; j < num_to_combine; ++j) {
                int k;
                int block_length = block_lengths[i + j];
                Histogram.clearHistogram(histograms[j]);
                for (k = 0; k < block_length; ++k) {
                    Histogram.addHistogram(histograms[j], data[pos++]);
                }
                histograms[j].bitCost = Histogram.brotliPopulationCost(histograms[j]);
                new_clusters[j] = j;
                symbols[j] = j;
                sizes[j] = 1;
            }
            num_new_clusters = Histogram.brotliHistogramCombine(
                    histograms, sizes, symbols, 0, new_clusters, 0, pairs, num_to_combine,
                    num_to_combine, Constant.HISTOGRAMS_PER_BATCH, max_num_pairs);
            for (j = 0; j < num_new_clusters; ++j) {
                all_histograms[all_histograms_size++] = histograms[new_clusters[j]];
                cluster_size[cluster_size_size++] = sizes[new_clusters[j]];
                remap[new_clusters[j]] = j;
            }
            for (j = 0; j < num_to_combine; ++j) {
                histogram_symbols[i + j] = num_clusters + remap[symbols[j]];
            }
            num_clusters += num_new_clusters;
            if (num_clusters != cluster_size_size) {
                LOGGER.log(Level.WARNING, "clusterBlocks : num_clusters different than cluster_size_size");
            }
            if (num_clusters != all_histograms_size) {
                LOGGER.log(Level.WARNING, "clusterBlocks : num_clusters different than all_histograms_size");
            }
        }

        /* Final clustering. */
        max_num_pairs = Math.min(64 * num_clusters, (num_clusters / 2) * num_clusters);
        if (pairs_capacity < max_num_pairs + 1) {
            //Todo : Should we dynamically extend memory ?
            //pairs = BROTLI_ALLOC(m, HistogramPair, max_num_pairs + 1);
        }
        clusters = new int[num_clusters];
        for (i = 0; i < num_clusters; ++i) {
            clusters[i] = i;
        }
        num_final_clusters = Histogram.brotliHistogramCombine(
                all_histograms, cluster_size, histogram_symbols, 0, clusters, 0, pairs,
                num_clusters, num_blocks, Constant.BROTLI_MAX_NUMBER_OF_BLOCK_TYPES,
                max_num_pairs);

        /* Assign blocks to final histograms. */
        new_index = new int[num_clusters];
        for (i = 0; i < num_clusters; ++i) {
            new_index[i] = kInvalidIndex;
        }
        pos = 0;

        int next_index = 0;
        for (i = 0; i < num_blocks; ++i) {
            Histogram histo = new Histogram();
            int j;
            int best_out;
            double best_bits;
            Histogram.clearHistogram(histo);
            for (j = 0; j < block_lengths[i]; ++j) {
                Histogram.addHistogram(histo, data[pos++]);
            }
            /* Among equally good histograms prefer last used. */
            /* TODO: should we give a block-switch discount here? */
            best_out = (i == 0) ? histogram_symbols[0] : histogram_symbols[i - 1];
            best_bits = Histogram.brotliHistogramBitCostDistance(histo, all_histograms[best_out]);
            for (j = 0; j < num_final_clusters; ++j) {
                double cur_bits = Histogram.brotliHistogramBitCostDistance(histo, all_histograms[clusters[j]]);
                if (cur_bits < best_bits) {
                    best_bits = cur_bits;
                    best_out = clusters[j];
                }
            }
            histogram_symbols[i] = best_out;
            if (new_index[best_out] == kInvalidIndex) {
                new_index[best_out] = next_index++;
            }
        }

        /* Rewrite final assignment to block-split. There might be less blocks
         * than |num_blocks| due to clustering. */

        int cur_length = 0;
        block_idx = 0;
        int max_type = 0;
        for (i = 0; i < num_blocks; ++i) {
            cur_length += block_lengths[i];
            if (i + 1 == num_blocks ||
                    histogram_symbols[i] != histogram_symbols[i + 1]) {
                int id = new_index[histogram_symbols[i]];
                split.types[block_idx] = id;
                split.lengths[block_idx] = cur_length;
                max_type = Math.max(max_type, id);
                cur_length = 0;
                ++block_idx;
            }
        }
        split.numBlocks = block_idx;
        split.numTypes = max_type + 1;
    }

    public static void buildBlockHistograms(int[] data, int length, int[] block_ids, int num_histograms,
                                            Histogram[] histograms) {
        Histogram.clearHistograms(histograms, num_histograms);
        for (int i = 0; i < length; ++i) {
            Histogram.addHistogram(histograms[block_ids[i]], data[i]);
        }
    }

    public static int remapBlockIds(int[] block_ids, int length, int[] new_id, int num_histograms) {
        int kInvalidId = 256;
        int next_id = 0;
        int i;
        for (i = 0; i < num_histograms; ++i) {
            new_id[i] = kInvalidId;
        }
        for (i = 0; i < length; ++i) {
            if (block_ids[i] >= num_histograms) {
                LOGGER.log(Level.WARNING, "findBlocks : Too many histograms");
            }
            if (new_id[block_ids[i]] == kInvalidId) {
                new_id[block_ids[i]] = next_id++;
            }
        }
        for (i = 0; i < length; ++i) {
            block_ids[i] = new_id[block_ids[i]];
            if (block_ids[i] >= num_histograms) {
                LOGGER.log(Level.WARNING, "findBlocks : Too many histograms");
            }
        }
        if (next_id > num_histograms) {
            LOGGER.log(Level.WARNING, "findBlocks : Too many histograms");
        }
        return next_id;
    }

    public static int findBlocks(int[] data, int length, double block_switch_bitcost, int num_histograms,
                                 Histogram[] histograms, double[] insert_cost, double[] cost, int[] switch_signal,
                                 int[] block_ids) {
        int alphabet_size = 256;
        int bitmap_len = (num_histograms + 7) >> 3;
        int num_blocks = 1;
        int byte_ix;
        int i;
        int j;
        if (num_histograms > 256) {
            LOGGER.log(Level.WARNING, "findBlocks : Too many histograms");
        }

        /* Trivial case: single historgram -> single block type. */
        if (num_histograms <= 1) {
            for (i = 0; i < length; ++i) {
                block_ids[i] = 0;
            }
            return 1;
        }

        insert_cost = new double[alphabet_size * num_histograms];
        for (i = 0; i < num_histograms; ++i) {
            insert_cost[i] = Utils.fastLog2(histograms[i].totalCount);
        }
        for (i = alphabet_size; i != 0; ) {
            /* Reverse order to use the 0-th row as a temporary storage. */
            --i;
            for (j = 0; j < num_histograms; ++j) {
                insert_cost[i * num_histograms + j] =
                        insert_cost[j] - bitCost(histograms[j].data[i]);
            }
        }

        cost = new double[num_histograms];
        switch_signal = new int[length * bitmap_len];
        for (byte_ix = 0; byte_ix < length; ++byte_ix) {
            int ix = byte_ix * bitmap_len;
            int symbol = data[byte_ix];
            int insert_cost_ix = symbol * num_histograms;
            double min_cost = 1e99;
            double block_switch_cost = block_switch_bitcost;
            int k;
            for (k = 0; k < num_histograms; ++k) {
                /* We are coding the symbol with entropy code k. */
                cost[k] += insert_cost[insert_cost_ix + k];
                if (cost[k] < min_cost) {
                    min_cost = cost[k];
                    block_ids[byte_ix] = k;
                }
            }
            /* More blocks for the beginning. */
            if (byte_ix < 2000) {
                block_switch_cost *= 0.77 + 0.07 * (double) byte_ix / 2000;
            }
            for (k = 0; k < num_histograms; ++k) {
                cost[k] -= min_cost;
                if (cost[k] >= block_switch_cost) {
                    int mask = (1 << (k & 7));
                    cost[k] = block_switch_cost;
                    if ((k >> 3) >= bitmap_len) {
                        LOGGER.log(Level.WARNING, "findBlocks : Weird value");
                    }
                    switch_signal[ix + (k >> 3)] |= mask;
                }
            }
        }

        byte_ix = length - 1;
        /* Trace back from the last position and switch at the marked places. */
        int ix = byte_ix * bitmap_len;
        int cur_id = block_ids[byte_ix];
        while (byte_ix > 0) {
            int mask = (1 << (cur_id & 7));
            if ((cur_id >> 3) >= bitmap_len) {
                LOGGER.log(Level.WARNING, "findBlocks : Weird value");
            }
            --byte_ix;
            ix -= bitmap_len;
            if ((switch_signal[ix + (cur_id >> 3)] & mask) != 0) {
                if (cur_id != block_ids[byte_ix]) {
                    cur_id = block_ids[byte_ix];
                    ++num_blocks;
                }
            }
            block_ids[byte_ix] = cur_id;
        }

        return num_blocks;
    }

    public static double bitCost(int count) {
        return count == 0 ? -2.0 : Utils.fastLog2(count);
    }

    public static void refineEntropyCodes(int[] data, int length, int stride, int num_histograms,
                                          Histogram[] histograms) {
        int iters =
                Constant.kIterMulForRefining * length / stride + Constant.kMinItersForRefining;
        int seed = 7;
        int iter;
        iters = ((iters + num_histograms - 1) / num_histograms) * num_histograms;
        for (iter = 0; iter < iters; ++iter) {
            Histogram sample = new Histogram();
            Histogram.clearHistogram(sample);
            seed = randomSample(seed, data, length, stride, sample);
            Histogram.histogramAddHistogram(histograms[iter % num_histograms], sample);
        }
    }

    public static int randomSample(int seed, int[] data, int length, int stride, Histogram sample) {
        int pos = 0;
        int[] seed_ = new int[1];
        if (stride >= length) {
            stride = length;
        } else {
            pos = myRand(seed_) % (length - stride + 1);
        }
        Histogram.histogramAddVector(sample, data, pos, stride);
        return seed_[0];
    }

    public static void initialEntropyCodes(int[] data, int length, int stride,
                                           int num_histograms, Histogram[] histograms) {
        int[] seed = new int[1];
        seed[0] = 7;
        int block_length = length / num_histograms;
        int i;
        Histogram.clearHistograms(histograms, num_histograms);
        for (i = 0; i < num_histograms; ++i) {
            int pos = length * i / num_histograms;
            if (i != 0) {
                pos += myRand(seed) % block_length;
            }
            if (pos + stride >= length) {
                pos = length - stride - 1;
            }
            Histogram.histogramAddVector(histograms[i], data, pos, stride);
        }
    }

    public static int myRand(int[] seed) {
        seed[0] *= 16807;
        return seed[0];
    }

    //TODO : check index into data ?
    public static void copyLiteralsToByteArray(Command[] commands, int numCommands, int[] data, int offset,
                                               int mask, int[] literals) throws BrotliException {
        int pos = 0;
        int from_pos = offset & mask;
        int i;
        for (i = 0; i < numCommands; ++i) {
            long insert_len = commands[i].getInsertLen();
            if (from_pos + insert_len > mask) {
                int head_size = mask + 1 - from_pos;
                Utils.copyBytes(literals, pos, data, from_pos, head_size);
                from_pos = 0;
                pos += head_size;
                insert_len -= head_size;
            }
            if (insert_len > 0) {
                Utils.copyBytes(literals, pos, data, from_pos, (int) insert_len);
                pos += insert_len;
            }
            from_pos = (int) ((from_pos + insert_len + Command.commandCopyLen(commands[i])) & mask);
        }
    }

    public static int countLiterals(Command[] commands, int numCommands) {
        int total_length = 0;
        int i;
        for (i = 0; i < numCommands; ++i) {
            total_length += commands[i].getInsertLen();
        }
        return total_length;
    }
}