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

package io.helidon.webserver.http2;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;

/**
 * Serializes HTTP/2 lifecycle callbacks across the connection and its stream threads.
 * Created only for an observed connection.
 */
final class Http2TransportObservation {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition writesFinished = lock.newCondition();
    private final Map<Thread, Integer> terminalWriters = new HashMap<>();
    private final ConnectionObservation observation;
    private boolean stopping;
    private boolean stopped;

    Http2TransportObservation(ConnectionObservation observation) {
        this.observation = observation;
    }

    void protocolSelected() {
        lock.lock();
        try {
            if (!stopping && !stopped) {
                observation.protocolSelected(PROTOCOL_HTTP_2);
            }
        } finally {
            lock.unlock();
        }
    }

    Stream openStream(boolean initiallyRemoteEnded) {
        lock.lock();
        try {
            return new Stream(stopping || stopped ? StreamObservation.noop()
                                                 : observation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE),
                              initiallyRemoteEnded);
        } finally {
            lock.unlock();
        }
    }

    void stop(Duration gracePeriod, Runnable abortSocket) {
        boolean interrupted = false;
        boolean pendingWrites;
        lock.lock();
        try {
            stopping = true;
            // A successful terminal write can still be publishing its outcome. Let admitted writers finish
            // before cancellation so normal connection teardown retains graceful TLS closure.
            long remaining = TimeUnit.NANOSECONDS.convert(gracePeriod);
            while (!terminalWriters.isEmpty() && remaining > 0) {
                try {
                    remaining = writesFinished.awaitNanos(remaining);
                } catch (InterruptedException _) {
                    interrupted = true;
                    break;
                }
            }
            // Remaining writers can be blocked in flow control or socket I/O. Interrupt the actual writer,
            // including asynchronous subprotocol writers, before draining success/failure publication.
            // Keep registration locked while interrupting so a pooled thread cannot advance to unrelated work.
            terminalWriters.keySet().forEach(Thread::interrupt);
            pendingWrites = !terminalWriters.isEmpty();
        } finally {
            lock.unlock();
        }
        try {
            if (pendingWrites) {
                // Platform-thread writes on legacy sockets need transport close as well as interruption.
                abortSocket.run();
            }
        } finally {
            lock.lock();
            try {
                while (!terminalWriters.isEmpty()) {
                    writesFinished.awaitUninterruptibly();
                }
                // Physical observation closes after handle returns; all admitted terminal outcomes are now published.
                stopped = true;
            } finally {
                lock.unlock();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    void connectionClosing(Runnable closing) {
        lock.lock();
        try {
            // A stream thread can send GOAWAY while the connection thread is stopping. Keep the final
            // transport outcome ahead of stop(), including when writing GOAWAY fails or times out.
            closing.run();
        } finally {
            lock.unlock();
        }
    }

    final class Stream {
        private final StreamObservation delegate;
        private final boolean initiallyRemoteEnded;
        private StreamOutcome outcome = StreamOutcome.COMPLETED;
        private volatile boolean applicationStarted;
        private boolean localEnd;
        private boolean remoteEnd;
        private boolean closed;

        private Stream(StreamObservation delegate, boolean initiallyRemoteEnded) {
            this.delegate = delegate;
            this.initiallyRemoteEnded = initiallyRemoteEnded;
            this.remoteEnd = initiallyRemoteEnded;
        }

        void applicationStarted() {
            applicationStarted = true;
        }

        void beginTerminalWrite() {
            lock.lock();
            try {
                if (stopping || stopped) {
                    throw new IllegalStateException("HTTP/2 connection observation is closing");
                }
                terminalWriters.merge(Thread.currentThread(), 1, Integer::sum);
            } finally {
                lock.unlock();
            }
        }

        void endTerminalWrite() {
            lock.lock();
            try {
                Thread writer = Thread.currentThread();
                int pending = terminalWriters.get(writer);
                if (pending == 1) {
                    terminalWriters.remove(writer);
                } else {
                    terminalWriters.put(writer, pending - 1);
                }
                if (terminalWriters.isEmpty()) {
                    writesFinished.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        void requestFailed() {
            lock.lock();
            try {
                outcome = applicationStarted ? StreamOutcome.ERROR : StreamOutcome.REJECTED;
            } finally {
                lock.unlock();
            }
        }

        void rejected() {
            lock.lock();
            try {
                close(StreamOutcome.REJECTED);
            } finally {
                lock.unlock();
            }
        }

        void localEnd() {
            lock.lock();
            try {
                localEnd = true;
                if (remoteEnd || outcome != StreamOutcome.COMPLETED) {
                    close(outcome);
                }
            } finally {
                lock.unlock();
            }
        }

        void remoteEnd() {
            if (initiallyRemoteEnded) {
                return;
            }
            lock.lock();
            try {
                remoteEnd = true;
                if (localEnd) {
                    close(outcome);
                }
            } finally {
                lock.unlock();
            }
        }

        void localReset() {
            lock.lock();
            try {
                close(outcome != StreamOutcome.COMPLETED
                              ? outcome
                              : applicationStarted ? StreamOutcome.RESET : StreamOutcome.REJECTED);
            } finally {
                lock.unlock();
            }
        }

        void remoteReset() {
            lock.lock();
            try {
                close(StreamOutcome.RESET);
            } finally {
                lock.unlock();
            }
        }

        void fail() {
            lock.lock();
            try {
                close(StreamOutcome.ERROR);
            } finally {
                lock.unlock();
            }
        }

        private void close(StreamOutcome outcome) {
            if (!stopped && !closed) {
                closed = true;
                delegate.close(outcome);
            }
        }
    }
}
