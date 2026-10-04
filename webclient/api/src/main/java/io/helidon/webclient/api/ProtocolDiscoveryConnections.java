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

package io.helidon.webclient.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver;
import io.helidon.webclient.spi.ClientConnectionCache;

// Owns observed ALPN probes until a selected HTTP implementation accepts the connection.
final class ProtocolDiscoveryConnections extends ClientConnectionCache {
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<Registration> active = new HashSet<>();
    private boolean closed;

    ProtocolDiscoveryConnections() {
        super(false);
    }

    @Override
    public void closeResource() {
        closeConnections(true);
    }

    @Override
    protected void evict() {
        closeConnections(false);
    }

    Registration registration(HttpTransportObserver observer) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Protocol discovery connection owner is closed");
            }
            var registration = new Registration(observer);
            active.add(registration);
            return registration;
        } finally {
            lock.unlock();
        }
    }

    private void closeConnections(boolean closing) {
        List<Registration> toClose;
        lock.lock();
        try {
            closed |= closing;
            toClose = List.copyOf(active);
            active.clear();
        } finally {
            lock.unlock();
        }
        RuntimeException failure = null;
        for (Registration registration : toClose) {
            try {
                registration.closeResource();
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

    final class Registration implements ReleasableResource {
        private final ReentrantLock connectionLock = new ReentrantLock();
        private final HttpTransportObserver observer;
        private ClientConnection connection;
        private boolean finished;

        private Registration(HttpTransportObserver observer) {
            this.observer = observer;
        }

        @Override
        public void closeResource() {
            ClientConnection toClose;
            connectionLock.lock();
            try {
                finished = true;
                toClose = connection;
                connection = null;
                remove();
            } finally {
                connectionLock.unlock();
            }
            if (toClose != null) {
                toClose.closeResource();
            }
        }

        void connect(ClientConnection connection) {
            connectionLock.lock();
            try {
                lock.lock();
                try {
                    if (finished || closed) {
                        throw new IllegalStateException("Protocol discovery connection is closed");
                    }
                } finally {
                    lock.unlock();
                }
                this.connection = connection;
                HttpTransportObserverSupport.observe(connection, observer).connect();
            } finally {
                connectionLock.unlock();
            }
        }

        void handoff() {
            connectionLock.lock();
            try {
                lock.lock();
                try {
                    if (finished || closed) {
                        throw new IllegalStateException("Protocol discovery connection closed before protocol selection");
                    }
                    finished = true;
                    connection = null;
                    active.remove(this);
                } finally {
                    lock.unlock();
                }
            } finally {
                connectionLock.unlock();
            }
        }

        void closed(ClientConnection connection) {
            connectionLock.lock();
            try {
                finished = true;
                this.connection = null;
                remove();
            } finally {
                connectionLock.unlock();
            }
        }

        private void remove() {
            lock.lock();
            try {
                active.remove(this);
            } finally {
                lock.unlock();
            }
        }
    }
}
