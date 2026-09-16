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

package io.helidon.quic;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

final class QuicServerImpl implements QuicServer {
    private final QuicServerConfig config;
    private final QuicServerRuntime runtime;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    QuicServerImpl(QuicServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        AtomicInteger connections = new AtomicInteger();
        QuicServerRuntime.Builder builder = QuicServerRuntime.builder()
                .tls(config.tls())
                .executor(config.executor())
                .applicationProtocols(config.applicationProtocols())
                .quicConfig(config.quicConfig())
                .retryEnabled(config.retryEnabled())
                .handshakeTimeout(config.handshakeTimeout())
                .maxPendingHandshakes(config.maxPendingHandshakes())
                .connectionAdmission(() -> {
                    int current = connections.get();
                    while (current < config.maxConnections()) {
                        if (connections.compareAndSet(current, current + 1)) {
                            var released = new AtomicBoolean();
                            Runnable release = () -> {
                                if (released.compareAndSet(false, true)) {
                                    connections.decrementAndGet();
                                }
                            };
                            return QuicServerRuntime.ConnectionPermit.accepted(release, release);
                        }
                        current = connections.get();
                    }
                    return QuicServerRuntime.ConnectionPermit.rejected();
                });
        config.bindAddress().ifPresent(builder::bindAddress);
        config.serverId().ifPresent(builder::serverId);
        this.runtime = builder.build();
    }

    @Override
    public QuicServerConfig prototype() {
        return config;
    }

    @Override
    public QuicServer start() {
        lifecycleLock.lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("QUIC server is closed");
            }
            runtime.start();
            running.set(true);
            return this;
        } catch (RuntimeException | Error failure) {
            closed.set(true);
            running.set(false);
            throw failure;
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public boolean isRunning() {
        if (runtime.isClosed()) {
            closed.set(true);
            running.set(false);
        }
        return running.get() && !closed.get();
    }

    @Override
    public InetSocketAddress localAddress() {
        if (!isRunning()) {
            throw new IllegalStateException("QUIC server is not running");
        }
        return runtime.localAddress();
    }

    @Override
    public QuicSession accept() {
        lifecycleLock.lock();
        CompletableFuture<QuicConnection> accepted;
        try {
            if (!isRunning()) {
                throw new IllegalStateException("QUIC server is not running");
            }
            accepted = runtime.accept();
        } finally {
            lifecycleLock.unlock();
        }
        QuicConnection connection = QuicBlockingSupport.await(accepted,
                                                               true,
                                                               () -> closeUnclaimedConnection(accepted),
                                                               "QUIC server accept");
        lifecycleLock.lock();
        try {
            if (closed.get() || runtime.isClosed()) {
                closed.set(true);
                running.set(false);
                connection.terminate(QuicCloseCommand.silent("QUIC server closed during accept"));
                throw new IllegalStateException("QUIC server is closed");
            }
            return new QuicApplicationSession(connection, config.streamOpenTimeout());
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public QuicServer stop() {
        return stop(config.shutdownTimeout());
    }

    @Override
    public QuicServer stop(Duration timeout) {
        QuicPublicApiSupport.positiveNanosDuration(timeout, "timeout");
        lifecycleLock.lock();
        try {
            if (!closed.compareAndSet(false, true)) {
                return this;
            }
            running.set(false);
        } finally {
            lifecycleLock.unlock();
        }
        if (!runtime.close(timeout)) {
            throw new QuicException("QUIC server did not complete graceful shutdown within " + timeout);
        }
        return this;
    }

    private static void closeUnclaimedConnection(CompletableFuture<QuicConnection> accepted) {
        if (accepted.isDone() && !accepted.isCompletedExceptionally()) {
            QuicConnection connection = accepted.resultNow();
            // Closing can write to the shared interruptible channel. Keep caller interruption away from that I/O,
            // and wait for cleanup to release admission before the interrupted accept returns.
            CompletableFuture.runAsync(() -> connection.terminate(QuicCloseCommand.transport(
                                               QuicTransportErrors.NO_ERROR,
                                               "QUIC server accept interrupted")),
                                       QuicPublicApiSupport.defaultExecutor())
                    .join();
        }
    }
}
