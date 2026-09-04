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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

final class QuicClientImpl implements QuicClient {
    private final QuicClientConfig config;
    private final QuicClientRuntime runtime;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    QuicClientImpl(QuicClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        QuicClientRuntime.Builder builder = QuicClientRuntime.builder()
                .tls(config.tls())
                .executor(config.executor())
                .quicConfig(config.quicConfig())
                .initialResponseTimeout(config.initialResponseTimeout());
        config.bindAddress().ifPresent(builder::bindAddress);
        config.clientId().ifPresent(builder::clientId);
        this.runtime = builder.build();
    }

    @Override
    public QuicClientConfig prototype() {
        return config;
    }

    @Override
    public QuicSession connect(QuicClientTarget target) {
        Objects.requireNonNull(target, "target");
        QuicClientConnection connection;
        lifecycleLock.lock();
        try {
            if (closed.get() || runtime.isClosed()) {
                closed.set(true);
                throw new IllegalStateException("QUIC client is closed");
            }
            try {
                connection = runtime.createConnection(target.peerAddress(),
                                                      target.tlsPeerName(),
                                                      target.tlsPeerPort(),
                                                      target.applicationProtocols().toArray(String[]::new));
            } catch (RuntimeException failure) {
                throw QuicApplicationSession.runtimeFailure(failure);
            }
        } finally {
            lifecycleLock.unlock();
        }
        try {
            QuicBlockingSupport.await(connection.startHandshake(),
                                      config.handshakeTimeout(),
                                      () -> connection.terminate(QuicCloseCommand.silent(
                                              "standalone QUIC client handshake did not complete")),
                                      "QUIC client handshake");
            lifecycleLock.lock();
            try {
                if (closed.get() || runtime.isClosed()) {
                    closed.set(true);
                    connection.terminate(QuicCloseCommand.silent("QUIC client closed during handshake"));
                    throw new IllegalStateException("QUIC client is closed");
                }
                return new QuicApplicationSession(connection, config.streamOpenTimeout());
            } finally {
                lifecycleLock.unlock();
            }
        } catch (RuntimeException | Error failure) {
            try {
                connection.terminate(QuicCloseCommand.silent(failure, "standalone QUIC client handshake failed"));
            } catch (RuntimeException | Error closeFailure) {
                if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public boolean isClosed() {
        if (runtime.isClosed()) {
            closed.set(true);
        }
        return closed.get();
    }

    @Override
    public void close() {
        lifecycleLock.lock();
        try {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
        } finally {
            lifecycleLock.unlock();
        }
        runtime.close();
    }
}
