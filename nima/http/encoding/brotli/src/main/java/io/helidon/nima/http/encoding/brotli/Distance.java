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

class Distance {
    public int distancePostfixBits;
    public int numDirectDistanceCodes;
    public int alphabetSizeMax;
    public int alphabetSizeLimit;
    public int maxDistance;

    Distance() {
        this.distancePostfixBits = 0;
        this.numDirectDistanceCodes = 0;
        this.alphabetSizeMax = 0;
        this.alphabetSizeLimit = 0;
        this.maxDistance = 0;
    }

    public static Distance copyCurrentDistance(State state) {
        Distance distance = new Distance();
        distance.distancePostfixBits = state.distance.distancePostfixBits;
        distance.numDirectDistanceCodes = state.distance.numDirectDistanceCodes;
        distance.alphabetSizeMax = state.distance.alphabetSizeMax;
        distance.alphabetSizeLimit = state.distance.alphabetSizeLimit;
        distance.maxDistance = state.distance.maxDistance;
        return distance;
    }

    public static void copyDistanceToState(Distance distance, State state) {
        state.distance.distancePostfixBits = distance.distancePostfixBits;
        state.distance.numDirectDistanceCodes = distance.numDirectDistanceCodes;
        state.distance.alphabetSizeMax = distance.alphabetSizeMax;
        state.distance.alphabetSizeLimit = distance.alphabetSizeLimit;
        state.distance.maxDistance = distance.maxDistance;
    }

    public static void brotliInitDistanceParams(Distance distance, int npostfix, int ndirect) {
        int alphabet_size_max;
        int alphabet_size_limit;
        int max_distance;

        distance.distancePostfixBits = npostfix;
        distance.numDirectDistanceCodes = ndirect;

        alphabet_size_max = brotliDistanceAlphabetSize(npostfix, ndirect, Constant.BROTLI_MAX_DISTANCE_BITS);
        alphabet_size_limit = alphabet_size_max;
        max_distance = ndirect + (1 << (Constant.BROTLI_MAX_DISTANCE_BITS + npostfix + 2)) -
                (1 << (npostfix + 2));

        distance.alphabetSizeMax = alphabet_size_max;
        distance.alphabetSizeLimit = alphabet_size_limit;
        distance.maxDistance = max_distance;
    }

    public static int brotliDistanceAlphabetSize(int npostFix, int nDirect, int maxDistance) {
        return Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + (nDirect) + ((maxDistance) << ((npostFix) + 1));
    }

    public static boolean computeDistanceCost(Command[] commands, int numCommands, Distance orig_params,
                                              Distance new_params, double[] dist_cost) {
        int i;
        boolean equal_params = false;
        int dist_prefix = 0;
        int[] dist_extra = new int[1];
        double extra_bits = 0.0;
        Histogram histo = new Histogram();
        Histogram.clearHistogram(histo);

        if (orig_params.distancePostfixBits == new_params.distancePostfixBits &&
                orig_params.numDirectDistanceCodes ==
                        new_params.numDirectDistanceCodes) {
            equal_params = true;
        }

        for (i = 0; i < numCommands; i++) {
            Command cmd = commands[i];
            if (Command.commandCopyLen(cmd) != 0 && cmd.getCmdPrefix() >= 128) {
                if (equal_params) {
                    dist_prefix = cmd.getDistPrefix();
                } else {
                    int distance = commandRestoreDistanceCode(cmd, orig_params);
                    if (distance > new_params.maxDistance) {
                        return false;
                    }
                    dist_prefix = Compress.prefixEncodeCopyDistance(distance,
                                                                    new_params.numDirectDistanceCodes,
                                                                    new_params.distancePostfixBits,
                                                                    dist_extra);
                }
                Histogram.histogramAddDistance(histo, dist_prefix & 0x3FF);
                extra_bits += dist_prefix >> 10;
            }
        }

        dist_cost[0] = Histogram.brotliPopulationCost(histo) + extra_bits;
        return true;
    }

    public static int commandRestoreDistanceCode(Command cmd, Distance distance) {
        if ((cmd.getDistExtra() & 0x3FF) <
                Constant.BROTLI_NUM_DISTANCE_SHORT_CODES + distance.numDirectDistanceCodes) {
            return cmd.getCmdPrefix() & 0x3FF;
        } else {
            int dcode = cmd.getCmdPrefix() & 0x3FF;
            int nbits = cmd.getCmdPrefix() >> 10;
            long extra = cmd.getDistExtra();
            int postfix_mask = (1 << distance.distancePostfixBits) - 1;
            int hcode = (
                    dcode - distance.numDirectDistanceCodes -
                            Constant.BROTLI_NUM_DISTANCE_SHORT_CODES) >>
                    distance.distancePostfixBits;
            int lcode = (
                    dcode - distance.numDirectDistanceCodes -
                            Constant.BROTLI_NUM_DISTANCE_SHORT_CODES) & postfix_mask;
            int offset = ((2 + (hcode & 1)) << nbits) - 4;
            return (int) ((offset + extra) << distance.distancePostfixBits) + lcode +
                    distance.numDirectDistanceCodes + Constant.BROTLI_NUM_DISTANCE_SHORT_CODES;
        }
    }

}
