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

package io.helidon.webclient.grpc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.HttpTransportObserverSupport;
import io.helidon.webclient.spi.ClientConnectionCache;

/**
 * Owns active observed gRPC connections without reusing connections between calls.
 */
final class GrpcClientConnections extends ClientConnectionCache {
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<Registration> active = new HashSet<>();
    private boolean closed;

    GrpcClientConnections() {
        super(false);
    }

    Registration registration(HttpTransportObserver observer) {
        return new Registration(observer);
    }

    @Override
    public void closeResource() {
        closeConnections(true);
    }

    @Override
    protected void evict() {
        closeConnections(false);
    }

    private void closeConnections(boolean closing) {
        List<ClientConnection> toClose;
        lock.lock();
        try {
            closed |= closing;
            toClose = new ArrayList<>(active.size());
            for (Registration registration : active) {
                registration.closed = true;
                toClose.add(registration.connection);
                registration.connection = null;
            }
            active.clear();
        } finally {
            lock.unlock();
        }
        RuntimeException failure = null;
        for (ClientConnection connection : toClose) {
            try {
                connection.closeResource();
            } catch (RuntimeException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    final class Registration {
        private final HttpTransportObserver observer;
        private ClientConnection connection;
        private boolean closed;

        private Registration(HttpTransportObserver observer) {
            this.observer = observer;
        }

        ClientConnection connect(ClientConnection connection) {
            lock.lock();
            try {
                if (GrpcClientConnections.this.closed || closed) {
                    throw new IllegalStateException("gRPC connection owner is closed");
                }
            } finally {
                lock.unlock();
            }
            HttpTransportObserverSupport.observe(connection, observer).connect();
            boolean accepted;
            lock.lock();
            try {
                accepted = !GrpcClientConnections.this.closed && !closed;
                if (accepted) {
                    this.connection = connection;
                    active.add(this);
                }
            } finally {
                lock.unlock();
            }
            if (!accepted) {
                connection.closeResource();
                throw new IllegalStateException("gRPC connection closed while connecting");
            }
            return connection;
        }

        void closed(ClientConnection connection) {
            lock.lock();
            try {
                closed = true;
                this.connection = null;
                active.remove(this);
            } finally {
                lock.unlock();
            }
        }
    }
}
