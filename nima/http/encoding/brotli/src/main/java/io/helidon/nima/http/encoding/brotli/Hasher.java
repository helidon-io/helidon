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

class Hasher {
    public static final int BUCKET_BITS = 17;
    public static final int MAX_TREE_SEARCH_DEPTH = 64;
    public static final int MAX_TREE_COMP_LENGTH = 128;
    public static final int MAX_NUM_MATCHES_H10 = 128;
    HasherCommon common = new HasherCommon();
    HashToBinaryTree tree = new HashToBinaryTree();
    EncoderDictionary encDict = new EncoderDictionary();

    Hasher() {
    }

    public static void hasherReset(Hasher hasher) {
        hasher.common.isPrepared = false;
    }

    public static void hasherSetup(Hasher hasher, State state, int[] data, int position, int inputSize,
                                   boolean isLast) {
        boolean one_shot = (position == 0 && isLast);
        if (!hasher.common.isSetup) {
            int[] alloc_size = new int[4];
            int i;
            chooseHasher(state.hasher);
            hasherSize(state, one_shot, inputSize, alloc_size);
            for (i = 0; i < 4; ++i) {
                if (alloc_size[i] == 0) {
                    continue;
                }
                hasher.common.extra[i] = new int[alloc_size[i]];
            }
            copyHasherParam(hasher.common, state);
            initializeH10(hasher.common, hasher.tree, state);
            hasherReset(hasher);
            hasher.common.isSetup = true;
        }

        if (!hasher.common.isPrepared) {
            prepareH10(hasher.tree, one_shot, inputSize, data);
            if (position == 0) {
                hasher.common.dictNumLookups = 0;
                hasher.common.dictNumMatches = 0;
            }
            hasher.common.isPrepared = true;
        }
    }

    public static void prepareH10(HashToBinaryTree tree, boolean one_shot, int inputSize, int[] data) {
        int invalid_pos = tree.invalidPos;
        int i;
        int[] buckets = tree.buckets;
        //BROTLI_UNUSED(data);
        //BROTLI_UNUSED(one_shot);
        //BROTLI_UNUSED(input_size);
        for (i = 0; i < Constant.BUCKET_SIZE; i++) {
            buckets[i] = invalid_pos;
        }
    }

    public static void initializeH10(HasherCommon common, HashToBinaryTree tree, State state) {
        tree.buckets = common.extra[0];
        tree.forest = common.extra[1];
        tree.windowMask = (1 << state.window) - 1;
        tree.invalidPos = ~tree.windowMask; //Todo : what to do here ?
    }

    public static void hasherSize(State state, boolean one_shot, int inputSize, int[] alloc_size) {
        int num_nodes = 1 << state.window;
        if (one_shot && inputSize < num_nodes) {
            num_nodes = inputSize;
        }
        alloc_size[0] = Constant.BUCKET_SIZE;
        alloc_size[1] = 2 * num_nodes;
    }

    public static void chooseHasher(Hasher hasher) {
        hasher.common.type = 10;
    }

    public static int hashTypeLength() {
        return 4;
    }

    public static int hashBytes(int[] data, int index) {
        int h = Utils.get32Bits(data, index) * Constant.kHashMul32;
        return (h >> (32 - Hasher.BUCKET_BITS)) & 0xFF;
    }

    public static void hasherInit(Hasher hasher) {
        hasher.common.isSetup = false;
        hasher.common.extra[0] = null;
        hasher.common.extra[1] = null;
        hasher.common.extra[2] = null;
        hasher.common.extra[3] = null;
    }

    private static void copyHasherParam(HasherCommon common, State state) {
        common.type = state.hasher.common.type;
        common.bucketBits = state.hasher.common.bucketBits;
        common.blockBits = state.hasher.common.blockBits;
        common.hashLen = state.hasher.common.hashLen;
        common.numLastDistancesToCheck = state.numLastDistancesToCheck;
    }
}
