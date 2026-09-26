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

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

// Allocated only for observed physical transports. All callbacks, including child completion,
// share one lock so connection closure cannot race protocol or stream callbacks.
final class ClientTransportObservation implements ConnectionObservation {
    private final HttpTransportObserver observer;
    private final ReentrantLock lock = new ReentrantLock();
    private final HandshakeObservation handshakeCompletion = this::handshakeCompleted;
    private ConnectionObservation delegate;
    private HandshakeObservation handshake;
    private ConnectionOutcome outcome;
    private String protocol;
    private boolean closed;
    private boolean handshakeFinished;

    ClientTransportObservation(HttpTransportObserver observer) {
        this.observer = observer;
    }

    @Override
    public HandshakeObservation handshakeStarted() {
        lock.lock();
        try {
            if (closed || delegate == null) {
                return HandshakeObservation.noop();
            }
            if (handshake == null) {
                handshake = delegate.handshakeStarted();
            }
            return handshakeCompletion;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void protocolSelected(String protocol) {
        lock.lock();
        try {
            if (!closed && delegate != null) {
                Objects.requireNonNull(protocol, "protocol");
                if (protocol.equals(this.protocol)) {
                    return;
                }
                if (protocol.isBlank()) {
                    throw new IllegalArgumentException("protocol must not be blank");
                }
                // A reentrant callback may select another protocol and must retain that selection.
                this.protocol = protocol;
                delegate.protocolSelected(protocol);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public StreamObservation streamOpened(Direction direction, Initiator initiator) {
        lock.lock();
        try {
            if (closed || delegate == null) {
                return StreamObservation.noop();
            }
            return new Stream(delegate.streamOpened(direction, initiator));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close(ConnectionOutcome outcome) {
        lock.lock();
        try {
            if (!closed) {
                closed = true;
                if (delegate != null) {
                    delegate.close(outcome);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    void opened(String transport, Handshake handshake) {
        lock.lock();
        try {
            if (!closed && delegate == null) {
                delegate = observer.connectionOpened(Role.CLIENT, transport, handshake);
            }
        } finally {
            lock.unlock();
        }
    }

    void outcome(ConnectionOutcome outcome) {
        lock.lock();
        try {
            if (!closed && (this.outcome == null
                    || ((outcome == ConnectionOutcome.ERROR || outcome == ConnectionOutcome.TIMEOUT)
                    && this.outcome != ConnectionOutcome.ERROR && this.outcome != ConnectionOutcome.TIMEOUT))) {
                this.outcome = outcome;
            }
        } finally {
            lock.unlock();
        }
    }

    void failed(Throwable failure) {
        outcome(HttpTransportObserverSupport.isTimeout(failure) ? ConnectionOutcome.TIMEOUT : ConnectionOutcome.ERROR);
    }

    void closed() {
        lock.lock();
        try {
            close(outcome == null ? ConnectionOutcome.LOCAL_CLOSE : outcome);
        } finally {
            lock.unlock();
        }
    }

    private void handshakeCompleted(HandshakeOutcome outcome) {
        lock.lock();
        try {
            if (!closed && !handshakeFinished && handshake != null) {
                handshakeFinished = true;
                handshake.close(outcome);
            }
        } finally {
            lock.unlock();
        }
    }

    private final class Stream implements StreamObservation {
        private final StreamObservation observation;
        private boolean completed;

        private Stream(StreamObservation observation) {
            this.observation = observation;
        }

        @Override
        public void close(StreamOutcome outcome) {
            lock.lock();
            try {
                if (!closed && !completed) {
                    completed = true;
                    observation.close(outcome);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
