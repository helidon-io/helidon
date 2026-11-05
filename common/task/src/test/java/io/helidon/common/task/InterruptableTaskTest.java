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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void missingOptionalSignatureDoesNotPreventRunAdaptation() throws Exception {
        var executions = new AtomicInteger();
        var task = optionalSignatureTask(executions::incrementAndGet);

        task.run();
        assertThat(task.call(), nullValue());
        assertThat(task.call(), nullValue());

        assertThat(executions.get(), is(3));
    }

    @Test
    void missingOptionalSignatureDoesNotPreventIncompleteCallFailure() throws Exception {
        var task = (InterruptableTask<?>) taskClassWithoutOptionalType(OptionalSignatureIncompleteTask.class)
                .getConstructor().newInstance();

        assertThat(assertThrows(UnsupportedOperationException.class, task::call).getMessage(), is(MISSING_IMPLEMENTATION));
        assertThat(assertThrows(UnsupportedOperationException.class, task::call).getMessage(), is(MISSING_IMPLEMENTATION));
    }

    @Test
    void missingOptionalSignatureDoesNotPreventIncompleteRunFailure() throws Exception {
        var task = (InterruptableTask<?>) taskClassWithoutOptionalType(OptionalSignatureIncompleteTask.class)
                .getConstructor().newInstance();

        var exception = assertThrows(RuntimeException.class, task::run);

        assertThat(exception.getCause(), instanceOf(UnsupportedOperationException.class));
        assertThat(exception.getCause().getMessage(), is(MISSING_IMPLEMENTATION));
    }

    @Test
    void missingOptionalSignaturePreservesTaskFailuresAndSubsequentExecution() throws Exception {
        var executions = new AtomicInteger();
        var expected = new IllegalStateException("run failure");
        var task = optionalSignatureTask(() -> {
            if (executions.incrementAndGet() == 1) {
                throw expected;
            }
        });

        assertThat(assertThrows(IllegalStateException.class, task::call), sameInstance(expected));
        assertThat(task.call(), nullValue());
        assertThat(executions.get(), is(2));
    }

    @Test
    void missingOptionalSignaturePreservesTaskErrors() throws Exception {
        var expected = new NoClassDefFoundError("task failure");
        var task = optionalSignatureTask(() -> {
            throw expected;
        });

        assertThat(assertThrows(NoClassDefFoundError.class, task::call), sameInstance(expected));
        assertThat(assertThrows(NoClassDefFoundError.class, task::call), sameInstance(expected));
    }

    @Test
    void missingOptionalSignatureSupportsNestedTasksOfTheSameClass() throws Exception {
        var taskClass = taskClassWithoutOptionalType(OptionalSignatureTask.class);
        var constructor = taskClass.getConstructor(Runnable.class);
        var executions = new AtomicInteger();
        Runnable action = executions::incrementAndGet;
        var inner = (InterruptableTask<?>) constructor.newInstance(action);
        Runnable nested = () -> {
            try {
                inner.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        var outer = (InterruptableTask<?>) constructor.newInstance(nested);

        assertThat(outer.call(), nullValue());
        assertThat(outer.call(), nullValue());
        assertThat(executions.get(), is(2));
    }

    @Test
    void missingOptionalSignatureSupportsConcurrentExecutionOfTheSameTask() throws Exception {
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var task = optionalSignatureTask(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(task::call);
            var second = executor.submit(task::call);
            try {
                assertThat("Both invocations enter run()", started.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS), nullValue());
            assertThat(second.get(10, TimeUnit.SECONDS), nullValue());
        }
    }

    private static InterruptableTask<?> optionalSignatureTask(Runnable action) throws Exception {
        return (InterruptableTask<?>) taskClassWithoutOptionalType(OptionalSignatureTask.class)
                .getConstructor(Runnable.class).newInstance(action);
    }

    private static Class<?> taskClassWithoutOptionalType(Class<?> taskClass) throws IOException {
        var loader = new ClassLoader(InterruptableTaskTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(OptionalSignatureType.class.getName())) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }

            Class<?> loadTask() throws IOException {
                var resource = "/" + taskClass.getName().replace('.', '/') + ".class";
                try (var input = taskClass.getResourceAsStream(resource)) {
                    if (input == null) {
                        throw new IllegalStateException("Missing task fixture: " + resource);
                    }
                    byte[] bytes = input.readAllBytes();
                    return defineClass(taskClass.getName(), bytes, 0, bytes.length);
                }
            }
        };
        return loader.loadTask();
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

    public static class OptionalSignatureTask implements InterruptableTask<Void> {
        private final Runnable action;

        public OptionalSignatureTask(Runnable action) {
            this.action = action;
        }

        @Override
        public boolean canInterrupt() {
            return true;
        }

        @Override
        public void run() {
            action.run();
        }

        public OptionalSignatureType unused() {
            return null;
        }
    }

    public static class OptionalSignatureIncompleteTask implements InterruptableTask<Void> {
        @Override
        public boolean canInterrupt() {
            return true;
        }

        public OptionalSignatureType unused() {
            return null;
        }
    }

    public static class OptionalSignatureType {
    }
}
