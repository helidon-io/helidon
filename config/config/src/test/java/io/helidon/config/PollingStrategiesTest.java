/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.PollingStrategy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link PollingStrategies}.
 */
public class PollingStrategiesTest {

    @Test
    void testRegularDefaultsPoll() throws InterruptedException {
        var strategy = (ScheduledPollingStrategy) PollingStrategies.regular(Duration.ofMillis(10)).build();
        var tick = new CountDownLatch(1);
        try {
            strategy.start(_ -> {
                tick.countDown();
                return ChangeEventType.UNCHANGED;
            });
            assertThat("Omitting an executor should produce polling ticks", tick.await(10, TimeUnit.SECONDS), is(true));
        } finally {
            strategy.stop();
        }
        assertThat("The default regular strategy owns its pool", strategy.executor().isShutdown(), is(true));
    }

    @Test
    void testNullExecutorPreservesCustomSelection() {
        assertThrows(NullPointerException.class, () -> PollingStrategies.regular(Duration.ofMillis(10)).executor(null));
        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            var builder = PollingStrategies.regular(Duration.ofMillis(10)).executor(executor);
            assertThrows(NullPointerException.class, () -> builder.executor(null));
            var strategy = (ScheduledPollingStrategy) builder.build();
            try {
                assertThat("An invalid setter must preserve the selected executor", strategy.executor(), sameInstance(executor));
            } finally {
                strategy.stop();
            }
            assertThat("A supplied executor remains caller-owned", executor.isShutdown(), is(false));
        }
    }

    @Test
    void testClearExecutorAffectsFutureBuilds() throws InterruptedException {
        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            var builder = PollingStrategies.regular(Duration.ofMillis(10)).executor(executor);
            var custom = (ScheduledPollingStrategy) builder.build();
            var defaults = (ScheduledPollingStrategy) builder.clearExecutor().clearExecutor().build();
            var restored = (ScheduledPollingStrategy) builder.executor(executor).build();
            var tick = new CountDownLatch(1);
            try {
                assertThat("Clearing must not close the supplied pool", executor.isShutdown(), is(false));
                assertThat("An existing strategy retains its supplied pool", custom.executor(), sameInstance(executor));
                assertThat("A cleared builder selects a separate default pool", defaults.executor(), not(sameInstance(executor)));
                assertThat("A custom pool can be selected again", restored.executor(), sameInstance(executor));
                defaults.start(_ -> {
                    tick.countDown();
                    return ChangeEventType.UNCHANGED;
                });
                assertThat("The cleared default should poll", tick.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                custom.stop();
                defaults.stop();
                restored.stop();
            }
            assertThat("The cleared default owns its pool", defaults.executor().isShutdown(), is(true));
            assertThat("Custom pools remain caller-owned", executor.isShutdown(), is(false));
        }
    }

    @Test
    public void testTicksNop() throws InterruptedException {
        PollingStrategy pollingStrategy = PollingStrategies.nop();

        AtomicInteger count = new AtomicInteger();
        CountDownLatch onComplete = new CountDownLatch(1);

        pollingStrategy.start(when -> {
            count.incrementAndGet();
            onComplete.countDown();
            return ChangeEventType.UNCHANGED;
        });

        assertThat(onComplete.await(100, TimeUnit.MILLISECONDS), is(false));
    }

}
