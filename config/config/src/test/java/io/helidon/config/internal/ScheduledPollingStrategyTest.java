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

package io.helidon.config.internal;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Flow;
import io.helidon.config.spi.PollingStrategy.PollingEvent;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScheduledPollingStrategy}.
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
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION, null);

        assertThat(pollingStrategy.getExecutor(), is(nullValue()));
    }

    @Test
    public void testStartPolling() throws InterruptedException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(3);

        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION, null);

        pollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testStopPolling() throws InterruptedException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(1);

        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION, null);

        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        pollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Reached onError", throwable);
            }

            @Override
            public void onComplete() {
                fail("Reached onComplete`");
            }
        });
        assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        assertThat(pollingStrategy.getExecutor(), not(nullValue()));

        //cancel subscription
        subscriptionRef.get().cancel();

        assertThat(pollingStrategy.getExecutor(), is(nullValue()));
    }

    @Test
    public void testRestartPollingWithCustomExecutor() throws InterruptedException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(1);

        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                Executors.newScheduledThreadPool(1));

        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();

        pollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Reached onError", throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));

        //cancel subscription
        subscriptionRef.get().cancel();
        assertThat(pollingStrategy.getExecutor(), not(nullValue()));

        //subscribe again
        pollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Reached onError", throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testRestartPollingWithDefaultExecutor() throws InterruptedException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(1);

        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION, null);

        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();

        pollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Reached onError", throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(200, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));

        //cancel subscription
        subscriptionRef.get().cancel();
        assertThat(pollingStrategy.getExecutor(), is(nullValue()));

        //subscribe again
        pollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Reached onError", throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(NEXT_LATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
    }

    /* NOTE: TEMPORARILY MOVED FROM POLLING_STRATEGY, WILL BE PUBLIC API AGAIN LATER, Issue #14.
    @Test
    public void testScheduledPollingStrategyWithAdaptiveFromHelper() throws InterruptedException {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(3);

        PollingStrategy pollingStrategy = ScheduledPollingStrategy.adaptive(Duration.ofMillis(1));

        pollingStrategy.ticks().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
            }

            @Override
            public void onNext(PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail();
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        assertThat(nextLatch.await(100, TimeUnit.MILLISECONDS), is(true));
    }
    */

    @Test
    public void testScheduledFuture() {
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                Executors.newScheduledThreadPool(1));
        assertThat(pollingStrategy.getScheduledFuture(), nullValue());

        pollingStrategy.startScheduling();

        assertThat(pollingStrategy.getScheduledFuture(), notNullValue());
        assertThat(pollingStrategy.getScheduledFuture().isCancelled(), is(false));
    }

    @Test
    public void testScheduledFutureCleaning() throws InterruptedException {
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                Executors.newScheduledThreadPool(1));
        assertThat(pollingStrategy.getScheduledFuture(), nullValue());

        pollingStrategy.startScheduling();
        TimeUnit.SECONDS.sleep(DELAY_AFTER_START_SCHEDULING_BEFORE_STOP_SCHEDULING);
        pollingStrategy.stopScheduling();

        assertThat(pollingStrategy.getScheduledFuture(), notNullValue());
        assertThat(pollingStrategy.getScheduledFuture().isCancelled(), is(true));
    }

    @Test
    public void testExecutor() throws InterruptedException {
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                null);
        assertThat(pollingStrategy.getExecutor(), nullValue());

        pollingStrategy.startScheduling();

        assertThat(pollingStrategy.getExecutor(), notNullValue());
        assertThat(pollingStrategy.getExecutor().awaitTermination(1, TimeUnit.SECONDS), is(false));
    }

    @Test
    public void testCustomExecutor() throws InterruptedException {
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                Executors.newScheduledThreadPool(1));
        assertThat(pollingStrategy.getExecutor(), notNullValue());

        pollingStrategy.startScheduling();

        assertThat(pollingStrategy.getExecutor(), notNullValue());
        assertThat(pollingStrategy.getExecutor().awaitTermination(1, TimeUnit.SECONDS), is(false));
    }

    @Test
    public void testExecutorCleaning() throws InterruptedException {
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                null);
        assertThat(pollingStrategy.getExecutor(), nullValue());

        pollingStrategy.startScheduling();
        TimeUnit.SECONDS.sleep(DELAY_AFTER_START_SCHEDULING_BEFORE_STOP_SCHEDULING);
        pollingStrategy.stopScheduling();

        assertThat(pollingStrategy.getExecutor(), nullValue());
    }

    @Test
    public void testCustomExecutorCleaning() throws InterruptedException {
        ScheduledPollingStrategy pollingStrategy = new ScheduledPollingStrategy(() -> POLLING_STRATEGY_DURATION,
                                                                                Executors.newScheduledThreadPool(1));
        assertThat(pollingStrategy.getExecutor(), notNullValue());

        pollingStrategy.startScheduling();
        TimeUnit.SECONDS.sleep(DELAY_AFTER_START_SCHEDULING_BEFORE_STOP_SCHEDULING);
        pollingStrategy.stopScheduling();

        assertThat(pollingStrategy.getExecutor(), notNullValue());
        assertThat(pollingStrategy.getExecutor().awaitTermination(1, TimeUnit.SECONDS), is(false));
    }

    /* NOTE: TEMPORARILY MOVED FROM POLLING_STRATEGY, WILL BE PUBLIC API AGAIN LATER, Issue #14.
    @Test
    public void testApi() {
        PollingStrategies.regular(Duration.ofSeconds(60));

        ScheduledPollingStrategy.adaptive(Duration.ofSeconds(60));

        PollingStrategies.watch(Paths.get("/tmp/app.conf"));

        PollingStrategy configuredAdaptiveScheduledStrategy = ScheduledPollingStrategy.recurringPolicyBuilder(
                ScheduledPollingStrategy.RecurringPolicy.adaptiveBuilder(Duration.ofMinutes(4))
                        .min(Duration.ofMinutes(1))
                        .max(Duration.ofHours(1))
                        .shorten((current, count) -> current.dividedBy(count))
                        .build())
                .executor(Executors.newScheduledThreadPool(1))
                .build();

        PollingStrategy withCustomBackoff = ScheduledPollingStrategy.recurringPolicyBuilder(
                new ScheduledPollingStrategy.RecurringPolicy() {
                    @Override
                    public Duration interval() {
                        return Duration.ofHours(1);
                    }

                    @Override
                    public void shorten() {

                    }
                })
                .build();
    }
    */
}
