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

package io.helidon.common.task;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterruptableTaskTest {
    private static final String MISSING_IMPLEMENTATION = "InterruptableTask must override run() or call()";

    @Test
    void runOnlyTaskSupportsBothEntryPoints() throws Exception {
        var task = new RunTask();

        task.run();
        assertThat(task.call(), nullValue());
        assertThat(task.call(), nullValue());

        assertThat(task.executions, is(3));
    }

    @Test
    void callOnlyTaskSupportsBothEntryPoints() throws Exception {
        var task = new CallTask();

        assertThat(task.call(), is(1));
        task.run();
        assertThat(task.call(), is(3));
    }

    @Test
    void inheritedRunSupportsCall() throws Exception {
        RunTask task = new RunTask() { };

        assertThat(task.call(), nullValue());
        assertThat(task.executions, is(1));
    }

    @Test
    void inheritedCallSupportsRun() {
        CallTask task = new CallTask() { };

        task.run();

        assertThat(task.executions, is(1));
    }

    @Test
    void subinterfaceRunDefaultSupportsCall() throws Exception {
        var task = new DefaultRunTask() { };

        assertThat(task.call(), nullValue());
    }

    @Test
    void overridingBothMethodsKeepsThemIndependent() throws Exception {
        var expected = new IllegalStateException("run failure");
        var task = new Task<String>() {
            @Override
            public void run() {
                throw expected;
            }

            @Override
            public String call() {
                return "call result";
            }
        };

        assertThat(task.call(), is("call result"));
        assertThat(assertThrows(IllegalStateException.class, task::run), sameInstance(expected));
    }

    @Test
    void incompleteTaskCallFailsPredictably() {
        var task = new Task<Void>() { };

        var exception = assertThrows(UnsupportedOperationException.class, task::call);

        assertThat(exception.getMessage(), is(MISSING_IMPLEMENTATION));
    }

    @Test
    void incompleteTaskRunWrapsPredictableFailure() {
        var task = new Task<Void>() { };

        var exception = assertThrows(RuntimeException.class, task::run);

        assertThat(exception.getCause(), instanceOf(UnsupportedOperationException.class));
        assertThat(exception.getCause().getMessage(), is(MISSING_IMPLEMENTATION));
    }

    @Test
    void runExceptionPropagatesThroughCall() {
        var expected = new IllegalStateException("run failure");
        var task = new Task<Void>() {
            @Override
            public void run() {
                throw expected;
            }
        };

        assertThat(assertThrows(IllegalStateException.class, task::call), sameInstance(expected));
    }

    @Test
    void callExceptionIsWrappedByRun() {
        var expected = new Exception("call failure");
        var task = new Task<Void>() {
            @Override
            public Void call() throws Exception {
                throw expected;
            }
        };

        var exception = assertThrows(RuntimeException.class, task::run);

        assertThat(exception.getCause(), sameInstance(expected));
    }

    @Test
    void callErrorPropagatesThroughRun() {
        var expected = new AssertionError("call failure");
        var task = new Task<Void>() {
            @Override
            public Void call() {
                throw expected;
            }
        };

        assertThat(assertThrows(AssertionError.class, task::run), sameInstance(expected));
    }

    private interface DefaultRunTask extends InterruptableTask<Void> {
        @Override
        default boolean canInterrupt() {
            return true;
        }

        @Override
        default void run() {
        }
    }

    private abstract static class Task<T> implements InterruptableTask<T> {
        @Override
        public boolean canInterrupt() {
            return true;
        }
    }

    private static class RunTask extends Task<Void> {
        private int executions;

        @Override
        public void run() {
            executions++;
        }
    }

    private static class CallTask extends Task<Integer> {
        private int executions;

        @Override
        public Integer call() {
            return ++executions;
        }
    }
}
