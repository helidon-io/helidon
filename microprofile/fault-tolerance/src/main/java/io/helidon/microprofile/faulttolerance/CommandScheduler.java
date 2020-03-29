/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Class CommandScheduler.
 */
public class CommandScheduler implements Scheduler {

    private static final String THREAD_NAME_PREFIX = "helidon-ft-async-";

    private static CommandScheduler instance;

    private final ScheduledThreadPoolSupplier poolSupplier;

    private CommandScheduler(ScheduledThreadPoolSupplier poolSupplier) {
        this.poolSupplier = poolSupplier;
    }

    /**
     * If no command scheduler exists, creates one using default values.
     * The created command scheduler uses daemon threads, so the JVM shuts-down if these are the only ones running.
     *
     * @param threadPoolSize Size of thread pool for async commands.
     * @return Existing scheduler or newly created one.
     */
    public static synchronized CommandScheduler create(int threadPoolSize) {
        if (instance == null) {
            instance = new CommandScheduler(ScheduledThreadPoolSupplier.builder()
                    .daemon(true)
                    .threadNamePrefix(THREAD_NAME_PREFIX)
                    .corePoolSize(threadPoolSize)
                    .prestart(false)
                    .build());
        }
        return instance;
    }

    /**
     * Returns underlying pool supplier.
     *
     * @return The pool supplier.
     */
    ScheduledThreadPoolSupplier poolSupplier() {
        return poolSupplier;
    }

    /**
     * Schedules a task using an executor.
     *
     * @param callable The callable.
     * @param delay Delay before scheduling task.
     * @param unit Unite of delay.
     * @return Future to track task execution.
     */
    @Override
    public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
        return poolSupplier.get().schedule(callable, delay, unit);
    }
}
