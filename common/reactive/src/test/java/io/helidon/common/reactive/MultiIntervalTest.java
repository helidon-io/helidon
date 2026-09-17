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
package io.helidon.common.reactive;

import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

public class MultiIntervalTest {

    private static ScheduledExecutorService executor;

    @BeforeAll
    public static void beforeClass() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    public static void afterClass() {
        executor.shutdown();
    }

    @Test
    public void normal() {
        TestSubscriber<Long> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.interval(1, TimeUnit.MILLISECONDS, executor)
                .subscribe(ts);

        ts.awaitCount(10)
                .cancel()
                .assertNotTerminated();
    }

    @Test
    public void normalBackpressured() throws Exception {
        TestSubscriber<Long> ts = new TestSubscriber<>();

        Multi.interval(10, TimeUnit.MILLISECONDS, executor)
                .subscribe(ts);

        Thread.sleep(100);

        ts.assertEmpty()
        .request(15);

        ts.awaitCount(15)
                .cancel()
                .assertNotTerminated();
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-1, true", "0, false", "-1, false"})
    void invalidDemandCancelsPeriodicTask(long demand, boolean requestOnSubscribe) throws Exception {
        var scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        try {
            var subscriptionRef = new AtomicReference<Flow.Subscription>();
            var subscriber = new TestSubscriber<Long>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    super.onSubscribe(subscription);
                    subscriptionRef.set(subscription);
                    if (requestOnSubscribe) {
                        subscription.request(demand);
                    }
                }
            };

            Multi.interval(1, TimeUnit.DAYS, scheduler).subscribe(subscriber);
            if (!requestOnSubscribe) {
                subscriptionRef.get().request(demand);
            }

            subscriber.assertFailure(IllegalArgumentException.class);
            assertThat("Terminated interval must not leave a periodic task scheduled", scheduler.getQueue(), empty());
            assertThat("The caller still owns the executor", scheduler.isShutdown(), is(false));
            assertThat(scheduler.submit(() -> "available").get(5, TimeUnit.SECONDS), is("available"));
        } finally {
            scheduler.shutdownNow();
            assertThat("Test executor did not terminate", scheduler.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
    }
}
