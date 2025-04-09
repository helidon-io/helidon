/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

/**
 * A Set like implementation optimized for integers.
 */
class IntSet {
    private final long[] data;
    private int size;

    /**
     * Create a new set.
     *
     * @param sizeInBits expected size
     */
    IntSet(int sizeInBits) {
        data = new long[(sizeInBits + 63) / 64];
    }

    /**
     * Get next value.
     *
     * <pre>
     * for(int i=bs.nextSetBit(0); i>=0; i=bs.nextSetBit(i+1)) {
     *   // operate on index i here
     * }
     * </pre>
     *
     * @param i index
     * @return next value
     */
    public int nextSetBit(int i) {
        int x = i / 64;
        if (x >= data.length) {
            return -1;
        }
        long w = data[x];
        w >>>= (i % 64);
        if (w != 0) {
            return i + Long.numberOfTrailingZeros(w);
        }
        ++x;
        for (; x < data.length; ++x) {
            if (data[x] != 0) {
                return x * 64 + Long.numberOfTrailingZeros(data[x]);
            }
        }
        return -1;
    }

    /**
     * Add next value.
     *
     * @param i value
     */
    public void add(int i) {
        long mask = (1L << (i % 64));
        if ((data[i / 64] & mask) == 0) {
            data[i / 64] |= mask;
            size++;
        }
    }

    /**
     * Remove a value.
     *
     * @param i value
     */
    public void remove(int i) {
        long mask = (1L << (i % 64));
        if ((data[i / 64] & mask) > 0) {
            data[i / 64] &= ~mask;
            size--;
        }
    }

    /**
     * Current size.
     *
     * @return size
     */
    public int size() {
        return size;
    }
}
