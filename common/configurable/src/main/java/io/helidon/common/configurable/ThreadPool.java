/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.context.ContextAwareExecutorService;

/**
 * A {@link ThreadPoolExecutor} that adds queue state accessors.
 */
public class ThreadPool extends ThreadPoolExecutor {
    private static final Logger LOGGER = Logger.getLogger(ThreadPool.class.getName());

    private final String name;
    private final int queueCapacity;
    private final AtomicInteger peakQueueSize;

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
     * Creates a new {@code ThreadPool} with the default rejected execution handler.
     *
     * @param name The pool name.
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the {@code Runnable}
     * tasks submitted by the {@code execute} method.
     * @param workQueueCapacity The capacity of the work queue.
     * @param threadFactory the factory to use when the executor
     * creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveTime < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    ThreadPool(final String name,
               int corePoolSize,
               int maximumPoolSize,
               long keepAliveTime,
               TimeUnit unit,
               BlockingQueue<Runnable> workQueue,
               int workQueueCapacity,
               ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.name = name;
        this.queueCapacity = workQueueCapacity;
        this.peakQueueSize = new AtomicInteger();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(toString());
        }
    }

    @Override
    public void execute(final Runnable command) {
        super.execute(command);
        // Use a best-effort approach to maintaining the peak size without locking
        final int queueSize = getQueue().size();
        final int currentPeak = peakQueueSize.get();
        if (queueSize > currentPeak) {
            peakQueueSize.compareAndSet(currentPeak, queueSize);
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
        return queueCapacity;
    }

    /**
     * Returns the peak queue size.
     *
     * @return The size.
     */
    public int getPeakQueueSize() {
        return peakQueueSize.get();
    }

    @Override
    public String toString() {
        return "ThreadPool '" + getName() + "' {"
               + "corePoolSize=" + getCorePoolSize()
               + ", maxPoolSize=" + getMaximumPoolSize()
               + (getMaximumPoolSize() > getCorePoolSize() ? ", largestPoolSize=" + getLargestPoolSize() : "")
               + ", completedTasks=" + getCompletedTaskCount()
               + ", peakQueueSize=" + getPeakQueueSize()
               + ", queueCapacity=" + getQueueCapacity()
               + '}';
    }
}
