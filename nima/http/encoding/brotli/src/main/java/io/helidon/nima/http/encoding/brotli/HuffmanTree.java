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
 * Probably rename it to HuffmanNode.
 */
class HuffmanTree {
    private long totalCount = 0;
    private int indexLeft = -1;
    private int indexRightOrValue;

    public HuffmanTree() {
    }

    public static void initHuffmanTree(HuffmanTree huffmanTree, long size, int left, int right) {
        huffmanTree.setTotalCount(size);
        huffmanTree.setIndexLeft(left);
        huffmanTree.setIndexRightOrValue(right);
    }

    public long getTotalCount() {
        return this.totalCount;
    }

    public void setTotalCount(long size) {
        this.totalCount = size;
    }

    public int getIndexLeft() {
        return this.indexLeft;
    }

    public void setIndexLeft(int left) {
        this.indexLeft = left;
    }

    public int getIndexRightOrValue() {
        return this.indexRightOrValue;
    }

    public void setIndexRightOrValue(int right) {
        this.indexRightOrValue = right;
    }

}
