/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.task;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DeadlineGuardTest {

    @Test
    void invokesTimeoutActionAfterTimeout() throws Exception {
        ScheduledThreadPoolExecutor executor = executor();
        try {
            CountDownLatch invoked = new CountDownLatch(1);

            try (DeadlineGuard guard = DeadlineGuard.create(Duration.ofMillis(10), invoked::countDown, executor)) {
                assertThat(invoked.await(5, TimeUnit.SECONDS), is(true));
                assertThat(guard.timedOut(), is(true));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closeCancelsTimeoutAction() {
        ScheduledThreadPoolExecutor executor = executor();
        try {
            AtomicBoolean invoked = new AtomicBoolean();

            DeadlineGuard guard = DeadlineGuard.create(Duration.ofDays(1), () -> invoked.set(true), executor);
            guard.close();

            assertThat(guard.timedOut(), is(false));
            assertThat(invoked.get(), is(false));
            assertThat(executor.getQueue().isEmpty(), is(true));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timeoutStateIsPublishedWhenTimeoutWins() throws Exception {
        ScheduledThreadPoolExecutor executor = executor();
        try {
            CountDownLatch timeoutClaimed = new CountDownLatch(1);
            CountDownLatch releaseTimeoutClaim = new CountDownLatch(1);
            CountDownLatch timeoutActionStarted = new CountDownLatch(1);
            CountDownLatch releaseTimeoutAction = new CountDownLatch(1);

            DeadlineGuard guard = DeadlineGuard.create(Duration.ofNanos(1),
                                                       () -> {
                                                           timeoutActionStarted.countDown();
                                                           try {
                                                               releaseTimeoutAction.await();
                                                           } catch (InterruptedException e) {
                                                               Thread.currentThread().interrupt();
                                                           }
                                                       },
                                                       executor,
                                                       () -> {
                                                           timeoutClaimed.countDown();
                                                           try {
                                                               releaseTimeoutClaim.await();
                                                           } catch (InterruptedException e) {
                                                               Thread.currentThread().interrupt();
                                                           }
                                                       });

            try {
                assertThat(timeoutClaimed.await(5, TimeUnit.SECONDS), is(true));
                assertThat(guard.timedOut(), is(true));
                releaseTimeoutClaim.countDown();
                assertThat(timeoutActionStarted.await(5, TimeUnit.SECONDS), is(true));
                guard.close();
                assertThat(guard.timedOut(), is(true));
            } finally {
                releaseTimeoutClaim.countDown();
                releaseTimeoutAction.countDown();
                guard.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void zeroTimeoutDoesNotScheduleTimeoutAction() {
        ScheduledThreadPoolExecutor executor = executor();
        try {
            AtomicBoolean invoked = new AtomicBoolean();

            try (DeadlineGuard guard = DeadlineGuard.create(Duration.ZERO, () -> invoked.set(true), executor)) {
                assertThat(guard.timedOut(), is(false));
            }

            assertThat(invoked.get(), is(false));
            assertThat(executor.getQueue().isEmpty(), is(true));
        } finally {
            executor.shutdownNow();
        }
    }

    private static ScheduledThreadPoolExecutor executor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
