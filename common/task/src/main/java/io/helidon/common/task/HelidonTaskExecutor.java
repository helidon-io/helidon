/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.io.Closeable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A simplified {@link java.util.concurrent.ExecutorService} that can execute
 * {@link InterruptableTask}s and can be efficiently terminated. A thread that is
 * waiting to read on an open connection cannot be efficiently stopped. This
 * executor will query the thread and interrupt it if possible. It is important
 * to efficiently shut down the webserver in certain environments.
 */
public interface HelidonTaskExecutor extends Closeable {

    /**
     * Executes a task.
     *
     * @param task an interruptable task
     * @param <T> type ov value returned by task
     * @return a future for a value returned by the task
     */
    <T> Future<T> execute(InterruptableTask<T> task);

    /**
     * Verifies if the executor is terminated.
     *
     * @return outcome of test
     */
    boolean isTerminated();

    /**
     * Terminate executor waiting for any running task to complete for a specified
     * timeout period. It will only wait for those {@link InterruptableTask}s that
     * are not interruptable.
     *
     * @param timeout timeout period
     * @param unit timeout period unit
     * @return outcome of shutdown process
     */
    boolean terminate(long timeout, TimeUnit unit);

    /**
     * Force termination by forcefully interrupting all tasks. Shall only be called
     * if {@link #terminate} returns {@code false}.
     */
    void forceTerminate();
}
