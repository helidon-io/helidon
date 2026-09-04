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

package io.helidon.quic;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequentialSchedulerTest {
    @Test
    void rejectsNullTasksAndExecutorsWithoutStartingScheduler() {
        assertThrows(NullPointerException.class, () -> SequentialScheduler.lockingScheduler(null));
        assertThrows(NullPointerException.class, () -> SequentialScheduler.LockingRestartableTask.create(null));

        AtomicInteger runs = new AtomicInteger();
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(runs::incrementAndGet);
        assertThrows(NullPointerException.class,
                     () -> scheduler.runOrSchedule((Executor) null));

        assertThat(runs.get(), is(0));
        scheduler.runOrSchedule();
        assertThat(runs.get(), is(1));
    }

    @Test
    void executorRejectionRollsBackSchedulerState() {
        AtomicInteger runs = new AtomicInteger();
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(runs::incrementAndGet);
        RejectedExecutionException rejection = new RejectedExecutionException("test rejection");

        RejectedExecutionException thrown = assertThrows(
                RejectedExecutionException.class,
                () -> scheduler.runOrSchedule(command -> {
                    throw rejection;
                }));
        scheduler.runOrSchedule();

        assertThat(thrown, sameInstance(rejection));
        assertThat(runs.get(), is(1));
    }

    @Test
    void executorRejectionReplaysConcurrentSignal() {
        AtomicInteger runs = new AtomicInteger();
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(runs::incrementAndGet);
        RejectedExecutionException rejection = new RejectedExecutionException("test rejection");

        RejectedExecutionException thrown = assertThrows(
                RejectedExecutionException.class,
                () -> scheduler.runOrSchedule(command -> {
                    scheduler.runOrSchedule();
                    throw rejection;
                }));

        assertThat(thrown, sameInstance(rejection));
        assertThat(runs.get(), is(1));
    }
}
