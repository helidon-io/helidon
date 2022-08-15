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

class StartPosQueue {
    public PosData[] q = new PosData[8];
    public int idx;

    public static void initStartPosQueue(StartPosQueue posQueue) {
        posQueue.idx = 0;
    }

    public static void startPosQueuePush(StartPosQueue queue, PosData posdata) {
        int offset = ~(queue.idx++) & 7;
        int len = startPosQueueSize(queue);
        int i;
        queue.q[offset] = posdata;
        for (i = 1; i < len; ++i) {
            if (queue.q[offset & 7].costDiff > queue.q[(offset + 1) & 7].costDiff) {
                swap(queue.q, offset & 7, (offset + 1) & 7);
            }
            ++offset;
        }
    }

    public static int startPosQueueSize(StartPosQueue queue) {
        return Math.min(queue.idx, 8);
    }

    public static PosData startPosQueueAt(StartPosQueue queue, int index) {
        return queue.q[(index - queue.idx) & 7];
    }

    private static void swap(PosData[] q, int first, int second) {
        PosData temp = q[first];
        q[first] = q[second];
        q[second] = temp;
    }
}
