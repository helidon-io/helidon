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

package io.helidon.webserver;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import io.helidon.http.LogFormatter;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * Tracks active HTTP requests on one listener socket.
 */
final class StuckThreadDetectionFilter implements Filter {
    private static final System.Logger LOGGER = System.getLogger(StuckThreadDetectionFilter.class.getName());
    private static final long STOP_WAIT_NANOS = Duration.ofSeconds(1).toNanos();

    private final Set<ActiveRequest> activeRequests = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<ActiveRequest> completedRequests = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Thread> monitorThread = new AtomicReference<>();
    private final long checkPeriodNanos;
    private final long thresholdNanos;
    private final String socketName;

    StuckThreadDetectionFilter(StuckThreadDetectionConfig config, String socketName) {
        this.checkPeriodNanos = config.checkPeriod().toNanos();
        this.thresholdNanos = config.threshold().toNanos();
        this.socketName = socketName;
    }

    @Override
    public void afterStart(WebServer webServer) {
        Thread thread = Thread.ofVirtual()
                .name("stuck-thread-detection-" + socketName)
                .inheritInheritableThreadLocals(false)
                .unstarted(this::monitorRequests);
        if (!monitorThread.compareAndSet(null, thread)) {
            return;
        }
        activeRequests.clear();
        completedRequests.clear();
        running.set(true);
        try {
            thread.start();
        } catch (RuntimeException | Error e) {
            running.set(false);
            monitorThread.compareAndSet(thread, null);
            activeRequests.forEach(ActiveRequest::stop);
            activeRequests.clear();
            throw e;
        }
    }

    @Override
    public void afterStop() {
        running.set(false);
        activeRequests.forEach(ActiveRequest::stop);
        activeRequests.clear();

        Thread thread = monitorThread.getAndSet(null);
        if (thread == null) {
            completedRequests.clear();
            return;
        }
        thread.interrupt();
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + STOP_WAIT_NANOS;
        while (thread.isAlive()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            try {
                thread.join(Duration.ofNanos(remainingNanos));
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        completedRequests.clear();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (!running.get()) {
            chain.proceed();
            return;
        }

        Thread thread = Thread.currentThread();
        var prologue = req.prologue();
        var active = new ActiveRequest(thread,
                                       System.nanoTime(),
                                       prologue.rawProtocol(),
                                       prologue.method().text(),
                                       LogFormatter.escape(prologue.uriPath().path()),
                                       req.id(),
                                       req.serverSocketId(),
                                       req.socketId());
        activeRequests.add(active);
        if (!running.get()) {
            activeRequests.remove(active);
            active.stop();
            chain.proceed();
            return;
        }

        try {
            chain.proceed();
        } finally {
            complete(active);
        }
    }

    private void complete(ActiveRequest active) {
        activeRequests.remove(active);
        if (active.complete(System.nanoTime())) {
            completedRequests.offer(active);
            if (!running.get()) {
                completedRequests.remove(active);
                return;
            }
            Thread thread = monitorThread.get();
            if (thread != null) {
                LockSupport.unpark(thread);
            }
        }
    }

    private void monitorRequests() {
        long nextScan = System.nanoTime() + checkPeriodNanos;
        while (running.get()) {
            ActiveRequest completed;
            while ((completed = completedRequests.poll()) != null) {
                if (running.get()) {
                    logRecovery(completed);
                }
            }

            long nanosUntilScan = nextScan - System.nanoTime();
            if (nanosUntilScan > 0) {
                LockSupport.parkNanos(this, nanosUntilScan);
                if (Thread.interrupted()) {
                    return;
                }
                continue;
            }
            nextScan = System.nanoTime() + checkPeriodNanos;

            if (!LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                continue;
            }

            try {
                long now = System.nanoTime();
                for (ActiveRequest active : activeRequests) {
                    long elapsedNanos = now - active.startNanos;
                    if (elapsedNanos < thresholdNanos || !active.beginReport()) {
                        continue;
                    }

                    StackTraceElement[] stackTrace = active.thread.getStackTrace();
                    if (!activeRequests.contains(active) || !active.reporting()) {
                        active.cancelReport();
                        continue;
                    }

                    var message = new StringBuilder()
                            .append("Request has been running for ")
                            .append(Duration.ofNanos(elapsedNanos))
                            .append(" and may be stuck. Request: ")
                            .append(active.method)
                            .append(' ')
                            .append(active.path)
                            .append(' ')
                            .append(active.protocol)
                            .append(", request id: ")
                            .append(active.requestId)
                            .append(", server socket: ")
                            .append(active.serverSocketId)
                            .append(", connection: ")
                            .append(active.connectionId)
                            .append(", thread: \"")
                            .append(active.threadName)
                            .append("\" (id: ")
                            .append(active.thread.threadId())
                            .append(", virtual: ")
                            .append(active.thread.isVirtual())
                            .append(", state: ")
                            .append(active.thread.getState())
                            .append(')');
                    for (StackTraceElement stackTraceElement : stackTrace) {
                        message.append(System.lineSeparator())
                                .append("\tat ")
                                .append(stackTraceElement);
                    }
                    boolean reported = false;
                    try {
                        LOGGER.log(System.Logger.Level.WARNING, message.toString());
                        reported = true;
                    } finally {
                        if (reported) {
                            if (active.finishReport() && running.get()) {
                                logRecovery(active);
                            }
                        } else {
                            active.abortReport();
                        }
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.ERROR,
                           "Failed to inspect request threads for socket " + socketName,
                           e);
            }
        }
    }

    private void logRecovery(ActiveRequest active) {
        if (!LOGGER.isLoggable(System.Logger.Level.INFO)) {
            return;
        }
        long elapsedNanos = active.completionNanos - active.startNanos;
        LOGGER.log(System.Logger.Level.INFO,
                   "Request previously reported as stuck completed after " + Duration.ofNanos(elapsedNanos)
                           + ". Request: " + active.method + " " + active.path + " " + active.protocol
                           + ", request id: " + active.requestId
                           + ", server socket: " + active.serverSocketId
                           + ", connection: " + active.connectionId
                           + ", thread: \"" + active.threadName + "\" (id: "
                           + active.thread.threadId() + ", virtual: " + active.thread.isVirtual() + ")");
    }

    private static final class ActiveRequest {
        private static final int ACTIVE = 0;
        private static final int REPORTING = 1;
        private static final int REPORTED = 2;
        private static final int COMPLETED = 3;
        private static final int COMPLETED_DURING_REPORT = 4;
        private static final int STOPPED = 5;

        private final AtomicInteger state = new AtomicInteger();
        private final Thread thread;
        private final String threadName;
        private final long startNanos;
        private final String protocol;
        private final String method;
        private final String path;
        private final int requestId;
        private final String serverSocketId;
        private final String connectionId;

        private volatile long completionNanos;

        private ActiveRequest(Thread thread,
                              long startNanos,
                              String protocol,
                              String method,
                              String path,
                              int requestId,
                              String serverSocketId,
                              String connectionId) {
            this.thread = thread;
            this.threadName = LogFormatter.escape(Objects.toString(thread.getName(), ""));
            this.startNanos = startNanos;
            this.protocol = protocol;
            this.method = method;
            this.path = path;
            this.requestId = requestId;
            this.serverSocketId = serverSocketId;
            this.connectionId = connectionId;
        }

        private boolean beginReport() {
            return state.compareAndSet(ACTIVE, REPORTING);
        }

        private boolean reporting() {
            return state.get() == REPORTING;
        }

        private void cancelReport() {
            if (!state.compareAndSet(REPORTING, ACTIVE)) {
                state.compareAndSet(COMPLETED_DURING_REPORT, COMPLETED);
            }
        }

        private boolean finishReport() {
            if (state.compareAndSet(REPORTING, REPORTED)) {
                return false;
            }
            return state.compareAndSet(COMPLETED_DURING_REPORT, COMPLETED);
        }

        private void abortReport() {
            if (!state.compareAndSet(REPORTING, ACTIVE)) {
                state.compareAndSet(COMPLETED_DURING_REPORT, COMPLETED);
            }
        }

        private boolean complete(long completionNanos) {
            this.completionNanos = completionNanos;
            while (true) {
                int currentState = state.get();
                switch (currentState) {
                case ACTIVE:
                    if (state.compareAndSet(ACTIVE, COMPLETED)) {
                        return false;
                    }
                    break;
                case REPORTING:
                    if (state.compareAndSet(REPORTING, COMPLETED_DURING_REPORT)) {
                        return false;
                    }
                    break;
                case REPORTED:
                    if (state.compareAndSet(REPORTED, COMPLETED)) {
                        return true;
                    }
                    break;
                default:
                    return false;
                }
            }
        }

        private void stop() {
            state.set(STOPPED);
        }
    }
}
