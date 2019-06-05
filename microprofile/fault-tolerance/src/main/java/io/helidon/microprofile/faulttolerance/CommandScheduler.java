/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * Disables daemon threads.
     *
     * @param threadPoolSize Size of thread pool for async commands.
     * @return Existing scheduler or newly created one.
     */
    public static synchronized CommandScheduler create(int threadPoolSize) {
        if (instance == null) {
            instance = new CommandScheduler(ScheduledThreadPoolSupplier.builder()
                    .daemon(false)
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
     * Schedules a task using an executor. Returns a wrapped future with special logic
     * to handle cancellations of async bulkhead tasks that have been queued but have
     * not executed yet. Without forcing interruption of those tasks (see flag), they
     * would be allowed to start once the bulkhead is freed.
     *
     * @param callable The callable.
     * @param delay Delay before scheduling task.
     * @param unit Unite of delay.
     * @return Future to track task execution.
     */
    @Override
    public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
        ScheduledFuture<?> delegate = poolSupplier.get().schedule(callable, delay, unit);
        return new ScheduledFuture<Object>() {
            @Override
            public int hashCode() {
                return delegate.hashCode();
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Delayed)) {
                    return false;
                }
                return compareTo((Delayed) o) == 0;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return delegate.getDelay(unit);
            }

            @Override
            public int compareTo(Delayed o) {
                return delegate.compareTo(o);
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                CommandRetrier.CommandCallable<?> unwrapped = unwrapCallable(callable);
                if (unwrapped != null) {
                    FaultToleranceCommand command = unwrapped.getCommand();
                    BulkheadHelper bulkheadHelper = command.getBulkheadHelper();
                    if (bulkheadHelper != null && !bulkheadHelper.isInvocationRunning(command)) {
                        return delegate.cancel(true);      // overridden
                    }
                }
                return delegate.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return delegate.isCancelled();
            }

            @Override
            public boolean isDone() {
                return delegate.isDone();
            }

            @Override
            public Object get() throws InterruptedException, ExecutionException {
                return delegate.get();
            }

            @Override
            public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                    TimeoutException {
                return delegate.get(timeout, unit);
            }
        };
    }

    /**
     * Get access to underlying command from wrapped callable. First unwrap wrapper
     * created by Failsafe and then access our wrapper.
     *
     * @param callable Callable to be unwrapped
     * @return Unwrapped callable or {@code null} if cannot be unwrapped.
     */
    private static CommandRetrier.CommandCallable<?> unwrapCallable(Callable<?> callable) {
        Field[] fields = callable.getClass().getDeclaredFields();
        if (fields.length > 0) {
            try {
                fields[0].setAccessible(true);
                Callable<?> unwrapped = (Callable<?>) fields[0].get(callable);
                if (unwrapped instanceof CommandRetrier.CommandCallable<?>) {
                    return (CommandRetrier.CommandCallable<?>) unwrapped;
                }
            } catch (IllegalAccessException e) {
                // falls through
            }
        }
        return null;        // could not unwrap
    }
}
