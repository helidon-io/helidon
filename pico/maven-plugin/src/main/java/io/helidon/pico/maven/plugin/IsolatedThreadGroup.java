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

package io.helidon.pico.maven.plugin;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.pico.tools.ToolsException;

/**
 * a ThreadGroup to isolate execution and collect exceptions.
 */
class IsolatedThreadGroup extends ThreadGroup implements Closeable {
    static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);

    private final AtomicInteger counter = new AtomicInteger();
    private final long timeoutInMillis;
    private Throwable uncaughtThrowable;

    private IsolatedThreadGroup(
            String name,
            long timeOutInMillis) {
        super(name);
        this.timeoutInMillis = timeOutInMillis;
    }

    /**
     * Creates am isolated thread group using the default timeout of {@link #DEFAULT_TIMEOUT_MILLIS}.
     *
     * @param name the name of the group
     * @return the instance of the isolated thread group created
     */
    static IsolatedThreadGroup create(
            String name) {
        return new IsolatedThreadGroup(name, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates an isolated thread group using the provided timeout of {@link #DEFAULT_TIMEOUT_MILLIS}.
     *
     * @param name the name of the group
     * @param timeOutInMillis timeoutInMillis used during close processing
     * @return the instance of the isolated thread group created
     */
    static IsolatedThreadGroup create(
            String name,
            long timeOutInMillis) {
        return new IsolatedThreadGroup(name, timeOutInMillis);
    }

    /**
     * Adds an uncaught throwable for this thread group.
     * @param t the throwable to add, or null to reset
     */
    void setUncaughtThrowable(
            Throwable t) {
        if (t instanceof ThreadDeath) {
            return; // harmless
        }
        if (Objects.nonNull(uncaughtThrowable)) {
            // we will only handle 0..1 errors
            return;
        }
        this.uncaughtThrowable = t;
    }

    /**
     * Should be closed at completion.  The implementation will attempt to join threads,
     * terminate any threads after the timeout period, and throw any uncaught errors.
     */
    @Override
    public void close() {
        try {
            joinNonDaemonThreads();
            terminateThreads();
        } catch (Throwable t) {
            setUncaughtThrowable(t);
        }
        throwAnyUncaughtErrors();
    }

    /**
     * If anything was caught, will throw an error immediately.
     */
    public void throwAnyUncaughtErrors() {
        if (Objects.nonNull(uncaughtThrowable)) {
            ToolsException e = new ToolsException("uncaught error", uncaughtThrowable);
            uncaughtThrowable = null;
            throw e;
        }
    }

    /**
     * Prepares the thread for start.
     *
     * @param thread the thread
     * @param loader the loader
     */
    void preStart(
            Thread thread,
            ClassLoader loader) {
        thread.setContextClassLoader(loader);
        thread.setName(getName() + "-" + counter.incrementAndGet());
    }

    private void joinNonDaemonThreads() {
        boolean foundNonDaemon;
        long expiry = System.currentTimeMillis() + timeoutInMillis;
        do {
            foundNonDaemon = false;
            Collection<Thread> threads = getActiveThreads();
            for (Thread thread : threads) {
                if (thread.isDaemon()) {
                    continue;
                }
                // try again; maybe more threads were created while we are busy
                foundNonDaemon = true;
                joinThread(thread, 0);
            }
        } while (foundNonDaemon && System.currentTimeMillis() < expiry);
    }

    private void joinThread(
            Thread thread,
            long timeoutInMillis) {
        try {
            thread.join(timeoutInMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void terminateThreads() {
        // these were not responsive to interruption
        Set<Thread> uncooperativeThreads = new CopyOnWriteArraySet<>();

        getActiveThreads().parallelStream().forEach(thread -> {
            thread.interrupt();
            if (thread.isAlive()) {
                joinThread(thread, timeoutInMillis);
                if (thread.isAlive()) {
                    uncooperativeThreads.add(thread);
                }
            }
        });

        if (!uncooperativeThreads.isEmpty()) {
            uncaughtThrowable = new ToolsException("unable to terminate these threads: " + uncooperativeThreads);
        }
    }

    /**
     * Returns the list of threads for this thread group.
     *
     * @return list of threads
     */
    List<Thread> getActiveThreads() {
        Thread[] threads = new Thread[activeCount()];
        int numThreads = enumerate(threads);
        List<Thread> result = new ArrayList<>(numThreads);
        for (int i = 0; i < threads.length && threads[i] != null; i++) {
            result.add(threads[i]);
        }
        return result;
    }

}
