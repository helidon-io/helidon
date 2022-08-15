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

class BlockEncoder {
    int histogramLength;
    int numBlockTypes;
    int[] blockTypes;  /* Not owned. */
    int[] blockLengths;  /* Not owned. */
    int numBlocks;
    BlockSplitCode blockSplitCode;
    int blockIx;
    int blockLen;
    int entropyIx;
    int[] depths;
    int[] bits;

    BlockEncoder() {
    }

    public static void initBlockEncoder(BlockEncoder self, int histogramLength, int numBlockTypes,
                                        int[] blockTypes, int[] blockLengths, int numBlocks) {
        self.histogramLength = histogramLength;
        self.numBlockTypes = numBlockTypes;
        self.blockTypes = blockTypes;
        self.blockLengths = blockLengths;
        self.numBlocks = numBlocks;
        self.blockSplitCode = new BlockSplitCode();
        BlockTypeCodeCalculator.initBlockTypeCodeCalculator(self.blockSplitCode.type_code_calculator);
        self.blockIx = 0;
        self.blockLen = numBlocks == 0 ? 0 : blockLengths[0];
        self.entropyIx = 0;
        self.depths = new int[1];
        self.bits = new int[1];
    }
}
