/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link LazyValue}.
 */
class LazyValueTest {

    @Test
    void testValue() {
        String text = "Helidon";
        LazyValue<String> value = LazyValue.create(text);

        String s = value.get();
        assertThat(s, is(text));
    }

    @Test
    void testSupplier() {
        String text = "Helidon";
        AtomicInteger called = new AtomicInteger();

        LazyValue<String> value = LazyValue.create(() -> {
            called.incrementAndGet();
            return text;
        });

        String s = value.get();
        assertThat(s, is(text));
        assertThat(called.get(), is(1));

        s = value.get();
        assertThat(s, is(text));
        assertThat(called.get(), is(1));
    }

    @Test
    void testSupplierParallel() {
        String text = "Helidon";
        AtomicInteger called = new AtomicInteger();
        AtomicInteger threadsStarted = new AtomicInteger();

        LazyValue<String> value = LazyValue.create(() -> {
            called.incrementAndGet();
            return text;
        });

        Errors.Collector errors = Errors.collector();

        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        Thread[] testingThreads = new Thread[threadCount];
        for (int i = 0; i < testingThreads.length; i++) {
            testingThreads[i] = new Thread(() -> {
                threadsStarted.incrementAndGet();
                try {
                    barrier.await();
                } catch (Exception e) {
                    errors.fatal("Failed to start, barrier failed: " + e.getMessage());
                }
                String s = value.get();
                if (!text.equals(s)) {
                    errors.fatal("Got wrong value. Expected " + text + ", but got: " + s);
                }
            });
        }

        for (Thread testingThread : testingThreads) {
            testingThread.start();
        }

        for (Thread testingThread : testingThreads) {
            try {
                testingThread.join();
            } catch (InterruptedException ignored) {
            }
        }

        errors.collect().checkValid();
        assertThat(called.get(), is(1));
        assertThat(threadsStarted.get(), is(threadCount));
    }
}