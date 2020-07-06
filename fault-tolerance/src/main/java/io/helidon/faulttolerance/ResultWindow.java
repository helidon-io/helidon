/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Floating window of results.
 * The status is eventually consistent - there may be inconsistencies under heavier load (the sum may be off-beat
 * from the actual results due to parallel execution).
 * This should not be a significant issue, as the calculations work on a state (that may change anyway when checking
 * whether to open the circuit).
 */
final class ResultWindow {
    private final AtomicInteger currentSum = new AtomicInteger();
    private final AtomicCycle index;
    private final AtomicInteger[] results;
    private final int thresholdSum;

    ResultWindow(int size, int ratio) {
        results = new AtomicInteger[size];
        for (int i = 0; i < size; i++) {
            results[i] = new AtomicInteger();
        }
        index = new AtomicCycle(size - 1);
        // calculate the sum needed to open the breaker
        int threshold = (size * ratio) / 100;
        thresholdSum = threshold == 0 ? 1 : threshold;

    }

    void update(Result resultEnum) {
        // success is zero, failure is 1
        int result = resultEnum.ordinal();

        AtomicInteger mine = results[index.incrementAndGet()];
        int origValue = mine.getAndSet(result);

        if (origValue == result) {
            // no change
            return;
        }

        if (origValue == 1) {
            currentSum.decrementAndGet();
        } else {
            currentSum.incrementAndGet();
        }
    }

    boolean shouldOpen() {
        return currentSum.get() >= thresholdSum;
    }

    void reset() {
        // "soft" reset - send in success equal to window size
        for (int i = 0; i < results.length; i++) {
            update(Result.SUCCESS);
        }
    }

    // order is significant, do not change
    enum Result {
        SUCCESS,
        FAILURE
    }
}
