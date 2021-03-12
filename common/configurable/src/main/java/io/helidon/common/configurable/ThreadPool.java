/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.NotificationEmitter;

import io.helidon.common.context.ContextAwareExecutorService;

/**
 * A {@link ThreadPoolExecutor} with an extensible growth policy and queue state accessors.
 */
public class ThreadPool extends ThreadPoolExecutor {
    private static final Logger LOGGER = Logger.getLogger(ThreadPool.class.getName());
    private static final int MAX_GROWTH_RATE = 100;

    private final String name;
    private final WorkQueue queue;
    private final RejectionHandler rejectionHandler;
    private final AtomicInteger activeThreads;
    private final LongAdder totalActiveThreads;
    private final AtomicInteger completedTasks;
    private final AtomicInteger failedTasks;
    private final int growthThreshold;
    private final int growthRate;

    /**
     * Returns the given executor as a {@link ThreadPool} if possible.
     *
     * @param executor The executor.
     * @return The thread pool or empty if not a {@link ThreadPool}.
     */
    public static Optional<ThreadPool> asThreadPool(ExecutorService executor) {
        if (executor instanceof ThreadPool) {
            return Optional.of((ThreadPool) executor);
        } else if (executor instanceof ContextAwareExecutorService) {
            return asThreadPool(((ContextAwareExecutorService) executor).unwrap());
        }
        return Optional.empty();
    }

    /**
     * Returns a new {@code ThreadPool}.
     *
     * @param name The pool name.
     * @param corePoolSize The number of threads to keep in the pool, even if they are idle, unless
     * {@code allowCoreThreadTimeOut} is set
     * @param maxPoolSize The maximum number of threads to allow in the pool
     * @param growthThreshold The queue size above which pool growth should be considered if the pool is not fixed size.
     * @param growthRate The percentage of task submissions that should result in adding threads, expressed as a value
     * from 0 to 100. For non-zero values the rate is applied when all of the following are true:
     * <ul>
     * <li>the pool size is below the maximum, and</li>
     * <li>there are no idle threads, and</li>
     * <li>the number of tasks in the queue exceeds the {@code growthThreshold}</li>
     * </ul>
     * <p></p>For example, a rate of 20 means that while these conditions are met one thread will be added for every 5 submitted tasks.
     * <p>A rate of 0 selects the default {@link ThreadPoolExecutor} growth behavior: a thread is added only when a submitted
     * task is rejected because the queue is full.
     * @param keepAliveTime When the number of threads is greater than the core, this is the maximum time that excess idle
     * threads will wait for new tasks before terminating.
     * @param keepAliveTimeUnits The units for {@code keepAliveTime}.
     * @param workQueueCapacity The capacity of the work queue.
     * @param threadNamePrefix The name prefix to use when a new thread is created.
     * @param useDaemonThreads {@code true} if created threads should be set as daemon.
     * @param rejectionHandler The rejection policy.
     * @throws IllegalArgumentException if any of the following holds:<br>
     * {@code name is null or empty}<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveMinutes < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}<br>
     * {@code growthThreshold < 0}<br>
     * {@code growthRate < 0} <br>
     * {@code growthRate > 100} <br>
     * {@code keepAliveMinutes < 1}
     * {@code workQueueCapacity < 1} <br>
     * {@code threadNamePrefix is null or empty}
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    static ThreadPool create(String name,
                             int corePoolSize,
                             int maxPoolSize,
                             int growthThreshold,
                             int growthRate,
                             long keepAliveTime,
                             TimeUnit keepAliveTimeUnits,
                             int workQueueCapacity,
                             String threadNamePrefix,
                             boolean useDaemonThreads,
                             RejectionHandler rejectionHandler) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is null or empty");
        } else if (corePoolSize < 0) {
            throw new IllegalArgumentException("corePoolSize < 0");
        } else if (maxPoolSize < 0) {
            throw new IllegalArgumentException("maxPoolSize < 0");
        } else if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize < corePoolSize");
        } else if (growthThreshold < 0) {
            throw new IllegalArgumentException("growthThreshold < 0");
        } else if (growthRate < 0) {
            throw new IllegalArgumentException("growthRate < 0");
        } else if (growthRate > MAX_GROWTH_RATE) {
            throw new IllegalArgumentException("growthRate > 100");
        } else if (keepAliveTime < 1) {
            throw new IllegalArgumentException("keepAliveTime < 1");
        } else if (workQueueCapacity < 1) {
            throw new IllegalArgumentException("workQueueCapacity < 1");
        } else if (threadNamePrefix == null || threadNamePrefix.isEmpty()) {
            throw new IllegalArgumentException("threadNamePrefix is null or empty");
        } else if (rejectionHandler == null) {
            throw new IllegalArgumentException("rejectionPolicy is null");
        }

        final WorkQueue queue = createQueue(workQueueCapacity, corePoolSize, maxPoolSize, growthThreshold, growthRate);
        final ThreadFactory threadFactory = new GroupedThreadFactory(name, threadNamePrefix, useDaemonThreads);
        return new ThreadPool(name, corePoolSize, maxPoolSize, growthThreshold, growthRate,
                              keepAliveTime, keepAliveTimeUnits, threadFactory, queue, rejectionHandler);
    }

    private ThreadPool(String name,
                       int corePoolSize,
                       int maximumPoolSize,
                       int growthThreshold,
                       int growthRate,
                       long keepAliveTime,
                       TimeUnit keepAliveTimeUnit,
                       ThreadFactory threadFactory,
                       WorkQueue queue,
                       RejectionHandler rejectionHandler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveTimeUnit, queue, threadFactory, rejectionHandler);
        this.name = name;
        this.queue = queue;
        this.growthThreshold = growthThreshold;
        this.activeThreads = new AtomicInteger();
        this.totalActiveThreads = new LongAdder();
        this.completedTasks = new AtomicInteger();
        this.failedTasks = new AtomicInteger();
        this.growthRate = growthRate;
        this.rejectionHandler = rejectionHandler;
        queue.setPool(this);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(toString());
        }
    }

    /**
     * Returns the name of this pool.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the queue capacity.
     *
     * @return The capacity.
     */
    public int getQueueCapacity() {
        return queue.getCapacity();
    }

    /**
     * Returns the growth threshold.
     *
     * @return The threshold.
     */
    int getGrowthThreshold() {
        return growthThreshold;
    }

    /**
     * Returns the growth rate.
     *
     * @return The rate.
     */
    int getGrowthRate() {
        return growthRate;
    }

    /**
     * Returns the average queue size.
     *
     * @return The size.
     */
    public float getAverageQueueSize() {
        return queue.getAverageSize();
    }

    /**
     * Returns the peak queue size.
     *
     * @return The size.
     */
    public int getPeakQueueSize() {
        return queue.getPeakSize();
    }

    /**
     * Returns the number of completed tasks.
     *
     * @return The count.
     */
    public int getCompletedTasks() {
        return completedTasks.get();
    }

    /**
     * Returns the number of tasks that threw an exception.
     *
     * @return The count.
     */
    public int getFailedTasks() {
        return failedTasks.get();
    }

    /**
     * Returns the number of completed and failed tasks.
     *
     * @return The count.
     */
    public int getTotalTasks() {
        return completedTasks.get() + failedTasks.get();
    }

    /**
     * Returns the current number of active threads.
     *
     * @return The count.
     */
    public int getActiveThreads() {
        return activeThreads.get();
    }

    /**
     * Returns the average number of active threads across the life of the pool.
     *
     * @return The average.
     */
    public float getAverageActiveThreads() {
        final float totalActive = totalActiveThreads.sum();
        if (totalActive == 0) {
            return 0.0f;
        } else {
            return totalActive / (float) getTotalTasks();
        }
    }

    /**
     * Returns the rejection count.
     *
     * @return The count.
     */
    public int getRejectionCount() {
        return rejectionHandler.getRejectionCount();
    }

    /**
     * Tests whether or not the number of threads can change over time.
     *
     * @return {@code true} if maximum size is equal to core size.
     */
    public boolean isFixedSize() {
        return getMaximumPoolSize() == getCorePoolSize();
    }

    @Override
    public WorkQueue getQueue() {
        return queue;
    }

    /**
     * Returns the current number of tasks in the queue.
     *
     * @return The count.
     */
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler instanceof RejectionHandler) {
            super.setRejectedExecutionHandler(handler);
        } else {
            throw new IllegalArgumentException(handler.getClass() + " must be an instance of " + RejectionHandler.class);
        }
    }

    @Override
    public RejectionHandler getRejectedExecutionHandler() {
        return (RejectionHandler) super.getRejectedExecutionHandler();
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize != getMaximumPoolSize()) {
            LOGGER.warning("Maximum pool size cannot be changed in " + this);
        }
    }

    @Override
    public String toString() {
        final boolean fixedSize = isFixedSize();
        return "ThreadPool '" + getName() + "' {"
               + "corePoolSize=" + getCorePoolSize()
               + ", maxPoolSize=" + getMaximumPoolSize()
               + ", queueCapacity=" + getQueueCapacity()
               + (fixedSize ? "" : ", growthThreshold=" + getGrowthThreshold())
               + (fixedSize ? "" : ", growthRate=" + getGrowthRate() + "%")
               + String.format(", averageQueueSize=%.2f", getAverageQueueSize())
               + ", peakQueueSize=" + getPeakQueueSize()
               + String.format(", averageActiveThreads=%.2f", getAverageActiveThreads())
               + (fixedSize ? "" : ", peakPoolSize=" + getLargestPoolSize())
               + ", currentPoolSize=" + getPoolSize()
               + ", completedTasks=" + getCompletedTasks()
               + ", failedTasks=" + getFailedTasks()
               + ", rejectedTasks=" + getRejectionCount()
               + '}';
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        activeThreads.incrementAndGet();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        completedTasks.incrementAndGet();
        totalActiveThreads.add(activeThreads.getAndDecrement());
    }

    @Override
    public void shutdown() {
        Event.write();
        super.shutdown();
    }

    /**
     * A {@link RejectedExecutionHandler} that supports pool growth by re-attempting to add the
     * task to the queue. If the queue is actually full, the rejection is counted and an exception
     * thrown.
     */
    public static class RejectionHandler implements RejectedExecutionHandler {
        private final AtomicInteger rejections = new AtomicInteger();

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {

            // Just add it to the queue if there is capacity

            final WorkQueue queue = ((ThreadPool) executor).getQueue();
            if (!queue.offer(task)) {

                // No capacity, so reject

                LOGGER.warning(rejectionMessage(executor));
                rejections.incrementAndGet();
                throwException(executor);
            }
        }

        /**
         * Returns the number of rejections.
         *
         * @return the count.
         */
        public int getRejectionCount() {
            return rejections.get();
        }

        /**
         * Throws an exception.
         *
         * @param executor The executor that is rejecting the task.
         */
        protected void throwException(ThreadPoolExecutor executor) {
            throw new RejectedExecutionException(rejectionMessage(executor));
        }

        private static String rejectionMessage(ThreadPoolExecutor executor) {
            final ThreadPool pool = (ThreadPool) executor;
            return "Task rejected by ThreadPool '" + pool.getName() + "': queue is full";
        }
    }

    /**
     * A {@link ThreadFactory} that creates threads in a separate {@link ThreadGroup}.
     */
    private static class GroupedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final String namePrefix;
        private final boolean useDaemonThreads;
        private final AtomicInteger threadCount;

        GroupedThreadFactory(String groupName, String threadNamePrefix, boolean useDaemonThreads) {
            this.group = new ThreadGroup(groupName);
            this.namePrefix = threadNamePrefix;
            this.useDaemonThreads = useDaemonThreads;
            this.threadCount = new AtomicInteger();
        }

        @Override
        public Thread newThread(Runnable runnable) {
            final String name = namePrefix + threadCount.incrementAndGet();
            final Thread thread = new Thread(group, runnable, name);
            thread.setDaemon(useDaemonThreads);
            return thread;
        }
    }

    private static WorkQueue createQueue(int capacity, int corePoolSize, int maxPoolSize, int growthThreshold, int growthRate) {
        if (maxPoolSize == corePoolSize || growthRate == 0) {
            return new WorkQueue(capacity);
        } else {
            final Predicate<ThreadPool> growthPolicy = new RateLimitGrowth(growthThreshold, growthRate);
            return new DynamicPoolWorkQueue(growthPolicy, capacity, maxPoolSize);
        }
    }

    /**
     * A queue that tracks peak and average sizes.
     */
    static class WorkQueue extends ConcurrentLinkedQueue<Runnable> implements BlockingQueue<Runnable> {
        private final int capacity;
        private final LongAdder totalSize;
        private final AtomicInteger totalTasks;
        private final AtomicInteger peakSize;
        private final Semaphore semaphoreRead;
        private final Semaphore semaphoreWrite;

        /**
         * Constructor. Initially {@code capacity} writes (enqueues) and 0 reads (dequeues)
         * are available.
         *
         * @param capacity The queue capacity.
         */
        WorkQueue(int capacity) {
            this.capacity = capacity;
            this.semaphoreRead = new Semaphore(0);
            this.semaphoreWrite = new Semaphore(capacity);
            this.totalSize = new LongAdder();
            this.totalTasks = new AtomicInteger();
            this.peakSize = new AtomicInteger();
        }

        /**
         * Called by the {@link ThreadPool} constructor to enable the queue
         * to keep a reference.
         *
         * @param pool The pool that owns this queue.
         */
        void setPool(ThreadPool pool) {
        }

        @Override
        public boolean offer(Runnable task) {
            if (!semaphoreWrite.tryAcquire()) {
               return false;
            }
            enqueue(task);
            semaphoreRead.release();
            return true;
        }

        @Override
        public Runnable poll() {
            if (!semaphoreRead.tryAcquire()) {
               return null;
            }
            semaphoreWrite.release();
            return super.poll();
        }

        @Override
        public boolean offer(Runnable task, long timeout, TimeUnit tu) throws InterruptedException {
           if (!semaphoreWrite.tryAcquire(timeout, tu)) {
              return false;
           }
           enqueue(task);
           semaphoreRead.release();
           return true;
        }

        @Override
        public Runnable poll(long timeout, TimeUnit tu) throws InterruptedException {
           if (!semaphoreRead.tryAcquire(timeout, tu)) {
              return null;
           }
           semaphoreWrite.release();
           return super.poll();
        }

        @Override
        public void put(Runnable task) throws InterruptedException {
           semaphoreWrite.acquire();
           enqueue(task);
           semaphoreRead.release();
        }

        @Override
        public Runnable take() throws InterruptedException {
           semaphoreRead.acquire();
           semaphoreWrite.release();
           return super.poll();
        }

        /**
         * Enqueue the task by invoking the parent {@link #offer(Runnable)} method, updating
         * the statistics if successful. Moving the actual enqueue logic here provides flexibility
         * for subclasses that override {@link #offer(Runnable)} and provides a pathway for the
         * {@link RejectionHandler} to directly enqueue a task without invoking the subclass.
         *
         * @param task The task to enqueue.
         * @return {@code true} if the task was enqueued, {@code false} if the queue is full.
         */
        private boolean enqueue(Runnable task) {
            super.offer(task);

            // Update stats
            final int queueSize = size();
            if (queueSize > peakSize.get()) {
                peakSize.set(queueSize);
            }
            totalSize.add(queueSize);
            totalTasks.incrementAndGet();
            return true;
        }

        /**
         * Returns the capacity.
         *
         * @return The capacity.
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * Returns the average size.
         *
         * @return The size.
         */
        public float getAverageSize() {
            final float totalSize = this.totalSize.sum();
            if (totalSize == 0) {
                return 0.0f;
            } else {
                return totalSize / (float) totalTasks.get();
            }
        }

        /**
         * Returns the peak size.
         *
         * @return The size.
         */
        public int getPeakSize() {
            return peakSize.get();
        }

        @Override
        public int drainTo(Collection<? super Runnable> c) {
            int i = 0;
            for (Runnable r = poll(); r != null; i++, r = poll()) {
                c.add(r);
            }
            return i;
        }

        @Override
        public int drainTo(Collection<? super Runnable> c, int m) {
            if (m <= 0) {
                return 0;
            }

            Runnable r = poll();
            int i = 1;
            m--;
            while (m > 0 && r != null) {
                c.add(r);
                i++;
                m--;
                r = poll();
            }

            if (r != null) {
                c.add(r);
            }
            return i;
        }

        @Override
        public int size() {
            return semaphoreRead.availablePermits();
        }

        @Override
        public int remainingCapacity() {
            // size() should really never become greater than capacity
            return Math.max(capacity - size(), 0);
        }
    }

    /**
     * A queue that uses a predicate to determine if an offered task should be enqueued or if the associated thread pool
     * should add a thread. The predicate is consulted only when the pool is below the maximum size and there are no idle
     * threads; otherwise, the task is always enqueued.
     * <p>
     * This implementation relies on the behavior of {@link ThreadPoolExecutor#execute(Runnable)} and its interaction with
     * the queue and the {@link RejectedExecutionHandler}. Specifically, when the {@link #offer(Runnable)} method returns
     * {@code false}, the execute method attempts to add a thread, handing it the task to execute. If the pool has reached
     * maximum size, the task is instead handed off to the rejection handler.
     * <p>
     * To avoid introducing contention, this class explicitly does not try to coordinate across multiple threads invoking
     * {@link ThreadPoolExecutor#execute(Runnable)}; therefore, by the time a decision is made to add a thread, the state
     * on which that decision was made may have changed. In this case, the pool may fail to add a thread because the max
     * size has actually already been reached; to avoid rejection due to this race condition, we install a handler that
     * simply causes the task to be enqueued (see {@link RejectionHandler}).
     */
    static final class DynamicPoolWorkQueue extends WorkQueue {
        private final Predicate<ThreadPool> growthPolicy;
        private final int maxPoolSize;
        // We can't make pool final because it is a circular dependency, but we set it during the construction of
        // the pool itself and therefore don't have to worry about concurrent access.
        private ThreadPool pool;

        /**
         * Constructor.
         *
         * @param growthPolicy The growth policy.
         * @param capacity The queue capacity.
         * @param maxPoolSize The maximum pool size.
         */
        DynamicPoolWorkQueue(Predicate<ThreadPool> growthPolicy, int capacity, int maxPoolSize) {
            super(capacity);
            this.maxPoolSize = maxPoolSize;
            this.growthPolicy = growthPolicy;
        }

        @Override
        void setPool(ThreadPool pool) {
            this.pool = pool;
        }

        @Override
        public boolean offer(Runnable task) {

            // Are we maxed out?

            final int currentSize = pool.getPoolSize();
            if (currentSize >= maxPoolSize) {

                // Yes, so enqueue if we can

                Event.add(Event.Type.MAX, pool, this);
                return super.offer(task);

            } else if (pool.getActiveThreads() < currentSize) {

                // No, but we've got idle threads so enqueue if we can

                Event.add(Event.Type.IDLE, pool, this);
                return super.offer(task);

            } else {

                // Ok, we might want to add a thread so ask our policy

                if (growthPolicy.test(pool)) {

                    // Add a thread. Note that this can still result in a rejection due to a race condition
                    // in which the pool has not yet grown from a previous false return (and so our maxPoolSize
                    // check above is not accurate); in this case, the rejection handler will just add it to
                    // the queue.

                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Adding a thread, pool size = " + pool.getPoolSize() + ", queue size = " + size());
                    }
                    return false;

                } else {
                    // Enqueue if we can
                    return super.offer(task);
                }
            }
        }

        // The base class is serializable, but we don't need to support this behavior
        private void writeObject(ObjectOutputStream stream) throws IOException {
            throw new UnsupportedOperationException("cannot serialize");
        }

        // The base class is serializable, but we don't need to support this behavior
        private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException("cannot deserialize");
        }
    }

    /**
     * A growth policy that will attempt to add a thread to the pool when the queue is above the specified threshold
     * size and a random number is below the specified growth rate. As a guard against extreme queue growth, a thread
     * will always be added if the queue grows above eight times the specified threshold.
     */
    private static class RateLimitGrowth implements Predicate<ThreadPool> {
        private static final int ALWAYS_THRESHOLD_MULTIPLIER = 8;
        private final int queueThreshold;
        private final int alwaysThreshold;
        private final boolean alwaysAdd;
        private final float rate;

        /**
         * Constructor.
         *
         * @param queueThreshold The queue threshold.
         * @param growthRate The growth rate.
         */
        RateLimitGrowth(int queueThreshold, int growthRate) {
            this.queueThreshold = queueThreshold;
            this.alwaysThreshold = queueThreshold * ALWAYS_THRESHOLD_MULTIPLIER;
            this.alwaysAdd = growthRate == 100;
            this.rate = growthRate / 100f;
        }

        @Override
        public boolean test(ThreadPool pool) {
            final WorkQueue queue = pool.getQueue();
            final int queueSize = queue.size();

            // Is the queue above the threshold?

            if (queueSize > queueThreshold) {

                // Yes. Should we grow?
                // Note that this random number generator is quite fast, and on average is faster than or equivalent to
                // alternatives such as a counter (which does not provide even distribution) or System.nanoTime().

                if (alwaysAdd
                    || queueSize > alwaysThreshold
                    || ThreadLocalRandom.current().nextFloat() < rate) {

                    // Yep

                    Event.add(Event.Type.ADD, pool, queue);
                    return true;

                } else {

                    // No, so don't grow yet

                    Event.add(Event.Type.WAIT, pool, queue);
                    return false;
                }

            } else {

                // Queue is below the threshold, don't grow

                Event.add(Event.Type.BELOW, pool, queue);
                return false;
            }
        }
    }

    /**
     * This class supports recording of fine grained pool, queue and GC events to monitor growth behavior under
     * various load conditions.
     * <p>
     * Disabled by default, it can be enabled by setting the {@code "thread.pool.events"} system property to the
     * number of events to capture. On pool shutdown, events are written in CSV format to a file in the current
     * working directory.
     * <p>
     * Note that if this mechanism is removed, the {@code "java.management"} requires clause should be removed
     * from {@code module-info.java}.
     */
    private static class Event implements Comparable<Event> {
        private static final int MAX_EVENTS = getIntProperty("thread.pool.events", 0);
        private static final int DELAY_SECONDS = getIntProperty("thread.pool.events.delay", 0);
        private static final List<Event> EVENTS = MAX_EVENTS == 0 ? Collections.emptyList() : new ArrayList<>(MAX_EVENTS);
        private static final String EVENTS_FILE_NAME = "thread-pool-events.csv";
        private static final String FILE_HEADER = "Elapsed Seconds,Completed Tasks,Event,Threads,Active Threads,Queue Size%n";
        private static final AtomicBoolean STARTED = new AtomicBoolean();
        private static final AtomicBoolean WRITTEN = new AtomicBoolean();
        private static final long START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();
        private final long time;
        private final Type type;
        private final int threads;
        private final int activeThreads;
        private final int queueSize;
        private final int completedTasks;

        enum Type {
            IDLE,
            MAX,
            BELOW,
            ADD,
            WAIT,
            GC
        }

        private Event(Type type, ThreadPool pool, WorkQueue queue) {
            this.time = System.currentTimeMillis();
            this.type = type;
            this.threads = pool.getPoolSize();
            this.activeThreads = pool.getActiveThreads();
            this.queueSize = queue.size();
            this.completedTasks = pool.getCompletedTasks();
        }

        @Override
        public int compareTo(Event o) {
            return Long.compare(time, o.time);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Event event = (Event) o;
            return time == event.time
                   && threads == event.threads
                   && activeThreads == event.activeThreads
                   && queueSize == event.queueSize
                   && completedTasks == event.completedTasks
                   && type == event.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(time, type, threads, activeThreads, queueSize, completedTasks);
        }

        private String toCsv() {
            final float elapsedMillis = time - START_TIME;
            final float elapsedSeconds = elapsedMillis / 1000f;
            return String.format("%.4f,%d,%s,%d,%d,%d%n", elapsedSeconds, completedTasks, type, threads, activeThreads,
                                 queueSize);
        }

        /**
         * Add an event if recording is enabled.
         *
         * @param type The event type.
         * @param pool The thread pool.
         * @param queue The queue.
         */
        private static void add(Type type, ThreadPool pool, WorkQueue queue) {
            if (shouldAdd()) {
                if (!STARTED.getAndSet(true)) {
                    LOGGER.info("Recording up to " + MAX_EVENTS + " thread pool events");
                    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                        final NotificationEmitter emitter = (NotificationEmitter) bean;
                        emitter.addNotificationListener((notification, handback) -> {
                            if (notification.getType().equals("com.sun.management.gc.notification") && !WRITTEN.get()) {
                                add(Type.GC, pool, queue);
                            }
                        }, null, null);
                    }
                    Runtime.getRuntime().addShutdownHook(new Thread(Event::write));
                }
                EVENTS.add(new Event(type, pool, queue));
            }
        }

        private static boolean shouldAdd() {
            if (EVENTS.size() < MAX_EVENTS) {
                if (DELAY_SECONDS == 0) {
                    return true;
                } else {
                    final long elapsedMillis = System.currentTimeMillis() - START_TIME;
                    return (elapsedMillis / 1000) >= DELAY_SECONDS;
                }
            }
            return false;
        }

        private static void write() {
            if (!EVENTS.isEmpty() && !WRITTEN.getAndSet(true)) {
                final Path file = Paths.get(EVENTS_FILE_NAME).toAbsolutePath();
                LOGGER.info("Writing thread pool events to " + file);
                EVENTS.sort(null);
                try (OutputStream out = Files.newOutputStream(file,
                                                              StandardOpenOption.CREATE,
                                                              StandardOpenOption.WRITE,
                                                              StandardOpenOption.TRUNCATE_EXISTING)) {
                    out.write(FILE_HEADER.getBytes(StandardCharsets.UTF_8));
                    for (Event event : EVENTS) {
                        out.write(event.toCsv().getBytes(StandardCharsets.UTF_8));
                    }
                    LOGGER.info("Finished writing thread pool events");
                } catch (Throwable e) {
                    LOGGER.warning("failed to write thread pool events" + e);
                }
            }
        }

        private static int getIntProperty(String propertyName, int defaultValue) {
            final String value = System.getProperty(propertyName);
            return value == null ? defaultValue : Integer.parseInt(value);
        }
    }
}
