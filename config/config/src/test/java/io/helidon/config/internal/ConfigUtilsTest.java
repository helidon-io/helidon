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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Priority;

import io.helidon.config.internal.ConfigUtils.ScheduledTask;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.Matchers;
import org.hamcrest.core.IsInstanceOf;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigUtils}.
 */
public class ConfigUtilsTest {

    private void testAsStream(Iterable<Integer> integers) {
        List<Integer> list = ConfigUtils.asStream(integers).sorted(Integer::compare).collect(Collectors.toList());
        assertThat(list, Matchers.hasSize(4));
        assertThat(list.get(0), equalTo(0));
        assertThat(list.get(1), equalTo(10));
        assertThat(list.get(2), equalTo(20));
        assertThat(list.get(3), equalTo(30));
    }

    @Test
    public void testAsStream() {
        testAsStream(Arrays.asList(20, 0, 30, 10));
        testAsStream(Arrays.asList(10, 30, 0, 20));
        testAsStream(Arrays.asList(0, 10, 20, 30));
    }

    private void testAsPrioritizedStream(Iterable<Provider> providers) {
        List<Provider> list = ConfigUtils.asPrioritizedStream(providers, 0).collect(Collectors.toList());
        assertThat(list, Matchers.hasSize(4));
        assertThat(list.get(0), IsInstanceOf.instanceOf(Provider3.class));
        assertThat(list.get(1), IsInstanceOf.instanceOf(Provider1.class));
        assertThat(list.get(2), IsInstanceOf.instanceOf(Provider4.class));
        assertThat(list.get(3), IsInstanceOf.instanceOf(Provider2.class));
    }

    @Test
    public void testAsPrioritizedStream() {
        testAsPrioritizedStream(Arrays.asList(new Provider1(), new Provider2(), new Provider3(), new Provider4()));
        testAsPrioritizedStream(Arrays.asList(new Provider4(), new Provider3(), new Provider2(), new Provider1()));
        testAsPrioritizedStream(Arrays.asList(new Provider2(), new Provider4(), new Provider1(), new Provider3()));
    }

    @Test
    public void testScheduledTaskInterruptedRepeatedly() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        ScheduledTask task = new ScheduledTask(Executors.newSingleThreadScheduledExecutor(),
                                               counter::incrementAndGet,
                                               Duration.ofMillis(80));
        task.schedule();
        task.schedule();
        task.schedule();
        task.schedule();
        task.schedule();

        //not yet finished
        assertThat(counter.get(), is(0));

        TimeUnit.MILLISECONDS.sleep(120);
        assertThat(counter.get(), is(1));
    }

    @Test
    public void testScheduledTaskExecutedRepeatedly() throws InterruptedException {
        CountDownLatch execLatch = new CountDownLatch(5);
        ScheduledTask task = new ScheduledTask(Executors.newSingleThreadScheduledExecutor(),
                                               execLatch::countDown,
                                               Duration.ZERO);
        /*
        Because invoking 'schedule' can cancel an existing action, keep track
        of cancelations in case the latch expires without reaching 0.
        */
        final long RESCHEDULE_DELAY_MS = 5;
        final int ACTIONS_TO_SCHEDULE = 5;
        int cancelations = 0;
        
        for (int i = 0; i < ACTIONS_TO_SCHEDULE; i++ ) {
            if (task.schedule()) {
                cancelations++;
            }
            TimeUnit.MILLISECONDS.sleep(RESCHEDULE_DELAY_MS);
        }
        /*
        The latch can either complete -- because all the scheduled actions finished --
        or it can expire at the timeout because at least one action did not finish, in
        which case the remaining latch value should not exceed the number of actions
        canceled. (Do not check for exact equality; some attempts to cancel
        an action might occur after the action was deemed to be not-yet-run or in-progress
        but actually runs to completion before the cancel is actually invoked.
        */
        assertThat(
                "Current execLatch count: " + execLatch.getCount() + ", cancelations: "
                        + "" + cancelations, 
                execLatch.await(3000, TimeUnit.MILLISECONDS) || execLatch.getCount() <= cancelations,
                is(true));
    }

    //
    // providers ...
    //

    interface Provider {
    }

    @Priority(20)
    static class Provider1 implements Provider {
    }

    static class Provider2 implements Provider {
    }

    @Priority(30)
    static class Provider3 implements Provider {
    }

    @Priority(10)
    static class Provider4 implements Provider {
    }

}
