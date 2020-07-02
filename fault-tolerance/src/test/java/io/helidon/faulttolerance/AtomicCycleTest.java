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

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AtomicCycleTest {
    @Test
    void testSingleThread() {
        AtomicCycle cycle = new AtomicCycle(2);

        assertThat(cycle.incrementAndGet(), is(0));
        assertThat(cycle.incrementAndGet(), is(1));
        assertThat(cycle.incrementAndGet(), is(2));
        assertThat(cycle.incrementAndGet(), is(0));
        assertThat(cycle.incrementAndGet(), is(1));
        assertThat(cycle.incrementAndGet(), is(2));
        assertThat(cycle.incrementAndGet(), is(0));
    }

    @Test
    void testParallel() throws InterruptedException {
        int numberCount = 5;
        int threadCount = 8;
        int inThreadCycleCount = 10;

        AtomicCycle cycle = new AtomicCycle(numberCount - 1);
        Thread[] threads = new Thread[threadCount];
        int[] results = new int[numberCount * threadCount * inThreadCycleCount];
        for (int i = 0; i < threads.length; i++) {
            int finalIndex = i;
            threads[i] = new Thread(() -> {
                int firstIndex = finalIndex * numberCount * inThreadCycleCount;
                // try it five times to have a full cycle
                int cycles = inThreadCycleCount * numberCount;
                for (int j = 0; j < cycles; j++) {
                    results[firstIndex + j] = cycle.incrementAndGet();
                }
            });
        }
        // all threads are prepared
        for (Thread thread : threads) {
            thread.start();
        }

        // let them finish
        for (Thread thread : threads) {
            thread.join();
        }

        long eachNumberCount = threadCount * inThreadCycleCount;
        // and now validate the result - even though each thread may get quite random numbers
        // the total number of cycles must match
        Arrays.sort(results);
        for (int i = 0; i < numberCount; i++) {
            // i is in range 0 .. 4 - exactly what our AtomicCycle returns
            long count = count(results, i);
            assertThat("There should be a number of " + i + "s equal to number of threads", count,
                       is(eachNumberCount));
        }
    }

    private long count(int[] results, int number) {
        return IntStream.of(results)
                .filter(value -> value == number)
                .count();
    }
}