/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package io.helidon.nima.webserver.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A hacked version of ThreadPerTaskExecutor that is InterruptableTask aware
 * and on shutdown will appropriately interrupt idle tasks.
 */
public class ThreadPerTaskExecutor  implements ExecutorService {

    private final ThreadFactory factory;
    private final Map<Thread, Object> threadTasks = new ConcurrentHashMap<>(); // <!-- HERE
    private final CountDownLatch terminationSignal = new CountDownLatch(1);

    // states: RUNNING -> SHUTDOWN -> TERMINATED
    private static final int RUNNING    = 0;
    private static final int SHUTDOWN   = 1;
    private static final int TERMINATED = 2;
    private final AtomicInteger state = new AtomicInteger();

    private ThreadPerTaskExecutor(ThreadFactory factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    public static ExecutorService create(ThreadFactory factory) {
        return new ThreadPerTaskExecutor(factory);
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
     * Attempts to terminate if already shutdown. If this method terminates the
     * executor then it signals any threads that are waiting for termination.
     */
    private void tryTerminate() {
        assert state.get() >= SHUTDOWN;
        if (threadTasks.isEmpty() && state.compareAndSet(SHUTDOWN, TERMINATED)) {
            // signal waiters
            terminationSignal.countDown();
        }
    }

    /**
     * Attempts to shutdown and terminate the executor.
     * If interruptThreads is true then all running threads are interrupted.
     */
    private void tryShutdownAndTerminate(boolean interruptThreads) {
        if (state.compareAndSet(RUNNING, SHUTDOWN)) {
            tryStopInterruptableTask(); // <!-- HERE
            tryTerminate();
        }
        if (interruptThreads) {
            threadTasks.keySet().forEach(Thread::interrupt);
        }
    }


    // HERE --!>
    private void tryStopInterruptableTask() {
        threadTasks.entrySet().stream()
                .filter(entry -> entry.getKey().isAlive()) // Thread isAlive
                .filter(entry -> entry.getKey().getState() == Thread.State.WAITING)// Thread WAITING state
                .forEach(entry -> {
                    if (entry.getValue() instanceof InterruptableTask task) {
                        if (task.canInterrupt()) {
                            entry.getKey().interrupt();
                        }
                    }
                });
    }
    // <!-- HERE


    @Override
    public void shutdown() {
        if (!isShutdown())
            tryShutdownAndTerminate(false);
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (!isTerminated())
            tryShutdownAndTerminate(true);
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return state.get() >= SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state.get() >= TERMINATED;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit);
        if (isTerminated()) {
            return true;
        } else {
            return terminationSignal.await(timeout, unit);
        }
    }

    /**
     * Waits for executor to terminate.
     */
    private void awaitTermination() {
        boolean terminated = isTerminated();
        if (!terminated) {
            tryShutdownAndTerminate(false);
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        tryShutdownAndTerminate(true);
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        awaitTermination();
    }

    /**
     * Creates a thread to run the given task.
     */
    private Thread newThread(Runnable task) {
        Thread thread = factory.newThread(task);
        if (thread == null)
            throw new RejectedExecutionException();
        return thread;
    }

    /**
     * Notify the executor that the task executed by the given thread is complete.
     * If the executor has been shutdown then this method will attempt to terminate
     * the executor.
     */
    private void taskComplete(Thread thread) {
        var removed = threadTasks.remove(thread);
        assert removed != null;
        if (state.get() == SHUTDOWN) {
            tryTerminate();
        }
    }

    /**
     * Adds a thread to the set of threads and starts it.
     * @throws RejectedExecutionException
     */
    private void start(Thread thread, Object task) {
        assert thread.getState() == Thread.State.NEW;
        threadTasks.put(thread, task); // <!-- HERE, keep the associated task

        boolean started = false;
        try {
            if (state.get() == RUNNING) {
                thread.start();
                //JLA.start(thread, this);
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

    /**
     * Starts a thread to execute the given task.
     * @throws RejectedExecutionException
     */
    private Thread start(Runnable task) {
        Objects.requireNonNull(task);
        ensureNotShutdown();
        Thread thread = newThread(new ThreadPerTaskExecutor.TaskRunner(this, task));
        start(thread, task);
        return thread;
    }

    @Override
    public void execute(Runnable task) {
        start(task);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task);
        ensureNotShutdown();
        var future = new ThreadPerTaskExecutor.ThreadBoundFuture<>(this, task);
        Thread thread = future.thread();
        start(thread, task);
        return future;
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(Executors.callable(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return submit(Executors.callable(task, result));
    }

    /**
     * Runs a task and notifies the executor when it completes.
     */
    private static class TaskRunner implements Runnable {
        final ThreadPerTaskExecutor executor;
        final Runnable task;
        TaskRunner(ThreadPerTaskExecutor executor, Runnable task) {
            this.executor = executor;
            this.task = task;
        }
        @Override
        public void run() {
            try {
                task.run();
            } finally {
                executor.taskComplete(Thread.currentThread());
            }
        }
    }

    /**
     * A Future for a task that runs in its own thread. The thread is
     * created (but not started) when the Future is created. The thread
     * is interrupted when the future is cancelled. The executor is
     * notified when the task completes.
     */
    private static class ThreadBoundFuture<T>
            extends CompletableFuture<T> implements Runnable {

        final ThreadPerTaskExecutor executor;
        final Callable<T> task;
        final Thread thread;

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
            if (cancelled && mayInterruptIfRunning)
                thread.interrupt();
            return cancelled;
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {

        Objects.requireNonNull(tasks);
        List<Future<T>> futures = new ArrayList<>();
        int j = 0;
        try {
            for (Callable<T> t : tasks) {
                Future<T> f = submit(t);
                futures.add(f);
            }
            for (int size = futures.size(); j < size; j++) {
                Future<T> f = futures.get(j);
                if (!f.isDone()) {
                    try {
                        f.get();
                    } catch (ExecutionException | CancellationException ignore) { }
                }
            }
            return futures;
        } finally {
            cancelAll(futures, j);
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
            throws InterruptedException {

        Objects.requireNonNull(tasks);
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<>();
        int j = 0;
        try {
            for (Callable<T> t : tasks) {
                Future<T> f = submit(t);
                futures.add(f);
            }
            for (int size = futures.size(); j < size; j++) {
                Future<T> f = futures.get(j);
                if (!f.isDone()) {
                    try {
                        f.get(deadline - System.nanoTime(), NANOSECONDS);
                    } catch (TimeoutException e) {
                        break;
                    } catch (ExecutionException | CancellationException ignore) { }
                }
            }
            return futures;
        } finally {
            cancelAll(futures, j);
        }
    }

    private <T> void cancelAll(List<Future<T>> futures, int j) {
        for (int size = futures.size(); j < size; j++)
            futures.get(j).cancel(true);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        try {
            return invokeAny(tasks, false, 0, null);
        } catch (TimeoutException e) {
            // should not happen
            throw new InternalError(e);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(unit);
        return invokeAny(tasks, true, timeout, unit);
    }

    private <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                            boolean timed,
                            long timeout,
                            TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {

        int size = tasks.size();
        if (size == 0) {
            throw new IllegalArgumentException("'tasks' is empty");
        }

        var holder = new ThreadPerTaskExecutor.AnyResultHolder<T>(Thread.currentThread());
        var threadList = new ArrayList<Thread>(size);
        long nanos = (timed) ? unit.toNanos(timeout) : 0;
        long startNanos = (timed) ? System.nanoTime() : 0;

        try {
            int count = 0;
            Iterator<? extends Callable<T>> iterator = tasks.iterator();
            while (count < size && iterator.hasNext()) {
                Callable<T> task = iterator.next();
                Objects.requireNonNull(task);
                Thread thread = start(() -> {
                    try {
                        T r = task.call();
                        holder.complete(r);
                    } catch (Throwable e) {
                        holder.completeExceptionally(e);
                    }
                });
                threadList.add(thread);
                count++;
            }
            if (count == 0) {
                throw new IllegalArgumentException("'tasks' is empty");
            }

            if (Thread.interrupted())
                throw new InterruptedException();
            T result = holder.result();
            while (result == null && holder.exceptionCount() < count) {
                if (timed) {
                    long remainingNanos = nanos - (System.nanoTime() - startNanos);
                    if (remainingNanos <= 0)
                        throw new TimeoutException();
                    LockSupport.parkNanos(remainingNanos);
                } else {
                    LockSupport.park();
                }
                if (Thread.interrupted())
                    throw new InterruptedException();
                result = holder.result();
            }

            if (result != null) {
                return (result != ThreadPerTaskExecutor.AnyResultHolder.NULL) ? result : null;
            } else {
                throw new ExecutionException(holder.firstException());
            }

        } finally {
            // interrupt any threads that are still running
            for (Thread t : threadList) {
                if (t.isAlive()) {
                    t.interrupt();
                }
            }
        }
    }

    /**
     * An object for use by invokeAny to hold the result of the first task
     * to complete normally and/or the first exception thrown. The object
     * also maintains a count of the number of tasks that attempted to
     * complete up to when the first tasks completes normally.
     */
    private static class AnyResultHolder<T> {
        private static final VarHandle RESULT;
        private static final VarHandle EXCEPTION;
        private static final VarHandle EXCEPTION_COUNT;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                RESULT = l.findVarHandle(ThreadPerTaskExecutor.AnyResultHolder.class, "result", Object.class);
                EXCEPTION = l.findVarHandle(ThreadPerTaskExecutor.AnyResultHolder.class, "exception", Throwable.class);
                EXCEPTION_COUNT = l.findVarHandle(ThreadPerTaskExecutor.AnyResultHolder.class, "exceptionCount", int.class);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
        private static final Object NULL = new Object();

        private final Thread owner;
        private volatile T result;
        private volatile Throwable exception;
        private volatile int exceptionCount;

        AnyResultHolder(Thread owner) {
            this.owner = owner;
        }

        /**
         * Complete with the given result if not already completed. The winner
         * unparks the owner thread.
         */
        void complete(T value) {
            @SuppressWarnings("unchecked")
            T v = (value != null) ? value : (T) NULL;
            if (result == null && RESULT.compareAndSet(this, null, v)) {
                LockSupport.unpark(owner);
            }
        }

        /**
         * Complete with the given exception. If the result is not already
         * set then it unparks the owner thread.
         */
        void completeExceptionally(Throwable exc) {
            if (result == null) {
                if (exception == null)
                    EXCEPTION.compareAndSet(this, null, exc);
                EXCEPTION_COUNT.getAndAdd(this, 1);
                LockSupport.unpark(owner);
            }
        }

        /**
         * Returns non-null if a task completed successfully. The result is
         * NULL if completed with null.
         */
        T result() {
            return result;
        }

        /**
         * Returns the first exception thrown if recorded by this object.
         *
         * @apiNote The result() method should be used to test if there is
         * a result before invoking the exception method.
         */
        Throwable firstException() {
            return exception;
        }

        /**
         * Returns the number of tasks that terminated with an exception before
         * a task completed normally.
         */
        int exceptionCount() {
            return exceptionCount;
        }
    }
}

