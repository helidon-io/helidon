/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link UnorderedCollectorSupport}.
 */
class UnorderedCollectorSupportTest {

    private void fillAndTest(int threadsCount, int valuesPerThread) throws Exception {
        UnorderedCollectorSupport<Integer, ?, Set<Integer>> support = new UnorderedCollectorSupport<>(Collectors.<Integer>toSet());
        CountDownLatch latch = new CountDownLatch(threadsCount);
        for (int i = 0; i < threadsCount; i++) {
            int base = i * valuesPerThread;
            new Thread(() -> {
                for (int j = 0; j < valuesPerThread; j++) {
                    support.add(base + j);
                }
                latch.countDown();
            }).start();
        }
        if (!latch.await(1, TimeUnit.MINUTES)) {
            throw new AssertionError("Timeout!");
        }
        support.complete();
        Set<Integer> result = support.getResult()
                                       .toCompletableFuture()
                                       .get(10, TimeUnit.SECONDS);
        assertEquals(threadsCount * valuesPerThread, result.size());
    }

    @Test
    void singleThread() throws Exception {
        fillAndTest(1, 100_000);
    }

    @Test
    void eightThreads() throws Exception {
        fillAndTest(8, 400_000);
    }

    @Test
    void threeHundredsThreads() throws Exception {
        fillAndTest(300, 20_000);
    }
}
