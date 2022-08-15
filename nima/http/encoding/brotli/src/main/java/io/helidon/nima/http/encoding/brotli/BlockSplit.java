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

class BlockSplit {
    public int numTypes;  /* Amount of distinct types */
    public int numBlocks;  /* Amount of values in types and length */
    public int[] types;
    public int[] lengths;

    public int typesAllocSize;
    public int lengthsAllocSize;

    BlockSplit() {
        this.types = new int[1];
        this.lengths = new int[1];
        this.typesAllocSize = 1;
        this.lengthsAllocSize = 1;
    }

    public static void brotliInitBlockSplit(BlockSplit bs) {
        bs.numTypes = 0;
        bs.numBlocks = 0;
        bs.types = new int[1];
        bs.lengths = new int[1];
        bs.typesAllocSize = 0;
        bs.lengthsAllocSize = 0;
    }
}
