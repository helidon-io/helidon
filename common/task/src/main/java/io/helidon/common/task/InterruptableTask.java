/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.task;

import java.util.concurrent.Callable;

import io.helidon.common.Api;

/**
 * An interruptable task that implements both {@link Runnable} and
 * {@link Callable}.
 * Implementations must override at least one of {@link #run()} or {@link #call()}.
 * The defaults adapt either execution method to the other. An implementation
 * that overrides neither method fails when executed.
 *
 * @param <T> type of value returned by task
 */
@Api.Internal
public interface InterruptableTask<T> extends Runnable, Callable<T> {

    /**
     * Signals if a task can be interrupted at the time this method is called.
     *
     * @return outcome of interruptable test
     */
    boolean canInterrupt();

    /**
     * Executes this task, discarding any result.
     * The default implementation delegates to {@link #call()} and wraps any
     * {@link Exception} in a {@link RuntimeException}.
     */
    @Override
    default void run() {
        try {
            call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes this task and returns its result.
     * The default implementation invokes the overridden {@link #run()} method
     * and returns {@code null}. Override detection is cached per implementation class.
     *
     * @return the task result, or {@code null} when adapting {@link #run()}
     * @throws UnsupportedOperationException if neither execution method is overridden
     * @throws Exception if task execution fails
     */
    @Override
    default T call() throws Exception {
        InterruptableTaskSupport.run(this);
        return null;
    }
}
