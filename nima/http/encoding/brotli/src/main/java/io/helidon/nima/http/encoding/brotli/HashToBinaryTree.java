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

class HashToBinaryTree {
    public int windowMask;
    public int[] buckets;
    public int invalidPos;
    public int[] forest;

    HashToBinaryTree() {
        buckets = new int[Constant.BUCKET_SIZE];
        forest = new int[10];
    }

    public static int leftChildIndex(HashToBinaryTree tree, int index) {
        return 2 * (index & tree.windowMask);
    }

    public static int rightChildIndex(HashToBinaryTree tree, int index) {
        return 2 * (index & tree.windowMask) + 1;
    }
}
