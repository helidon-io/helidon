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

package io.helidon.config;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.config.spi.ChangeEventType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link io.helidon.config.ScheduledPollingStrategy}.
 */
public class ScheduledPollingStrategyTest {

    private static final int DELAY_AFTER_START_SCHEDULING_BEFORE_STOP_SCHEDULING = 1;

    /*
    Polling strategy time needs to be long enough so the ScheduledFuture is
    still running when the clean-up tests try to cancel it. Those tests
    expect the ScheduledFuture to have been canceled, not complete normally.
     */
    private static final int POLLING_STRATEGY_MILLIS = 100;
    private static final Duration POLLING_STRATEGY_DURATION =
            Duration.ofMillis(POLLING_STRATEGY_MILLIS);
    private static final int NEXT_LATCH_WAIT_MILLIS = POLLING_STRATEGY_MILLIS * 5;

    @Test
    public void testNotStartedYet() {
        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> POLLING_STRATEGY_DURATION)
                .build();

        assertThat(pollingStrategy.executor(), is(notNullValue()));
    }

    @Test
    public void testStartPolling() throws InterruptedException {
        CountDownLatch nextLatch = new CountDownLatch(3);

        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> POLLING_STRATEGY_DURATION)
                .build();

        pollingStrategy.start(when -> {
            nextLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testStopPolling() throws InterruptedException {
        CountDownLatch nextLatch = new CountDownLatch(1);

        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> POLLING_STRATEGY_DURATION)
                .build();

        pollingStrategy.start(when -> {
            nextLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        assertThat("Executor should be running", pollingStrategy.executor().isShutdown(), is(false));

        //cancel subscription
        pollingStrategy.stop();

        assertThat(pollingStrategy.executor().isShutdown(), is(true));
    }

    @Test
    public void testRestartPollingWithCustomExecutor() throws InterruptedException {
        CountDownLatch firstLatch = new CountDownLatch(1);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.create(() -> POLLING_STRATEGY_DURATION,
                                                                                   executor);

        pollingStrategy.start(when -> {
            firstLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(firstLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));

        //cancel subscription
        pollingStrategy.stop();
        assertThat("Custom executor should not get shut down",
                   pollingStrategy.executor().isShutdown(),
                   is(false));

        CountDownLatch secondLatch = new CountDownLatch(1);

        //subscribe again
        pollingStrategy.start(when -> {
            secondLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(secondLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));

        executor.shutdown();
    }

    @Test
    public void testRestartPollingWithDefaultExecutor() throws InterruptedException {
        CountDownLatch firstLatch = new CountDownLatch(1);

        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> POLLING_STRATEGY_DURATION)
                .build();

        pollingStrategy.start(when -> {
            firstLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(firstLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));

        //cancel subscription
        pollingStrategy.stop();
        assertThat("Default executor should get shut down",
                   pollingStrategy.executor().isShutdown(),
                   is(true));

        CountDownLatch secondLatch = new CountDownLatch(1);

        //subscribe again
        pollingStrategy.start(when -> {
            secondLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(secondLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
    }
}
