/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

public class TidyTestExecutor extends ThreadPoolExecutor {

    private static final Logger LOGGER = Logger.getLogger(TidyTestExecutor.class.getName());

    private final ConcurrentLinkedQueue<Future<?>> futures = new ConcurrentLinkedQueue<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.READY);

    public TidyTestExecutor() {
        super(4,
                Integer.MAX_VALUE,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());
        super.prestartAllCoreThreads();
    }

    public boolean submitAwaitable(final Runnable task) {
        return state.get().submit(task, this);
    }

    private void submitInternal(final Runnable task) {
        Future<?> future = super.submit(task);
        futures.add(future);
    }

    public void clearAndInterruptTasks() {
        futures.forEach(future -> future.cancel(true));
        this.purge();
    }

    public void clearTasks() {
        futures.forEach(future -> future.cancel(false));
        this.purge();
    }

    public void awaitAllFinished() {
        if (state.getAndSet(State.WAITING) == State.READY) {
            try {
                while (!futures.isEmpty() && !futures.stream().allMatch(Future::isDone)) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        fail(e);
                    }
                }
                futures.clear();
            } finally {
                state.set(State.READY);
            }
        }
    }

    private enum State {
        READY {
            @Override
            boolean submit(final Runnable runnable, TidyTestExecutor tidyTestExecutor) {
                tidyTestExecutor.submitInternal(runnable);
                return true;
            }
        },
        WAITING {
            @Override
            boolean submit(final Runnable runnable, TidyTestExecutor tidyTestExecutor) {
                LOGGER.warning("Cannot submit, executor is waiting for cleanup!");
                return false;
            }
        };

        abstract boolean submit(Runnable runnable, TidyTestExecutor tidyTestExecutor);
    }
}
