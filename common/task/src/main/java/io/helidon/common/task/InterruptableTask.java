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

import java.util.concurrent.Callable;

/**
 * An interruptable task that can implements both {@link Runnable} and
 * {@link Callable}.
 *
 * @param <T> type of value returned by task
 */
public interface InterruptableTask<T> extends Runnable, Callable<T> {

    /**
     * Signals if a task can be interrupted at the time this method is called.
     *
     * @return outcome of interruptable test
     */
    boolean canInterrupt();

    @Override
    default void run() {
        try {
            call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default T call() throws Exception {
        run();
        return null;
    }
}
