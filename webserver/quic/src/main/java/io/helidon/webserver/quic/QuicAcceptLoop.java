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

package io.helidon.webserver.quic;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.quic.QuicConnection;
import io.helidon.quic.SequentialScheduler;

final class QuicAcceptLoop {
    private final Supplier<CompletableFuture<QuicConnection>> accept;
    private final Consumer<QuicConnection> dispatch;
    private final Consumer<QuicConnection> discard;
    private final Consumer<Throwable> failure;
    private final Executor executor;
    private final SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(this::drain);
    private final ReentrantLock stateLock = new ReentrantLock();

    private CompletableFuture<QuicConnection> pendingAccept;
    private boolean stopped;

    QuicAcceptLoop(Supplier<CompletableFuture<QuicConnection>> accept,
                   Consumer<QuicConnection> dispatch,
                   Consumer<QuicConnection> discard,
                   Consumer<Throwable> failure,
                   Executor executor) {
        this.accept = Objects.requireNonNull(accept);
        this.dispatch = Objects.requireNonNull(dispatch);
        this.discard = Objects.requireNonNull(discard);
        this.failure = Objects.requireNonNull(failure);
        this.executor = Objects.requireNonNull(executor);
    }

    void start() {
        scheduler.runOrSchedule(executor);
    }

    void stop() {
        stopLoop();
    }

    private void drain() {
        while (true) {
            CompletableFuture<QuicConnection> accepting;
            stateLock.lock();
            try {
                if (stopped) {
                    return;
                }
                accepting = pendingAccept;
                if (accepting != null) {
                    if (!accepting.isDone()) {
                        return;
                    }
                    pendingAccept = null;
                }
            } finally {
                stateLock.unlock();
            }
            if (accepting == null) {
                try {
                    accepting = Objects.requireNonNull(accept.get(), "QUIC accept future");
                } catch (RuntimeException e) {
                    failed(e);
                    return;
                }
                if (!accepting.isDone()) {
                    boolean discardAccept;
                    stateLock.lock();
                    try {
                        discardAccept = stopped;
                        if (!discardAccept) {
                            pendingAccept = accepting;
                        }
                    } finally {
                        stateLock.unlock();
                    }
                    if (discardAccept) {
                        discard(accepting);
                    } else {
                        accepting.whenComplete((_, _) -> schedule());
                    }
                    return;
                }
            }

            QuicConnection connection;
            try {
                connection = accepting.join();
            } catch (CompletionException e) {
                failed(e.getCause() == null ? e : e.getCause());
                return;
            } catch (CancellationException e) {
                failed(e);
                return;
            }
            boolean discardConnection;
            stateLock.lock();
            try {
                discardConnection = stopped;
            } finally {
                stateLock.unlock();
            }
            if (discardConnection) {
                discard.accept(connection);
                return;
            }
            // Ownership transfers here; the dispatcher handles shutdown that starts after the state check.
            try {
                dispatch.accept(connection);
            } catch (RuntimeException e) {
                failed(e);
                return;
            }
        }
    }

    private void schedule() {
        if (scheduler.isStopped()) {
            return;
        }
        try {
            scheduler.runOrSchedule(executor);
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    private void failed(Throwable throwable) {
        stopLoop();
        failure.accept(throwable);
    }

    private void stopLoop() {
        CompletableFuture<QuicConnection> accepting;
        stateLock.lock();
        try {
            stopped = true;
            accepting = pendingAccept;
            pendingAccept = null;
        } finally {
            stateLock.unlock();
        }
        scheduler.stop();
        if (accepting != null) {
            discard(accepting);
        }
    }

    private void discard(CompletableFuture<QuicConnection> accepting) {
        accepting.whenComplete((connection, throwable) -> {
            if (throwable == null) {
                discard.accept(connection);
            }
        });
    }
}
