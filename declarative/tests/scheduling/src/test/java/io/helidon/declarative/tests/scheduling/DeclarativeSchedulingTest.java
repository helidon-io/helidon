/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.scheduling;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.scheduling.CronInvocation;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.TestRegistry;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class DeclarativeSchedulingTest {
    private final ScheduledTask task;

    DeclarativeSchedulingTest(ServiceRegistry registry) {
        registry.all(Lookup.builder()
                             .runLevel(70D) // scheduling run level
                             .addScope(Service.Singleton.TYPE)
                             .build());

        this.task = registry.get(ScheduledTask.class);
    }

    @Test
    void testCronNoParamSchedulingStarted() throws Exception {
        Boolean executed = task.cronNoParam.get(10, TimeUnit.SECONDS);

        assertThat("Cron should have been started", executed, is(true));
    }

    @Test
    void testCronParamSchedulingStarted() throws Exception {
        CronInvocation invocation = task.cronParam.get(10, TimeUnit.SECONDS);

        assertThat("Cron should have configured (overridden) pattern", invocation.cron(), is("0/1 * * * * ?"));
        assertThat("Cron should have configured concurrent set to false.", invocation.concurrent(), is(false));
    }

    @Test
    void testFixedNoParamSchedulingStarted() throws Exception {
        Boolean executed = task.fixedNoParam.get(10, TimeUnit.SECONDS);

        assertThat("Fixed rate should have been started", executed, is(true));
    }

    @Test
    void testFixedParamSchedulingStarted() throws Exception {
        FixedRateInvocation invocation = task.fixedParam.get(10, TimeUnit.SECONDS);

        assertThat("Cron should have configured (overridden) pattern", invocation.delayBy(), is(Duration.ZERO));
        assertThat("Cron should have configured concurrent set to false.", invocation.interval(), is(Duration.ofSeconds(1)));
    }

    @TestRegistry.AfterShutdown
    static void makeSureTaskIsStopped() throws InterruptedException {
        int counter = ScheduledTask.COUNTER.get();

        assertThat("At least one scheduled task should have been executed", counter, not(0));
        Thread.sleep(3000);

        assertThat("If the counter changed, the task is not stopped.", counter, is(counter));
    }
}
