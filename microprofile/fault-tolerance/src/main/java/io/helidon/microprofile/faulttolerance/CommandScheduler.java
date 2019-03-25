/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import io.helidon.config.Config;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Class CommandScheduler.
 */
public class CommandScheduler implements Scheduler {

    private final static String THREAD_NAME_PREFIX = "helidon-ft-async-";

    private static CommandScheduler instance;

    private final ScheduledThreadPoolSupplier poolSupplier;

    private CommandScheduler(ScheduledThreadPoolSupplier poolSupplier) {
        this.poolSupplier = poolSupplier;
    }

    /**
     * If no command scheduler exists, creates one using a config. Disables
     * daemon threads.
     *
     * @param config Config to use.
     * @return Existing scheduler or newly created one.
     */
    public static synchronized CommandScheduler create(Config config) {
        if (instance == null) {
            instance = new CommandScheduler(ScheduledThreadPoolSupplier.builder()
                    .daemon(false)
                    .threadNamePrefix(THREAD_NAME_PREFIX)
                    .config(config).build());
        }
        return instance;
    }

    /**
     * If no command scheduler exists, creates one using default values.
     * Disables daemon threads.
     *
     * @return Existing scheduler or newly created one.
     */
    public static synchronized CommandScheduler create() {
        if (instance == null) {
            instance = new CommandScheduler(ScheduledThreadPoolSupplier.builder()
                    .daemon(false)
                    .threadNamePrefix(THREAD_NAME_PREFIX)
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
     * Schedules a task using executor.
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

    /**
     * Returns underlying instance (for testing purposes).
     *
     * @return The instance.
     */
    static synchronized CommandScheduler instance() {
        if (instance == null) {
            create();
        }
        return instance;
    }
}
