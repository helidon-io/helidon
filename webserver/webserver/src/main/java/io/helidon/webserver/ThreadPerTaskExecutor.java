/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.common.task.HelidonTaskExecutor;
import io.helidon.common.task.InterruptableTask;

/**
 * An implementation of {@link HelidonTaskExecutor}. Implementation is a simplified
 * version of ThreadPerTaskExecutor in the JDK library. Upon termination, this
 * executor shall interrupt all tasks whose {@link InterruptableTask#canInterrupt()}
 * method return {@code true} in an attempt to stop as fast as possible.
 */
class ThreadPerTaskExecutor implements HelidonTaskExecutor {

    private final ThreadFactory factory;
    private final Map<Thread, Object> threadTasks = new ConcurrentHashMap<>();
    private final CountDownLatch terminationSignal = new CountDownLatch(1);
    private final ClassLoader contextClassLoader;

    // states: RUNNING -> SHUTDOWN -> TERMINATED
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int TERMINATED = 2;
    private final AtomicInteger state = new AtomicInteger();

    private ThreadPerTaskExecutor(ThreadFactory factory) {
        this.factory = Objects.requireNonNull(factory);
        this.contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    static HelidonTaskExecutor create(ThreadFactory factory) {
        return new ThreadPerTaskExecutor(factory);
    }

    @Override
    public <T> Future<T> execute(InterruptableTask<T> task) {
        return submit(task);
    }

    @Override
    public boolean isTerminated() {
        return state.get() >= TERMINATED;
    }

    @Override
    public boolean terminate(long timeout, TimeUnit unit) {
        if (isTerminated()) {
            return true;
        }
        if (state.compareAndSet(RUNNING, SHUTDOWN)) {
            // attempt to stop interruptable tasks first
            Set<Thread> interrupted = tryStopInterruptableTasks();
            interrupted.forEach(threadTasks::remove);
            if (threadTasks.isEmpty()) {
                return state.compareAndSet(SHUTDOWN, TERMINATED);
            }
            try {
                boolean result = terminationSignal.await(timeout, unit);
                return result && state.compareAndSet(SHUTDOWN, TERMINATED);
            } catch (InterruptedException e) {
                // falls through
            }
        }
        return false;
    }

    @Override
    public void forceTerminate() {
        if (!isTerminated()) {
            if (state.get() == RUNNING) {
                throw new IllegalArgumentException("Must call terminate(long, TimeUnit) first"
                        + " to attempt graceful termination");
            }
            if (state.compareAndSet(SHUTDOWN, TERMINATED)) {
                // force interruption of all tasks
                threadTasks.keySet().forEach(Thread::interrupt);
            }
        }
    }

    /**
     * Sends interrupt signal to all tasks that can be interrupted at this time
     * and returns that set.
     */
    private Set<Thread> tryStopInterruptableTasks() {
        return threadTasks.entrySet().stream()
                .filter(entry -> entry.getKey().isAlive() && entry.getKey().getState() == Thread.State.WAITING)
                .filter(entry -> {
                    // send interrupt signal to thread if possible
                    if (entry.getValue() instanceof InterruptableTask<?> task && task.canInterrupt()) {
                        entry.getKey().interrupt();
                        return true;
                    }
                    return false;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public void close() {
        terminate(0, TimeUnit.SECONDS);
        forceTerminate();
    }

    /**
     * Creates a thread to run the given task.
     */
    private Thread newThread(Runnable task) {
        Thread thread = factory.newThread(task);
        if (thread == null) {
            throw new RejectedExecutionException();
        }
        if (contextClassLoader != null) {
            thread.setContextClassLoader(contextClassLoader);
        }
        return thread;
    }

    /**
     * Notify the executor that the task executed by the given thread is complete.
     * If the executor has been shutdown then this method will count down the
     * termination signal.
     */
    private void taskComplete(Thread thread) {
        threadTasks.remove(thread);
        if (state.get() == SHUTDOWN && threadTasks.isEmpty()) {
            terminationSignal.countDown();
        }
    }

    /**
     * Adds a thread to the set of threads and starts it.
     *
     * @throws RejectedExecutionException if task not started
     */
    @SuppressWarnings("checkstyle:IllegalToken") // assert well placed
    private void start(Thread thread, Object task) {
        assert thread.getState() == Thread.State.NEW;

        // keeps track of thread/task association
        threadTasks.put(thread, task);

        boolean started = false;
        try {
            if (state.get() == RUNNING) {
                thread.start();
                started = true;
            }
        } finally {
            if (!started) {
                taskComplete(thread);
            }
        }

        // throw REE if thread not started and no exception thrown
        if (!started) {
            throw new RejectedExecutionException();
        }
    }

    private <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task);
        ensureNotShutdown();
        var future = new ThreadPerTaskExecutor.ThreadBoundFuture<>(this, task);
        Thread thread = future.thread();
        start(thread, task);
        return future;
    }

    /**
     * Throws RejectedExecutionException if the executor has been shutdown.
     */
    private void ensureNotShutdown() {
        if (state.get() >= SHUTDOWN) {
            // shutdown or terminated
            throw new RejectedExecutionException();
        }
    }

    /**
     * A Future for a task that runs in its own thread. The thread is
     * created (but not started) when the Future is created. The thread
     * is interrupted when the future is cancelled. The executor is
     * notified when the task completes.
     */
    private static class ThreadBoundFuture<T> extends CompletableFuture<T> implements Runnable {

        private final ThreadPerTaskExecutor executor;
        private final Callable<T> task;
        private final Thread thread;

        ThreadBoundFuture(ThreadPerTaskExecutor executor, Callable<T> task) {
            this.executor = executor;
            this.task = task;
            this.thread = executor.newThread(this);
        }

        Thread thread() {
            return thread;
        }

        @Override
        public void run() {
            if (Thread.currentThread() != thread) {
                // should not happen except where something casts this object
                // to a Runnable and invokes the run method.
                throw new WrongThreadException();
            }
            try {
                T result = task.call();
                complete(result);
            } catch (Throwable e) {
                completeExceptionally(e);
            } finally {
                executor.taskComplete(thread);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && mayInterruptIfRunning) {
                thread.interrupt();
            }
            return cancelled;
        }
    }
}

