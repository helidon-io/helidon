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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Class CommandScheduler.
 */
public class CommandScheduler implements Scheduler {

    private static CommandScheduler instance;

    private ScheduledThreadPoolExecutor executor;

    private CommandScheduler() {
        executor = ScheduledThreadPoolSupplier.create().get();
    }

    /**
     * Returns the single instance of this class.
     *
     * @return The instance.
     */
    public static CommandScheduler instance() {
        if (instance == null) {
            instance = new CommandScheduler();
        }
        return instance;
    }

    @Override
    public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
        return executor.schedule(callable, delay, unit);
    }
}
