/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.PollingStrategy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    public void testStartPollingRequiresPolled() {
        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> POLLING_STRATEGY_DURATION)
                .build();

        assertThrows(NullPointerException.class, () -> pollingStrategy.start(null));
    }

    @Test
    public void testPollingContinuesAfterTransientFailure() throws InterruptedException {
        CountDownLatch recoveredLatch = new CountDownLatch(1);
        AtomicInteger pollCount = new AtomicInteger();

        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> POLLING_STRATEGY_DURATION)
                .build();

        try {
            pollingStrategy.start(when -> {
                if (pollCount.incrementAndGet() == 1) {
                    throw new ConfigException("Transient reload failure");
                }
                recoveredLatch.countDown();
                return ChangeEventType.UNCHANGED;
            });

            assertThat("Polling should continue after a transient reload failure",
                       recoveredLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                       is(true));
        } finally {
            pollingStrategy.stop();
        }
    }

    @Test
    public void testRepeatedPollingFailureLoggingResetsAfterSuccess() throws InterruptedException {
        CountDownLatch thirdLogLatch = new CountDownLatch(3);
        AtomicInteger pollCount = new AtomicInteger();
        List<LogRecord> logRecords = new CopyOnWriteArrayList<>();
        Logger logger = Logger.getLogger(ScheduledPollingStrategy.class.getName());
        Level originalLevel = logger.getLevel();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logRecords.add(record);
                thirdLogLatch.countDown();
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };

        ScheduledPollingStrategy pollingStrategy = ScheduledPollingStrategy.builder()
                .recurringPolicy(() -> Duration.ofMillis(20))
                .build();
        ConfigException firstFailure = new ConfigException("First reload failure");
        ConfigException repeatedFailure = new ConfigException("Repeated reload failure");
        ConfigException failureAfterRecovery = new ConfigException("Failure after recovery");

        try {
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.FINE);

            PollingStrategy.Polled polled = when -> {
                switch (pollCount.incrementAndGet()) {
                case 1:
                    throw firstFailure;
                case 2:
                    throw repeatedFailure;
                case 3:
                    return ChangeEventType.UNCHANGED;
                case 4:
                    throw failureAfterRecovery;
                default:
                    return ChangeEventType.UNCHANGED;
                }
            };

            pollingStrategy.start(polled);

            assertThat("Third log record should be published",
                       thirdLogLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                       is(true));
            pollingStrategy.stop();

            assertThat(logRecords.stream().map(LogRecord::getLevel).toList(),
                       is(List.of(Level.WARNING, Level.FINE, Level.WARNING)));
            assertThat(logRecords.get(0).getMessage(), containsString("Failed to poll config source"));
            assertThat(logRecords.get(0).getThrown(), instanceOf(ConfigException.class));
            assertThat(logRecords.get(0).getThrown(), sameInstance(firstFailure));
            assertThat(logRecords.get(0).getThrown().getMessage(),
                       containsString("First reload failure"));
            assertThat(logRecords.get(1).getMessage(), is("Config polling failure"));
            assertThat(logRecords.get(1).getThrown(), instanceOf(ConfigException.class));
            assertThat(logRecords.get(1).getThrown(), sameInstance(repeatedFailure));
            assertThat(logRecords.get(1).getThrown().getMessage(),
                       containsString("Repeated reload failure"));
            assertThat(logRecords.get(2).getMessage(), containsString("Failed to poll config source"));
            assertThat(logRecords.get(2).getThrown(), instanceOf(ConfigException.class));
            assertThat(logRecords.get(2).getThrown(), sameInstance(failureAfterRecovery));
            assertThat(logRecords.get(2).getThrown().getMessage(),
                       containsString("Failure after recovery"));
        } finally {
            pollingStrategy.stop();
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.removeHandler(handler);
        }
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
