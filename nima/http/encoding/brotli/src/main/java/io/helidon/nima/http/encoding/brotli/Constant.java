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

/**
 * Gather all the immutable objects.
 */
class Constant {

    public static final int MAX_HUFFMAN_BITS = 16;
    public static final int BROTLI_WINDOW_GAP = 16;
    public static final int MAX_32BIT_VALUE = Integer.MAX_VALUE;
    public static final int INPUTBUFFER_SIZE = 64_000;
    public static final int MIN_RATIO = 980;
    public static final long MAX_DISTANCE = (1 << 18) - Constant.BROTLI_WINDOW_GAP;

    public static final int BROTLI_LARGE_MAX_WINDOW_BITS = 30;
    public static final int BROTLI_MAX_WINDOW_BITS = 24;
    public static final int BROTLI_MIN_WINDOW_BITS = 10;
    public static final int BROTLI_NUM_DISTANCE_SHORT_CODES = 16;
    public static final int BROTLI_MAX_DISTANCE_BITS = 24;

    /* Specification: 2. Compressed representation overview */
    public static final int BROTLI_MAX_NUMBER_OF_BLOCK_TYPES = 256;

    /* Specification: 7.3. Encoding of the context map */
    public static final int BROTLI_CONTEXT_MAP_MAX_RLE = 16;

    /* Specification: 3.3. Alphabet sizes: insert-and-copy length */
    public static final int BROTLI_NUM_LITERAL_SYMBOLS = 256;
    public static final int BROTLI_NUM_COMMAND_SYMBOLS = 704;
    public static final int BROTLI_NUM_BLOCK_LEN_SYMBOLS = 26;
    public static final int BROTLI_MAX_CONTEXT_MAP_SYMBOLS = 272;
    public static final int BROTLI_MAX_BLOCK_TYPE_SYMBOLS = 258;

    /* Specification: 3.5. Complex prefix codes */
    public static final int BROTLI_REPEAT_PREVIOUS_CODE_LENGTH = 16;
    public static final int BROTLI_REPEAT_ZERO_CODE_LENGTH = 17;
    public static final int BROTLI_CODE_LENGTH_CODES = 18;

    /* "code length of 8 is repeated" */
    public static final int BROTLI_INITIAL_REPEATED_CODE_LENGTH = 8;

    public static final int MIN_QUALITY_FOR_HQ_BLOCK_SPLITTING = 10;
    public static final int BROTLI_MAX_INPUT_BLOCK_BITS = 24;

    public static final int MAX_NUM_MATCHES_H10 = 128;
    public static final int BROTLI_LONG_COPY_QUICK_STEP = 16384;

    public static final int MAX_HUFFMAN_TREE_SIZE = 2 * BROTLI_NUM_COMMAND_SYMBOLS + 1;
    public static final int BROTLI_NUM_HISTOGRAM_DISTANCE_SYMBOLS = 544;

    public static final float kInfinity = 1.7e38f;
    public static final double kMinUTF8Ratio = 0.75;
    public static final int kHashMul32 = 0x1E35A7BD;

    public static final int BROTLI_FLINT_NEEDS_2_BYTES = 2;
    public static final int BROTLI_FLINT_NEEDS_1_BYTE = 1;
    public static final int BROTLI_FLINT_WAITING_FOR_PROCESSING = 0;
    public static final int BROTLI_FLINT_WAITING_FOR_FLUSHING = -1;
    public static final int BROTLI_FLINT_DONE = -2;

    public static final int BROTLI_MAX_NPOSTFIX = 3;
    public static final int HQ_ZOPFLIFICATION_QUALITY = 11;
    public static final int CLUSTERS_PER_BATCH = 16;
    public static final int HISTOGRAMS_PER_BATCH = 64;
    public static final int BROTLI_LITERAL_CONTEXT_BITS = 6;
    public static final int BROTLI_DISTANCE_CONTEXT_BITS = 2;
    public static final int BROTLI_NUM_INS_COPY_CODES = 24;

    public static final int BUCKET_SIZE = 1 << Hasher.BUCKET_BITS;

    public static final int BROTLI_MAX_STATIC_DICTIONARY_MATCH_LEN = 37;
    public static final int BROTLI_TRANSFORM_UPPERCASE_FIRST = 10;
    public static final int kInvalidMatch = 0xFFFFFFF;
    ;

    public static final int kMaxLiteralHistograms = 100;
    public static final int kMaxCommandHistograms = 50;
    public static final double kLiteralBlockSwitchCost = 28.1;
    public static final double kCommandBlockSwitchCost = 13.5;
    public static final double kDistanceBlockSwitchCost = 14.6;
    public static final int kLiteralStrideLength = 70;
    public static final int kCommandStrideLength = 40;
    public static final int kDistanceStrideLength = 40;
    public static final int kSymbolsPerLiteralHistogram = 544;
    public static final int kSymbolsPerCommandHistogram = 530;
    public static final int kSymbolsPerDistanceHistogram = 544;
    public static final int kMinLengthForBlockSplitting = 128;
    public static final int kIterMulForRefining = 2;
    public static final int kMinItersForRefining = 100;
    public static final short SYMBOL_BITS = 9;

    public static final int kDictHashMul32 = 0x1E35A7BD;
    public static final int kDictNumBits = 15;
    public static final int kCutoffTransformsCount = 10;
    public static final long kCutoffTransforms = Utils.get64Bits(new int[] {0x071B520A, 0xDA2D3200}, 0);
    public static final int BROTLI_SIZE_MAX = Integer.MAX_VALUE;
}
