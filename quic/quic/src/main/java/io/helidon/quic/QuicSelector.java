/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.quic.QuicEndpoint.QuicSelectableEndpoint;
import io.helidon.quic.QuicEndpoint.QuicVirtualThreadedEndpoint;

/**
 * A QUIC selector to select over one or several quic transport
 * endpoints.
 *
 * @param <T> endpoint type managed by this selector
 */
@Api.Internal
public abstract sealed class QuicSelector<T extends QuicEndpoint> implements Runnable, AutoCloseable
        permits QuicSelector.QuicNioSelector, QuicSelector.QuicVirtualThreadPoller {

    /**
     * The maximum timeout passed to Selector::select.
     */
    private static final long IDLE_PERIOD_MS = 1500;
    private static final System.Logger LOGGER = System.getLogger(QuicSelector.class.getName());
    private static final TimeLine SOURCE = TimeSource.source();
    private static final ScopedValue<Boolean> IS_SELECTOR = ScopedValue.newInstance();
    private final String name;
    private final QuicInstance instance;
    private final QuicSelectorThread thread;
    private final QuicTimerQueue timerQueue;
    private final AtomicBoolean createdLogged = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Supplier<String> logTagSupplier = () -> identityTag(this);
    private volatile boolean done;

    private QuicSelector(QuicInstance instance, QuicRuntimeConfig runtimeConfig, String name) {
        this.instance = instance;
        this.name = name;
        this.timerQueue = new QuicTimerQueue(this::wakeup, this::logTag);
        this.thread = QuicSelectorThread.of(runtimeConfig.endpoint().selectorThreading(), this);
    }

    /**
     * Returns whether the current thread is executing selector logic.
     *
     * @return {@code true} if called from a selector thread
     */
    public static boolean isSelectorThread() {
        return IS_SELECTOR.orElse(Boolean.FALSE);
    }

    /**
     * Returns a new instance of {@code QuicNioSelector}.
     *
     * @return a new instance of {@code QuicNioSelector}.
     * <p>
     * A {@code QuicNioSelector} is an implementation of {@link QuicSelector}
     * based on non blocking {@linkplain DatagramChannel Datagram Channels} and
     * using an underlying {@linkplain Selector NIO Selector}.
     * <p>
     * The returned implementation can only be used with
     * {@link QuicSelectableEndpoint} endpoints.
     *
     * @param quicInstance  the quic instance
     * @param runtimeConfig runtime configuration
     * @param name          the selector name
     * @throws UncheckedIOException if creating the underlying selector fails
     */
    static QuicSelector<? extends QuicEndpoint> createQuicNioSelector(QuicInstance quicInstance,
                                                                      QuicRuntimeConfig runtimeConfig,
                                                                      String name) {
        try {
            Selector selector = Selector.open();
            return new QuicNioSelector(quicInstance, runtimeConfig, selector, name);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create QUIC selector", e);
        }
    }

    /**
     * Returns a new instance of {@code QuicVirtualThreadPoller}.
     *
     * @return a new instance of {@code QuicVirtualThreadPoller}.
     * A {@code QuicVirtualThreadPoller} is an implementation of
     * {@link QuicSelector} based on blocking {@linkplain DatagramChannel
     * Datagram Channels} and using {@linkplain Thread#ofVirtual()
     * Virtual Threads} to poll the datagram channels.
     * <p>
     * The returned implementation can only be used with
     * {@link QuicVirtualThreadedEndpoint} endpoints.
     *
     * @param quicInstance  the quic instance
     * @param runtimeConfig runtime configuration
     * @param name          the selector name
     */
    static QuicSelector<? extends QuicEndpoint> createQuicVirtualThreadPoller(QuicInstance quicInstance,
                                                                              QuicRuntimeConfig runtimeConfig,
                                                                              String name) {
        return new QuicVirtualThreadPoller(quicInstance, runtimeConfig, name);
    }

    /**
     * Returns the selector name used for logs and thread naming.
     *
     * @return selector name
     */
    public String name() {
        return name;
    }

    // must be overridden by subclasses

    /**
     * Registers an endpoint with this selector.
     *
     * @param endpoint endpoint to register
     * @throws UncheckedIOException if the endpoint channel cannot be registered
     */
    public void register(T endpoint) {
        logTagSupplier = endpoint::channelId;
        if (createdLogged.compareAndSet(false, true) && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "created");
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "attaching endpoint");
        }
    }

    // must be overridden by subclasses

    /**
     * Wakes up any blocking selector loop.
     */
    public void wakeup() {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "waking up selector");
        }
    }

    /**
     * Returns the timer queue associated with this selector.
     *
     * @return selector timer queue
     */
    public QuicTimerQueue timer() {
        return timerQueue;
    }

    @Override
    public final void run() {
        ScopedValue.where(IS_SELECTOR, true).run(this::runSelector);
    }

    /**
     * Computes delay until the next timer deadline to use as selector timeout.
     *
     * @return timeout in milliseconds, always greater than zero
     */
    public long computeNextDeadLine() {
        Deadline now = SOURCE.instant();
        Deadline deadline = timerQueue.processEventsAndReturnNextDeadline(now, instance.executor());
        if (deadline.equals(Deadline.MAX)) {
            return IDLE_PERIOD_MS;
        }
        if (deadline.equals(Deadline.MIN)) {
            logTimerTrace("%s millis until %s", 1, "now");
            return 1;
        }
        now = SOURCE.instant();
        long millis = now.until(deadline, ChronoUnit.MILLIS);
        // millis could be 0 if the next deadline is within 1ms of now.
        // in that case, round up millis to 1ms since returning 0
        // means the selector would block indefinitely
        logTimerTrace("%s millis until %s", (millis <= 0L ? 1L : millis), deadline);
        return millis <= 0L ? 1L : millis;
    }

    /**
     * Starts the selector thread.
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            thread.start();
        }
    }

    /**
     * Shuts down the {@code QuicSelector} by invoking {@link Selector#close()}.
     * This method doesn't wait for the selector thread to terminate.
     *
     * @see #awaitTermination(long, TimeUnit)
     */
    public void shutdown() {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "closing");
        }
        done = true;
    }

    /**
     * Awaits termination of the selector thread, up until
     * the given timeout has elapsed.
     * If the current thread is the selector thread, returns
     * immediately without waiting.
     *
     * @param timeout the maximum time to wait for termination
     * @param unit    the timeout unit
     */
    public void awaitTermination(long timeout, TimeUnit unit) {
        if (isSelectorThread()) {
            return;
        }
        try {
            thread.thread().join(unit.toMillis(timeout));
        } catch (InterruptedException ie) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "awaitTermination interrupted: " + ie);
            }
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes this {@code QuicSelector}.
     * This method calls {@link #shutdown()} and then waits for the selector thread
     * to terminate. If interrupted while waiting, it restores the interrupt status and
     * returns after initiating shutdown. A call made by this selector's own thread, or by
     * one of a virtual-thread poller's workers, initiates shutdown without waiting for itself.
     */
    @Override
    public void close() {
        close(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    /**
     * Closes this selector and waits up to the supplied timeout for full termination.
     * Shutdown remains initiated when the timeout expires or the caller is interrupted.
     *
     * @param timeout maximum wait; must not be negative
     * @param unit timeout unit
     * @return {@code true} if termination was observed within the allotted wait;
     *         {@code false} if the wait expired, was interrupted, or was skipped to avoid waiting for the current worker
     */
    public boolean close(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }
        shutdown();
        if (started.compareAndSet(false, true)) {
            thread.start();
        }
        if (!canAwaitTermination()) {
            return !thread.thread().isAlive();
        }
        long timeoutNanos = unit.toNanos(timeout);
        long startedAt = System.nanoTime();
        while (thread.thread().isAlive()) {
            long elapsed = Math.max(0, System.nanoTime() - startedAt);
            long remainingNanos = elapsed >= timeoutNanos ? 0 : timeoutNanos - elapsed;
            if (remainingNanos == 0) {
                return false;
            }
            try {
                if (remainingNanos == Long.MAX_VALUE) {
                    thread.thread().join();
                } else {
                    long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                    int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
                    thread.thread().join(millis, nanos);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    boolean canAwaitTermination() {
        return Thread.currentThread() != thread.thread();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Aborts the selector and initiates shutdown after an unrecoverable error.
     *
     * @param t abort cause
     */
    public void abort(Throwable t) {
        shutdown();
    }

    final void runtimeFailed(Throwable t) {
        instance.runtimeFailed(t);
    }

    final void logTimerTrace(String format, Object... args) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(System.Logger.Level.TRACE, format, args);
        }
    }

    final void log(System.Logger.Level level, String format, Object... arguments) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level,
                       () -> "[" + logTag() + "] "
                               + (arguments.length == 0 ? format : format.formatted(arguments)));
        }
    }

    final void log(System.Logger.Level level, String message, Throwable throwable) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, "[" + logTag() + "] " + message, throwable);
        }
    }

    abstract void runSelector();

    boolean done() {
        return done;
    }

    // Called in case of RejectedExecutionException, or shutdownNow;

    private static String identityTag(Object instance) {
        return "0x" + HexFormat.of().toHexDigits(System.identityHashCode(instance));
    }

    private String logTag() {
        return logTagSupplier.get();
    }

    /**
     * A {@link QuicSelector} implementation based on blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * Virtual Threads to poll the channels.
     * This implementation is tied to {@link QuicVirtualThreadedEndpoint} instances.
     */
    static final class QuicVirtualThreadPoller extends QuicSelector<QuicVirtualThreadedEndpoint> {
        private final ReentrantLock waitLock = new ReentrantLock();
        private final Condition waitCondition = waitLock.newCondition();
        private final ConcurrentLinkedQueue<EndpointTask> endpoints = new ConcurrentLinkedQueue<>();
        private final ReentrantLock stateLock = new ReentrantLock();
        private final ExecutorService virtualThreadService;
        private final Set<Thread> workerThreads = ConcurrentHashMap.newKeySet();
        private final boolean usePlatformThreads;
        private final AtomicLong wakeups = new AtomicLong();

        private QuicVirtualThreadPoller(QuicInstance instance, QuicRuntimeConfig runtimeConfig, String name) {
            super(instance, runtimeConfig, name);
            this.usePlatformThreads = runtimeConfig.endpoint().pollerUsePlatformThreads();
            ThreadFactory threadFactory = usePlatformThreads
                    ? Thread.ofPlatform().name(name + "-pt-worker", 1).factory()
                    : Thread.ofVirtual().name(name + "-vt-worker-", 1).factory();
            virtualThreadService = Executors.newThreadPerTaskExecutor(task -> threadFactory.newThread(() -> {
                Thread currentThread = Thread.currentThread();
                workerThreads.add(currentThread);
                try {
                    task.run();
                } finally {
                    workerThreads.remove(currentThread);
                }
            }));
        }

        @Override
        public void register(QuicVirtualThreadedEndpoint endpoint) {
            super.register(endpoint);
            endpoint.attach(this);
        }

        public Future<?> startReading(QuicVirtualThreadedEndpoint endpoint) {
            EndpointTask task;
            stateLock.lock();
            try {
                if (done()) {
                    throw new ClosedSelectorException();
                }
                task = new EndpointTask(endpoint, endpoints);
                endpoints.add(task);
                return virtualThreadService.submit(task);
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        public void shutdown() {
            markDone();
            try {
                virtualThreadService.shutdown();
            } finally {
                wakeup();
            }
        }

        @Override
        public void wakeup() {
            super.wakeup();
            waitLock.lock();
            try {
                wakeups.incrementAndGet();
                // there's only one thread that can be waiting
                // on waitCondition - the thread that executes the run()
                // method.
                waitCondition.signal();
            } finally {
                waitLock.unlock();
            }
        }

        @Override
        public void abort(Throwable t) {
            super.shutdown();
            endpoints.removeIf(task -> abort(task, t));
            super.abort(t);
        }

        ExecutorService readLoopExecutor() {
            return virtualThreadService;
        }

        @Override
        boolean canAwaitTermination() {
            return super.canAwaitTermination() && !workerThreads.contains(Thread.currentThread());
        }

        void markDone() {
            // use stateLock to prevent startReading
            // to be called *after* shutdown.
            stateLock.lock();
            try {
                super.shutdown();
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        void runSelector() {
            try {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "started");
                }
                long waited = 0;
                while (!done()) {
                    long wakeups = this.wakeups.get();
                    long timeout = Math.min(computeNextDeadLine(), IDLE_PERIOD_MS);
                    long currentWakeups = this.wakeups.get();
                    logTimerTrace("wait(%s) wakeups:%s (+%s), waited:%s",
                                  timeout, currentWakeups, currentWakeups - wakeups, waited);
                    long wwaited = -1;
                    waitLock.lock();
                    try {
                        if (done()) {
                            return;
                        }
                        if (wakeups == this.wakeups.get()) {
                            var start = System.nanoTime();
                            try {
                                waitCondition.await(timeout, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                if (done()) {
                                    return;
                                }
                                throw new IllegalStateException("Selector wait interrupted", e);
                            }
                            var stop = System.nanoTime();
                            waited = (stop - start) / 1000_000;
                            wwaited = waited;
                        } else {
                            waited = 0;
                        }
                    } finally {
                        waitLock.unlock();
                    }
                    if (wwaited != -1 && wwaited < timeout) {
                        logTimerTrace("waked up early: waited %s, timeout %s", waited, timeout);
                    }
                }
            } catch (Throwable t) {
                if (done()) {
                    return;
                }
                log(System.Logger.Level.ERROR, "Selector failed", t);
                runtimeFailed(t);
                abort(t);
            } finally {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "exiting");
                }
                if (!done()) {
                    markDone();
                }
                timer().stop();
                endpoints.removeIf(this::close);
                virtualThreadService.close();
            }
        }

        boolean close(EndpointTask task) {
            try {
                task.endpoint.close();
            } catch (Throwable e) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to close endpoint %s: %s", task.endpoint.name(), e);
                }
            }
            return true;
        }

        boolean abort(EndpointTask task, Throwable error) {
            try {
                task.endpoint.abort(error);
            } catch (Throwable e) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to close endpoint %s: %s", task.endpoint.name(), e);
                }
            }
            return true;
        }

        static final class EndpointTask implements Runnable {

            private final QuicVirtualThreadedEndpoint endpoint;
            private final ConcurrentLinkedQueue<EndpointTask> endpoints;

            EndpointTask(QuicVirtualThreadedEndpoint endpoint,
                         ConcurrentLinkedQueue<EndpointTask> endpoints) {
                this.endpoint = endpoint;
                this.endpoints = endpoints;
            }

            @Override
            public void run() {
                try {
                    endpoint.channelReadLoop();
                } finally {
                    endpoints.remove(this);
                }
            }
        }
    }

    /**
     * A {@link QuicSelector} implementation based on non-blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * NIO {@link Selector}.
     * This implementation is tied to {@link QuicSelectableEndpoint} instances.
     */
    static final class QuicNioSelector extends QuicSelector<QuicSelectableEndpoint> {
        private final Selector selector;

        private QuicNioSelector(QuicInstance instance,
                                QuicRuntimeConfig runtimeConfig,
                                Selector selector,
                                String name) {
            super(instance, runtimeConfig, name);
            this.selector = selector;
        }

        public void register(QuicSelectableEndpoint endpoint) {
            super.register(endpoint);
            endpoint.attach(selector);
            selector.wakeup();
        }

        public void wakeup() {
            super.wakeup();
            selector.wakeup();
        }

        /**
         * Shuts down the {@code QuicSelector} by marking the
         * {@linkplain QuicSelector#shutdown() selector done},
         * and {@linkplain Selector#wakeup() waking up the
         * selector thread}.
         * Upon waking up, the selector thread will invoke
         * {@link Selector#close()}.
         * This method doesn't wait for the selector thread to terminate.
         *
         * @see #awaitTermination(long, TimeUnit)
         */
        public void shutdown() {
            super.shutdown();
            selector.wakeup();
        }

        @Override
        public void abort(Throwable error) {
            super.shutdown();
            try {
                if (selector.isOpen()) {
                    for (var k : selector.keys()) {
                        abort(k, error);
                    }
                }
            } catch (ClosedSelectorException cse) {
                // ignore
            } finally {
                super.abort(error);
            }
        }

        @Override
        void runSelector() {
            try {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "started");
                }
                while (!done()) {
                    long timeout = Math.min(computeNextDeadLine(), IDLE_PERIOD_MS);
                    // selected = 0 indicates that no key had its ready ops changed:
                    // it doesn't mean that no key is ready. Therefore - if a key
                    // was ready to read, and is again ready to read, it doesn't
                    // increment the selected count.
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "select(%s)", timeout);
                    }
                    int selected = selector.select(timeout);
                    var selectedKeys = selector.selectedKeys();
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "Selected: changes=%s, keys=%s", selected, selectedKeys.size());
                    }

                    // We do not synchronize on selectedKeys: selectedKeys is only
                    // modified in this thread, whether directly, by calling selectedKeys.clear() below,
                    // or indirectly, by calling selector.close() below.
                    for (var key : selectedKeys) {
                        QuicSelectableEndpoint endpoint = (QuicSelectableEndpoint) key.attachment();
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "selected(%s): %s", Utils.readyOps(key), endpoint);
                        }
                        try {
                            endpoint.selected(key.readyOps());
                        } catch (CancelledKeyException x) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                log(System.Logger.Level.DEBUG, "Key for %s cancelled: %s", endpoint.name(), x);
                            }
                        }
                    }
                    // need to clear the selected keys. select won't do that.
                    selectedKeys.clear();
                }
            } catch (Throwable t) {
                if (done()) {
                    return;
                }
                log(System.Logger.Level.ERROR, "Selector failed", t);
                runtimeFailed(t);
                abort(t);
            } finally {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "exiting");
                }
                timer().stop();

                try {
                    selector.close();
                } catch (IOException io) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "failed to close selector: " + io);
                    }
                }
            }
        }

        boolean abort(SelectionKey key, Throwable error) {
            try {
                QuicSelectableEndpoint endpoint = (QuicSelectableEndpoint) key.attachment();
                endpoint.abort(error);
            } catch (Throwable e) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to close endpoint associated with key %s: %s", key, error);
                }
            }
            return true;
        }
    }

    private record QuicSelectorThread(Thread thread) {
        static QuicSelectorThread ofPlatform(QuicSelector<?> selector) {
            Thread thread = Thread.ofPlatform()
                    .name("Thread(%s)".formatted(selector.name()))
                    .stackSize(0)
                    .inheritInheritableThreadLocals(false)
                    .daemon()
                    .unstarted(selector);
            return new QuicSelectorThread(thread);
        }

        static QuicSelectorThread ofVirtual(QuicSelector<?> selector) {
            Thread thread = Thread.ofVirtual()
                    .name("Thread(%s)".formatted(selector.name()))
                    .inheritInheritableThreadLocals(false)
                    .unstarted(selector);
            return new QuicSelectorThread(thread);
        }

        static QuicSelectorThread of(QuicSelectorThreading config, QuicSelector<?> selector) {
            return switch (config) {
                case VIRTUAL -> ofVirtual(selector);
                case PLATFORM -> ofPlatform(selector);
                default -> selector instanceof QuicNioSelector
                        ? ofPlatform(selector)
                        : ofVirtual(selector);
            };
        }

        void start() {
            thread.start();
        }
    }
}
