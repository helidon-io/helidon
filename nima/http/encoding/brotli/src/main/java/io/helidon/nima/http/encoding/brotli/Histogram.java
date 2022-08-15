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

class Histogram {
    public int[] data;
    public int totalCount;
    public double bitCost;

    Histogram() {
        data = new int[256];
        totalCount = 0;
        bitCost = 0;
    }

    Histogram(int size) {
        data = new int[size];
        totalCount = 0;
        bitCost = 0;
    }

    Histogram(HistogramType type) {
        data = new int[type.value];
        totalCount = 0;
        bitCost = 0;
    }

    public static void brotliOptimizeHistograms(int numDistanceCodes, MetaBlockSplit mb) throws BrotliException {
        int[] goodForRLE = new int[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
        int i;
        for (i = 0; i < mb.literalHistogramsSize; ++i) {
            brotliOptimizeHuffmanCountsForRle(256, mb.literalHistograms[i].data,
                                              goodForRLE);
        }
        for (i = 0; i < mb.commandHistogramsSize; ++i) {
            brotliOptimizeHuffmanCountsForRle(Constant.BROTLI_NUM_COMMAND_SYMBOLS,
                                              mb.commandHistograms[i].data,
                                              goodForRLE);
        }
        for (i = 0; i < mb.distanceHistogramsSize; ++i) {
            brotliOptimizeHuffmanCountsForRle(numDistanceCodes,
                                              mb.distanceHistograms[i].data,
                                              goodForRLE);
        }
    }

    public static void brotliOptimizeHuffmanCountsForRle(int length, int[] data, int[] goodForRLE) throws BrotliException {
        int nonzero_count = 0;
        int stride;
        int limit;
        int sum;
        int streak_limit = 1240;
        /* Let's make the Huffman code more compatible with RLE encoding. */
        int i;
        for (i = 0; i < length - 1; i++) {
            if (data[i] != 0) {
                ++nonzero_count;
            }
        }
        if (nonzero_count < 16) {
            return;
        }
        while (length != 0 && data[length - 1] == 0) {
            --length;
        }
        if (length == 0) {
            return;  /* All zeros. */
        }
        /* Now counts[0..length - 1] does not have trailing zeros. */

        int nonzeros = 0;
        int smallest_nonzero = 1 << 30;
        for (i = 0; i < length; ++i) {
            if (data[i] != 0) {
                ++nonzeros;
                if (smallest_nonzero > data[i]) {
                    smallest_nonzero = data[i];
                }
            }
        }
        if (nonzeros < 5) {
            /* Small histogram will model it well. */
            return;
        }
        if (smallest_nonzero < 4) {
            int zeros = length - nonzeros;
            if (zeros < 6) {
                for (i = 1; i < length - 1; ++i) {
                    if (data[i - 1] != 0 && data[i] == 0 && data[i + 1] != 0) {
                        data[i] = 1;
                    }
                }
            }
        }
        if (nonzeros < 28) {
            return;
        }

        /* 2) Let's mark all population counts that already can be encoded
            with an RLE code. */
        Utils.copyBytes(goodForRLE, new int[length], length);

        /* Let's not spoil any of the existing good RLE codes.
           Mark any seq of 0's that is longer as 5 as a good_for_rle.
           Mark any seq of non-0's that is longer as 7 as a good_for_rle. */
        int symbol = data[0];
        int step = 0;
        for (i = 0; i <= length; ++i) {
            if (i == length || data[i] != symbol) {
                if ((symbol == 0 && step >= 5) ||
                        (symbol != 0 && step >= 7)) {
                    int k;
                    for (k = 0; k < step; ++k) {
                        goodForRLE[i - k - 1] = 1;
                    }
                }
                step = 1;
                if (i != length) {
                    symbol = data[i];
                }
            } else {
                ++step;
            }
        }

          /* 3) Let's replace those population counts that lead to more RLE codes.
             Math here is in 24.8 fixed point representation. */
        stride = 0;
        limit = 256 * (data[0] + data[1] + data[2]) / 3 + 420;
        sum = 0;
        for (i = 0; i <= length; ++i) {
            if (i == length || goodForRLE[i] != 0 ||
                    (i != 0 && goodForRLE[i - 1] != 0) ||
                    (256 * data[i] - limit + streak_limit) >= 2 * streak_limit) {
                if (stride >= 4 || (stride >= 3 && sum == 0)) {
                    int k;
                    /* The stride must end, collapse what we have, if we have enough (4). */
                    int count = (sum + stride / 2) / stride;
                    if (count == 0) {
                        count = 1;
                    }
                    if (sum == 0) {
                        /* Don't make an all zeros stride to be upgraded to ones. */
                        count = 0;
                    }
                    for (k = 0; k < stride; ++k) {
                      /* We don't want to change value at counts[i],
                         that is already belonging to the next stride. Thus - 1. */
                        data[i - k - 1] = count;
                    }
                }
                stride = 0;
                sum = 0;
                if (i < length - 2) {
                    /* All interesting strides have a count of at least 4, */
                    /* at least when non-zeros. */
                    limit = 256 * (data[i] + data[i + 1] + data[i + 2]) / 3 + 420;
                } else if (i < length) {
                    limit = 256 * data[i];
                } else {
                    limit = 0;
                }
            }
            ++stride;
            if (i != length) {
                sum += data[i];
                if (stride >= 4) {
                    limit = (256 * sum + stride / 2) / stride;
                }
                if (stride == 4) {
                    limit += 120;
                }
            }
        }
    }

    public static void clearHistogram(Histogram histo) {
        histo.data = new int[histo.data.length];
        histo.totalCount = 0;
        histo.bitCost = Double.MAX_VALUE;
    }

    public static void histogramAddDistance(Histogram histo, int value) {
        ++histo.data[value];
        ++histo.totalCount;
    }

    public static void clearHistograms(Histogram[] histograms, int length) {
        for (int i = 0; i < length; ++i) {
            clearHistogram(histograms[i]);
        }
    }

    public static int brotliHistogramCombine(Histogram[] histograms, int[] clusterSize, int[] symbols, int symbolIndex,
                                             int[] clusters, int clusterIndex, HistogramPair[] pairs, int numClusters,
                                             int symbolsSize, int maxClusters, int maxNumPairs) throws BrotliException {
        double cost_diff_threshold = 0.0;
        int min_cluster_size = 1;
        int num_pairs = 0;
        int idx1;

        for (idx1 = 0; idx1 < numClusters; ++idx1) {
            int idx2;
            for (idx2 = idx1 + 1; idx2 < numClusters; ++idx2) {
                num_pairs = brotliCompareAndPushToQueue(histograms, clusterSize, clusters[clusterIndex + idx1],
                                                        clusters[clusterIndex + idx2], maxNumPairs, pairs, 0, num_pairs);
            }
        }

        while (numClusters > min_cluster_size) {
            int best_idx1;
            int best_idx2;
            int i;
            if (pairs[0].costDiff >= cost_diff_threshold) {
                cost_diff_threshold = 1e99;
                min_cluster_size = maxClusters;
                continue;
            }
            /* Take the best pair from the top of heap. */
            best_idx1 = pairs[0].idx1;
            best_idx2 = pairs[0].idx2;
            histogramAddHistogram(histograms[best_idx1], histograms[best_idx2]);
            histograms[best_idx1].bitCost = pairs[0].costCombo;
            clusterSize[best_idx1] += clusterSize[best_idx2];
            for (i = 0; i < symbolsSize; ++i) {
                if (symbols[symbolIndex + i] == best_idx2) {
                    symbols[symbolIndex + i] = best_idx1;
                }
            }
            for (i = 0; i < numClusters; ++i) {
                if (clusters[clusterIndex + i] == best_idx2) {
                    Utils.copyBytes(clusters, clusterIndex + i, clusters, clusterIndex + i + 1,
                                    numClusters - i - 1);
                    break;
                }
            }
            --numClusters;

            /* Remove pairs intersecting the just combined best pair. */
            int copy_to_idx = 0;
            for (i = 0; i < num_pairs; ++i) {
                HistogramPair p = pairs[i];
                if (p.idx1 == best_idx1 || p.idx2 == best_idx1 ||
                        p.idx1 == best_idx2 || p.idx2 == best_idx2) {
                    /* Remove invalid pair from the queue. */
                    continue;
                }
                if (histogramPairIsLess(pairs[0], p)) {
                    /* Replace the top of the queue if needed. */
                    HistogramPair front = pairs[0];
                    pairs[0] = p;
                    pairs[copy_to_idx] = front;
                } else {
                    pairs[copy_to_idx] = p;
                }
                ++copy_to_idx;
            }
            num_pairs = copy_to_idx;


            /* Push new pairs formed with the combined histogram to the heap. */
            for (i = 0; i < numClusters; ++i) {
                num_pairs = brotliCompareAndPushToQueue(histograms, clusterSize, best_idx1, clusters[clusterIndex + i],
                                                        maxNumPairs, pairs, 0, num_pairs);
            }
        }
        return numClusters;
    }

    public static int brotliCompareAndPushToQueue(Histogram[] histograms, int[] clusterSize, int idx1, int idx2,
                                                  int maxNumPairs, HistogramPair[] pairs, int pairsIndex, int num_pairs) {
        boolean is_good_pair = false;
        HistogramPair p = new HistogramPair();
        p.idx1 = 0;
        p.idx2 = 0;
        p.costDiff = p.costCombo = 0;
        if (idx1 == idx2) {
            return num_pairs;
        }
        if (idx2 < idx1) {
            int t = idx2;
            idx2 = idx1;
            idx1 = t;
        }
        p.idx1 = idx1;
        p.idx2 = idx2;
        p.costDiff = 0.5 * clusterCostDiff(clusterSize[idx1], clusterSize[idx2]);
        p.costDiff -= histograms[idx1].bitCost;
        p.costDiff -= histograms[idx2].bitCost;

        if (histograms[idx1].totalCount == 0) {
            p.costCombo = histograms[idx2].bitCost;
            is_good_pair = true;
        } else if (histograms[idx2].totalCount == 0) {
            p.costCombo = histograms[idx1].bitCost;
            is_good_pair = true;
        } else {
            double threshold = num_pairs == 0 ? 1e99 :
                    Math.max(0.0, pairs[pairsIndex].costDiff);
            Histogram combo = histograms[idx1];
            double cost_combo;
            histogramAddHistogram(combo, histograms[idx2]);
            cost_combo = brotliPopulationCost(combo);
            if (cost_combo < threshold - p.costDiff) {
                p.costCombo = cost_combo;
                is_good_pair = true;
            }
        }
        if (is_good_pair) {
            p.costDiff += p.costCombo;
            if (num_pairs > 0 && histogramPairIsLess(pairs[pairsIndex], p)) {
                /* Replace the top of the queue if needed. */
                if (num_pairs < maxNumPairs) {
                    pairs[num_pairs] = pairs[pairsIndex];
                    ++(num_pairs);
                }
                pairs[0] = p;
            } else if (num_pairs < maxNumPairs) {
                pairs[num_pairs] = p;
                ++(num_pairs);
            }
        }
        return num_pairs;
    }

    public static boolean histogramPairIsLess(HistogramPair p1, HistogramPair p2) {
        if (p1.costDiff != p2.costDiff) {
            return p1.costDiff > p2.costDiff;
        }
        return (p1.idx2 - p1.idx1) > (p2.idx2 - p2.idx1);
    }

    public static double clusterCostDiff(int size_a, int size_b) {
        int size_c = size_a + size_b;
        return (double) size_a * Utils.fastLog2(size_a) +
                (double) size_b * Utils.fastLog2(size_b) -
                (double) size_c * Utils.fastLog2(size_c);
    }

    public static double brotliPopulationCost(Histogram histogram) {
        double kOneSymbolHistogramCost = 12;
        double kTwoSymbolHistogramCost = 20;
        double kThreeSymbolHistogramCost = 28;
        double kFourSymbolHistogramCost = 37;
        int data_size = histogram.data.length;
        int count = 0;
        int[] s = new int[5];
        double bits = 0.0;
        int i;
        if (histogram.totalCount == 0) {
            return kOneSymbolHistogramCost;
        }
        for (i = 0; i < data_size; ++i) {
            if (histogram.data[i] > 0) {
                s[count] = i;
                ++count;
                if (count > 4) {
                    break;
                }
            }
        }
        if (count == 1) {
            return kOneSymbolHistogramCost;
        }
        if (count == 2) {
            return (kTwoSymbolHistogramCost + (double) histogram.totalCount);
        }
        if (count == 3) {
            int histo0 = histogram.data[s[0]];
            int histo1 = histogram.data[s[1]];
            int histo2 = histogram.data[s[2]];
            int histomax = Math.max(histo0, Math.max(histo1, histo2));
            return (
                    kThreeSymbolHistogramCost +
                            2 * (histo0 + histo1 + histo2) - histomax);
        }
        if (count == 4) {
            int[] histo = new int[4];
            int h23;
            int histomax;
            for (i = 0; i < 4; ++i) {
                histo[i] = histogram.data[s[i]];
            }
            /* Sort */
            for (i = 0; i < 4; ++i) {
                int j;
                for (j = i + 1; j < 4; ++j) {
                    if (histo[j] > histo[i]) {
                        Utils.swap(histo, j, i);
                    }
                }
            }
            h23 = histo[2] + histo[3];
            histomax = Math.max(h23, histo[0]);
            return (
                    kFourSymbolHistogramCost +
                            3 * h23 + 2 * (histo[0] + histo[1]) - histomax);
        }

        {
            int max_depth = 1;
            int[] depth_histo = new int[Constant.BROTLI_CODE_LENGTH_CODES];
            double log2total = Utils.fastLog2(histogram.totalCount);
            for (i = 0; i < data_size; ) {
                if (histogram.data[i] > 0) {
                    double log2p = log2total - Utils.fastLog2(histogram.data[i]);
                    /* Approximate the bit depth by round(-log2(P(symbol))) */
                    int depth = (int) (log2p + 0.5);
                    bits += histogram.data[i] * log2p;
                    if (depth > 15) {
                        depth = 15;
                    }
                    if (depth > max_depth) {
                        max_depth = depth;
                    }
                    ++depth_histo[depth];
                    ++i;
                } else {
                    int reps = 1;
                    int k;
                    for (k = i + 1; k < data_size && histogram.data[k] == 0; ++k) {
                        ++reps;
                    }
                    i += reps;
                    if (i == data_size) {
                        break;
                    }
                    if (reps < 3) {
                        depth_histo[0] += reps;
                    } else {
                        reps -= 2;
                        while (reps > 0) {
                            ++depth_histo[Constant.BROTLI_REPEAT_ZERO_CODE_LENGTH];
                            /* Add the 3 extra bits for the 17 code length code. */
                            bits += 3;
                            reps >>= 3;
                        }
                    }
                }
            }
            /* Add the estimated encoding cost of the code length code histogram. */
            bits += (double) (18 + 2 * max_depth);
            /* Add the entropy of the code length code histogram. */
            bits += Compress.bitsEntropy(depth_histo, Constant.BROTLI_CODE_LENGTH_CODES);
        }
        return bits;
    }

    public static double brotliHistogramBitCostDistance(Histogram histogram, Histogram candidate) throws BrotliException {
        if (histogram.totalCount == 0) {
            return 0.0;
        } else {
            Histogram tmp = new Histogram();
            Histogram.copyHistogram(tmp, histogram);
            histogramAddHistogram(tmp, candidate);
            return brotliPopulationCost(tmp) - candidate.bitCost;
        }
    }

    public static void copyHistogram(Histogram dest, Histogram src) throws BrotliException {
        dest.bitCost = src.bitCost;
        dest.totalCount = src.totalCount;
        dest.data = new int[src.data.length];
        Utils.copyBytes(dest.data, src.data, src.data.length);
    }

    public static void histogramAddVector(Histogram histogram, int[] data, int pos, int n) {
        histogram.totalCount += n;
        n += 1;
        while (--n != 0) {
            ++histogram.data[data[pos++]];
        }
    }

    public static void histogramAddHistogram(Histogram histogram, Histogram sample) {
        histogram.totalCount += sample.totalCount;
        for (int i = 0; i < histogram.data.length; ++i) {
            histogram.data[i] += sample.data[i];
        }
    }

    public static void addHistogram(Histogram histogram, int value) {
        ++histogram.data[value];
        ++histogram.totalCount;
    }

    public static void brotliBuildHistogramsWithContext(Command[] commands, int numCommands, BlockSplit literalsplit,
                                                        BlockSplit commandsplit, BlockSplit distancesplit, int[] data,
                                                        int startPosition, int mask, int prevByte, int prevByte2,
                                                        Compress.ContextType[] contextModes,
                                                        Histogram[] literalHistograms, Histogram[] commandHistograms,
                                                        Histogram[] distanceHistograms) {
        int pos = startPosition;
        BlockSplitIterator literal_it = new BlockSplitIterator();
        BlockSplitIterator insert_and_copy_it = new BlockSplitIterator();
        BlockSplitIterator dist_it = new BlockSplitIterator();
        int i;

        BlockSplitIterator.initBlockSplitIterator(literal_it, literalsplit);
        BlockSplitIterator.initBlockSplitIterator(insert_and_copy_it, commandsplit);
        BlockSplitIterator.initBlockSplitIterator(dist_it, distancesplit);
        for (i = 0; i < numCommands; ++i) {
            Command cmd = commands[i];
            long j;
            BlockSplitIterator.blockSplitIteratorNext(insert_and_copy_it);
            addHistogram(commandHistograms[insert_and_copy_it.type],
                         cmd.getCmdPrefix());
            /* TODO: unwrap iterator blocks. */
            for (j = cmd.getInsertLen(); j != 0; --j) {
                int context;
                BlockSplitIterator.blockSplitIteratorNext(literal_it);
                context = literal_it.type;
                if (contextModes[0] != Compress.ContextType.CONTEXT_LSB6) {
                    int lut = Compress.contextLut(contextModes[context]);
                    context = (context << Constant.BROTLI_LITERAL_CONTEXT_BITS) +
                            brotliContext(prevByte, prevByte2, lut);
                }
                addHistogram(literalHistograms[context], data[pos & mask]);
                prevByte2 = prevByte;
                prevByte = data[pos & mask];
                ++pos;
            }
            pos += Command.commandCopyLen(cmd);
            if (Command.commandCopyLen(cmd) != 0) {
                prevByte2 = data[(pos - 2) & mask];
                prevByte = data[(pos - 1) & mask];
                if (cmd.getCmdPrefix() >= 128) {
                    int context;
                    BlockSplitIterator.blockSplitIteratorNext(dist_it);
                    context = (dist_it.type << Constant.BROTLI_DISTANCE_CONTEXT_BITS) +
                            Command.commandDistanceContext(cmd);
                    addHistogram(distanceHistograms[context],
                                 cmd.getDistPrefix() & 0x3FF);
                }
            }
        }
    }

    public static int brotliContext(int p1, int p2, int lut) {
        return Tables._kBrotliContextLookupTable[lut + p1] | (Tables._kBrotliContextLookupTable[lut + 256 + p2]);
    }

    public static int brotliClusterHistograms(Histogram[] in, int inSize,
                                              int kMaxNumberOfHistograms, Histogram[] out,
                                              int outSize, int[] histogramSymbols) throws BrotliException {
        int[] cluster_size = new int[inSize];
        int[] clusters = new int[inSize];
        int num_clusters = 0;
        int max_input_histograms = 64;
        int pairs_capacity = max_input_histograms * max_input_histograms / 2;
        /* For the first pass of clustering, we allow all pairs. */
        HistogramPair[] pairs = new HistogramPair[pairs_capacity + 1];
        int i;

        for (i = 0; i < inSize; ++i) {
            cluster_size[i] = 1;
        }

        for (i = 0; i < inSize; ++i) {
            out[i] = in[i];
            out[i].bitCost = brotliPopulationCost(in[i]);
            histogramSymbols[i] = i;
        }

        for (i = 0; i < inSize; i += max_input_histograms) {
            int num_to_combine = Math.min(inSize - i, max_input_histograms);
            int num_new_clusters;
            int j;
            for (j = 0; j < num_to_combine; ++j) {
                clusters[num_clusters + j] = (i + j);
            }
            num_new_clusters = brotliHistogramCombine(out, cluster_size,
                                                      histogramSymbols, i,
                                                      clusters, num_clusters, pairs,
                                                      num_to_combine, num_to_combine,
                                                      kMaxNumberOfHistograms, pairs_capacity);
            num_clusters += num_new_clusters;
        }

        int max_num_pairs = Math.min(64 * num_clusters, (num_clusters / 2) * num_clusters);
        //BROTLI_ENSURE_CAPACITY(pairs, pairs_capacity, max_num_pairs + 1);

        /* Collapse similar histograms. */
        num_clusters = brotliHistogramCombine(out, cluster_size,
                                              histogramSymbols, 0, clusters, 0,
                                              pairs, num_clusters, inSize,
                                              kMaxNumberOfHistograms, max_num_pairs);

        /* Find the optimal map from original histograms to the final ones. */
        brotliHistogramRemap(in, inSize, clusters, num_clusters,
                             out, histogramSymbols);

        /* Convert the context map to a canonical form. */
        outSize = brotliHistogramReindex(out, histogramSymbols, inSize);

        return outSize;
    }

    public static int brotliHistogramReindex(Histogram[] out, int[] symbols, int length) {
        int kInvalidIndex = Integer.MAX_VALUE;
        int[] new_index = new int[length];
        int next_index;
        int i;
        for (i = 0; i < length; ++i) {
            new_index[i] = kInvalidIndex;
        }
        next_index = 0;
        for (i = 0; i < length; ++i) {
            if (new_index[symbols[i]] == kInvalidIndex) {
                new_index[symbols[i]] = next_index;
                ++next_index;
            }
        }
        /* TODO: by using idea of "cycle-sort" we can avoid allocation of
            tmp and reduce the number of copying by the factor of 2. */
        Histogram[] tmp = new Histogram[next_index];
        next_index = 0;
        for (i = 0; i < length; ++i) {
            if (new_index[symbols[i]] == next_index) {
                tmp[next_index] = out[symbols[i]];
                ++next_index;
            }
            symbols[i] = new_index[symbols[i]];
        }

        for (i = 0; i < next_index; ++i) {
            out[i] = tmp[i];
        }

        return next_index;
    }

    public static void brotliHistogramRemap(Histogram[] in, int inSize, int[] clusters, int num_clusters,
                                            Histogram[] out, int[] symbols) throws BrotliException {
        int i;
        for (i = 0; i < inSize; ++i) {
            int best_out = i == 0 ? symbols[0] : symbols[i - 1];
            double best_bits =
                    brotliHistogramBitCostDistance(in[i], out[best_out]);
            for (int j = 0; j < num_clusters; ++j) {
                double cur_bits = brotliHistogramBitCostDistance(in[i], out[clusters[j]]);
                if (cur_bits < best_bits) {
                    best_bits = cur_bits;
                    best_out = clusters[j];
                }
            }
            symbols[i] = best_out;
        }

        /* Recompute each out based on raw and symbols. */
        for (i = 0; i < num_clusters; ++i) {
            clearHistogram(out[clusters[i]]);
        }
        for (i = 0; i < inSize; ++i) {
            histogramAddHistogram(out[symbols[i]], in[i]);
        }
    }
}
