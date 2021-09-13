/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class TestPeriodicExecutor {

    private static final int SLEEP_TIME_MS = 1500;

    private static final int FAST_INTERVAL = 250;
    private static final int SLOW_INTERVAL = 400;

    private static final double SLOWDOWN_FACTOR = 0.80; // for slow pipelines!

    private static final double MIN_FAST_COUNT = 1500 / FAST_INTERVAL * SLOWDOWN_FACTOR;
    private static final double MIN_SLOW_COUNT = 1500 / SLOW_INTERVAL * SLOWDOWN_FACTOR;

    @Test
    void testWithNoDeferrals() throws InterruptedException {

        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            exec.startExecutor();
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            exec.enrollRunner(() -> countA.incrementAndGet(), Duration.ofMillis(FAST_INTERVAL));
            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            Thread.sleep(SLEEP_TIME_MS);

            assertThat("CountA", (double) countA.get(), is(greaterThan(MIN_FAST_COUNT)));
            assertThat("CountB", (double) countB.get(), is(greaterThan(MIN_SLOW_COUNT)));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }

    @Test
    void testWithDeferredEnrollments() throws InterruptedException {
        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            exec.enrollRunner(() -> countA.incrementAndGet(), Duration.ofMillis(FAST_INTERVAL));

            exec.startExecutor();

            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            Thread.sleep(SLEEP_TIME_MS);

            assertThat("CountA", (double) countA.get(), is(greaterThan(MIN_FAST_COUNT)));
            assertThat("CountB", (double) countB.get(), is(greaterThan(MIN_SLOW_COUNT)));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }

    @Test
    void testWithLateEnrollment() throws InterruptedException {
        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            exec.enrollRunner(() -> countA.incrementAndGet(), Duration.ofMillis(FAST_INTERVAL));

            exec.startExecutor();
            Thread.sleep(SLEEP_TIME_MS);

            exec.stopExecutor();

            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            assertThat("CountA", (double) countA.get(), is(greaterThan(MIN_FAST_COUNT))); // should be 8
            assertThat("CountB", (double) countB.get(), is(0.0));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }
}
