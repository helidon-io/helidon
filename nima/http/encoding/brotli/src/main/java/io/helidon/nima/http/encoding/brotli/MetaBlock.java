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

class MetaBlock {
    static void brotliBuildMetaBlock(State state, int[] data, int position,
                                     int mask, Compress.ContextType literalContextMode,
                                     MetaBlockSplit mb) throws BrotliException {
        int kMaxNumberOfHistograms = 256;
        Histogram[] distanceHistograms;
        Histogram[] literalHistograms;
        Compress.ContextType[] literal_context_modes = null;
        int literal_histograms_size;
        int distance_histograms_size;
        int i;
        int literal_context_multiplier = 1;
        int npostfix;
        int ndirect = 0;
        int ndirect_msb = 0;
        boolean check_orig = true;
        double best_dist_cost = 1e99;
        Distance orig_params = Distance.copyCurrentDistance(state);
        Distance new_params = Distance.copyCurrentDistance(state);
        for (npostfix = 0; npostfix <= Constant.BROTLI_MAX_NPOSTFIX; npostfix++) {
            for (; ndirect_msb < 16; ndirect_msb++) {
                ndirect = ndirect_msb << npostfix;
                boolean skip;
                double[] dist_cost = new double[1];
                Distance.brotliInitDistanceParams(new_params, npostfix, ndirect);
                if (npostfix == state.distance.distancePostfixBits &&
                        ndirect == state.distance.numDirectDistanceCodes) {
                    check_orig = false;
                }
                skip = !Distance.computeDistanceCost(
                        state.commands, state.numCommands,
                        orig_params, new_params, dist_cost);
                if (skip || (dist_cost[0] > best_dist_cost)) {
                    break;
                }
                best_dist_cost = dist_cost[0];
                Distance.copyDistanceToState(new_params, state);
            }
            if (ndirect_msb > 0) {
                ndirect_msb--;
            }
            ndirect_msb /= 2;
        }
        if (check_orig) {
            double[] dist_cost = new double[1];
            Distance.computeDistanceCost(state.commands, state.numCommands,
                                         orig_params, orig_params, dist_cost);
            if (dist_cost[0] < best_dist_cost) {
                /* NB: currently unused; uncomment when more param tuning is added. */
                /* best_dist_cost = dist_cost; */
                Distance.copyDistanceToState(orig_params, state);
            }
        }
        recomputeDistancePrefixes(state.commands, state.numCommands, orig_params, state.distance);

        MetaBlockSplit.brotliSplitBlock(state.commands, state.numCommands,
                                        data, position, mask, state,
                                        mb.literalsplit,
                                        mb.commandsplit,
                                        mb.distancesplit);

        if (!state.disableLiteralContextModeling) {
            literal_context_multiplier = 1 << Constant.BROTLI_LITERAL_CONTEXT_BITS;
            literal_context_modes = new Compress.ContextType[mb.literalsplit.numTypes];
            for (i = 0; i < mb.literalsplit.numTypes; ++i) {
                literal_context_modes[i] = literalContextMode;
            }
        }

        literal_histograms_size = mb.literalsplit.numTypes * literal_context_multiplier;
        literalHistograms = new Histogram[literal_histograms_size];
        for (i = 0; i < literal_histograms_size; i++) {
            literalHistograms[i] = new Histogram();
        }
        Histogram.clearHistograms(literalHistograms, literal_histograms_size);

        distance_histograms_size = mb.distancesplit.numTypes << Constant.BROTLI_DISTANCE_CONTEXT_BITS;
        distanceHistograms = new Histogram[distance_histograms_size];
        for (i = 0; i < distance_histograms_size; i++) {
            distanceHistograms[i] = new Histogram(16 + ndirect + (48 << npostfix));
        }
        Histogram.clearHistograms(distanceHistograms, distance_histograms_size);

        //BROTLI_DCHECK(mb->command_histograms == 0);
        mb.distanceHistogramsSize = mb.commandsplit.numTypes;
        mb.commandHistograms = new Histogram[mb.commandHistogramsSize];
        for (i = 0; i < mb.commandHistogramsSize; i++) {
            mb.commandHistograms[i] = new Histogram(HistogramType.COMMAND);
        }
        Histogram.clearHistograms(mb.commandHistograms, mb.commandHistogramsSize);

        Histogram.brotliBuildHistogramsWithContext(state.commands, state.numCommands,
                                                   mb.literalsplit, mb.commandsplit, mb.distancesplit,
                                                   data, position, mask, state.prevByte, state.prevByte2, literal_context_modes,
                                                   literalHistograms, mb.commandHistograms, distanceHistograms);

        mb.literalContextMapSize =
                mb.literalsplit.numTypes << Constant.BROTLI_LITERAL_CONTEXT_BITS;
        mb.literalContextMap = new int[mb.literalContextMapSize];

        mb.literalHistogramsSize = mb.literalContextMapSize;
        mb.literalHistograms = new Histogram[mb.literalHistogramsSize];
        for (i = 0; i < mb.literalHistogramsSize; i++) {
            mb.literalHistograms[i] = new Histogram();
        }
        mb.literalHistogramsSize = Histogram.brotliClusterHistograms(literalHistograms, literal_histograms_size,
                                                                     kMaxNumberOfHistograms, mb.literalHistograms,
                                                                     mb.literalHistogramsSize, mb.literalContextMap);

        if (state.disableLiteralContextModeling) {
            /* Distribute assignment to all contexts. */
            for (i = mb.literalsplit.numTypes; i != 0; ) {
                int j = 0;
                i--;
                for (; j < (1 << Constant.BROTLI_LITERAL_CONTEXT_BITS); j++) {
                    mb.literalContextMap[(i << Constant.BROTLI_LITERAL_CONTEXT_BITS) + j] =
                            mb.literalContextMap[i];
                }
            }
        }

        mb.distanceContextMapSize = mb.distancesplit.numTypes << Constant.BROTLI_DISTANCE_CONTEXT_BITS;
        mb.distanceContextMap = new int[mb.distanceContextMapSize];

        mb.distanceHistogramsSize = mb.distanceContextMapSize;
        mb.distanceHistograms = new Histogram[mb.distanceHistogramsSize];
        for (i = 0; i < mb.distanceHistogramsSize; i++) {
            mb.distanceHistograms[i] = new Histogram(16 + ndirect + (48 << npostfix));
        }
        mb.distanceHistogramsSize = Histogram.brotliClusterHistograms(distanceHistograms,
                                                                      mb.distanceContextMapSize,
                                                                      kMaxNumberOfHistograms,
                                                                      mb.distanceHistograms,
                                                                      mb.distanceHistogramsSize,
                                                                      mb.distanceContextMap);
    }

    static void recomputeDistancePrefixes(Command[] commands, int numCommands, Distance origParams,
                                          Distance newParams) {
        int i;
        int[] distExtra = new int[1];

        if (origParams.distancePostfixBits == newParams.distancePostfixBits &&
                origParams.numDirectDistanceCodes ==
                        newParams.numDirectDistanceCodes) {
            return;
        }

        for (i = 0; i < numCommands; ++i) {
            Command cmd = commands[i];
            if (Command.commandCopyLen(cmd) != 0 && cmd.getCmdPrefix() >= 128) {
                int res = Compress.prefixEncodeCopyDistance(Distance.commandRestoreDistanceCode(cmd, origParams),
                                                            newParams.numDirectDistanceCodes,
                                                            newParams.distancePostfixBits,
                                                            distExtra);
                cmd.setDistPrefix(res);
                cmd.setDistExtra(distExtra[0]);
            }
        }
    }

}
