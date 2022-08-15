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

class BlockSplitIterator {
    BlockSplit split;  /* Not owned. */
    int splitIndex;
    int idx;
    int type;
    int length;

    BlockSplitIterator() {
        this.split = new BlockSplit();
    }

    public static void initBlockSplitIterator(BlockSplitIterator self, BlockSplit split) {
        self.split = split;
        self.idx = 0;
        self.type = 0;
        self.length = split.lengths != null ? split.lengths[0] : 0;
    }

    public static void blockSplitIteratorNext(BlockSplitIterator self) {
        if (self.length == 0) {
            ++self.idx;
            self.type = self.split.types[self.idx];
            self.length = self.split.lengths[self.idx];
        }
        --self.length;
    }
}
