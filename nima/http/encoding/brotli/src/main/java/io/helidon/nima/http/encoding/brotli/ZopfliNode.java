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

class ZopfliNode {
    private int length;
    private int distance;
    private int dCodeInsertLength;
    private float cost;
    private int next;
    private int shortcut;

    static int computeShortestPathFromNodes(int numBytes, ZopfliNode[] nodes) {
        int num_commands = 0;
        while ((nodes[numBytes].dCodeInsertLength() & 0x7FFFFFF) == 0 &&
                nodes[numBytes].length() == 1) {
            --numBytes;
        }
        nodes[numBytes].next(Integer.MAX_VALUE);
        while (numBytes != 0) {
            int len = zopfliNodeCommandLength(nodes, numBytes);
            numBytes -= len;
            nodes[numBytes].next(len);
            num_commands++;
        }
        return num_commands;
    }

    static int zopfliNodeCommandLength(ZopfliNode[] nodes, int index) {
        return zopfliNodeCopyLength(nodes, index) + (nodes[index].dCodeInsertLength() & 0x7FFFFFF);
    }

    static int zopfliNodeCopyLength(ZopfliNode[] nodes, int index) {
        return nodes[index].length() & 0x1FFFFFF;
    }

    static int zopfliNodeCopyDistance(ZopfliNode[] nodes, int next) {
        return nodes[next].distance();
    }

    static int zopfliNodeDistanceCode(ZopfliNode[] nodes, int next) {
        int shortCode = nodes[next].dCodeInsertLength() >> 27;
        return shortCode == 0 ? zopfliNodeCopyDistance(nodes, next) + Constant.BROTLI_NUM_DISTANCE_SHORT_CODES - 1 :
                shortCode - 1;
    }

    static int zopfliNodeLengthCode(ZopfliNode[] nodes, int next) {
        int modifier = nodes[next].length() >> 25;
        return zopfliNodeCopyLength(nodes, next) + 9 - modifier;
    }

    static void updateZopfliNode(ZopfliNode[] nodes, int pos, int start, int len, int lenCode, int dist,
                                 int shortCode, float cost) {
        int next = pos + len;
        nodes[next].length(len | ((len + 9 - lenCode) << 25));
        nodes[next].distance(dist);
        nodes[next].dCodeInsertLength((shortCode << 27) | (pos - start));
        nodes[next].cost(cost);
    }

    int length() {
        return length;
    }

    void length(int length) {
        this.length = length;
    }

    int distance() {
        return distance;
    }

    void distance(int distance) {
        this.distance = distance;
    }

    int dCodeInsertLength() {
        return dCodeInsertLength;
    }

    void dCodeInsertLength(int dCodeInsertLength) {
        this.dCodeInsertLength = dCodeInsertLength;
    }

    float cost() {
        return cost;
    }

    void cost(float cost) {
        this.cost = cost;
    }

    int next() {
        return next;
    }

    void next(int next) {
        this.next = next;
    }

    int shortcut() {
        return shortcut;
    }

    void shortcut(int shortcut) {
        this.shortcut = shortcut;
    }
}
