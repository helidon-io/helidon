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

package io.helidon.http;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

import static java.lang.System.Logger.Level.WARNING;

final class HttpTransportObservers {
    static final ConnectionObservation NOOP_CONNECTION = new NoOpConnectionObservation();
    static final HttpTransportObserver NOOP = (role, transport, handshake) -> {
        Objects.requireNonNull(role, "role");
        requireIdentifier(transport, "transport");
        Objects.requireNonNull(handshake, "handshake");
        return NOOP_CONNECTION;
    };
    static final HandshakeObservation NOOP_HANDSHAKE = outcome -> {
        Objects.requireNonNull(outcome, "outcome");
    };
    static final StreamObservation NOOP_STREAM = outcome -> {
        Objects.requireNonNull(outcome, "outcome");
    };

    private static final System.Logger LOGGER = System.getLogger(HttpTransportObservers.class.getName());

    private HttpTransportObservers() {
    }

    static HttpTransportObserver compose(List<? extends HttpTransportObserver> observers) {
        Objects.requireNonNull(observers, "observers");
        Map<HttpTransportObserver, Boolean> seen = new IdentityHashMap<>();
        List<HttpTransportObserver> unique = new ArrayList<>(observers.size());
        for (HttpTransportObserver observer : observers) {
            Objects.requireNonNull(observer, "observer");
            if (observer != NOOP && seen.put(observer, Boolean.TRUE) == null) {
                unique.add(observer);
            }
        }
        if (unique.isEmpty()) {
            return NOOP;
        }
        return new CompositeObserver(List.copyOf(unique));
    }

    private static void observerFailed(String event, RuntimeException failure) {
        LOGGER.log(WARNING, "HTTP transport observer failed while processing " + event, failure);
    }

    private static String requireIdentifier(String identifier, String name) {
        Objects.requireNonNull(identifier, name);
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return identifier;
    }

    private static final class CompositeObserver implements HttpTransportObserver {
        private final List<HttpTransportObserver> observers;

        private CompositeObserver(List<HttpTransportObserver> observers) {
            this.observers = observers;
        }

        @Override
        public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            Objects.requireNonNull(role, "role");
            requireIdentifier(transport, "transport");
            Objects.requireNonNull(handshake, "handshake");
            List<ConnectionObservation> observations = new ArrayList<>(observers.size());
            for (HttpTransportObserver observer : observers) {
                try {
                    ConnectionObservation observation = Objects.requireNonNull(
                            observer.connectionOpened(role, transport, handshake),
                            "connection observation");
                    if (observation != NOOP_CONNECTION) {
                        observations.add(observation);
                    }
                } catch (RuntimeException failure) {
                    observerFailed("connection open", failure);
                } catch (Throwable failure) {
                    throw failure;
                }
            }
            if (observations.isEmpty()) {
                return NOOP_CONNECTION;
            }
            return new CompositeConnectionObservation(observations);
        }
    }

    private static final class CompositeConnectionObservation implements ConnectionObservation {
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final List<ConnectionObservation> observations;
        private final ArrayDeque<Runnable> transitions = new ArrayDeque<>();
        private CompositeHandshakeObservation handshake;
        private CompositeStreamObservation streams;
        private String protocol;
        private ConnectionOutcome connectionOutcome;
        private int inFlightOperations;
        private boolean transitionDraining;
        private boolean connectionCloseEnqueued;
        private boolean closed;

        private CompositeConnectionObservation(List<ConnectionObservation> observations) {
            this.observations = observations;
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            CompositeHandshakeObservation result;
            boolean open = false;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return NOOP_HANDSHAKE;
                }
                if (handshake == null) {
                    handshake = new CompositeHandshakeObservation(this);
                    inFlightOperations++;
                    open = true;
                }
                result = handshake;
            } finally {
                lifecycleLock.unlock();
            }
            if (open) {
                try {
                    for (ConnectionObservation observation : observations) {
                        try {
                            HandshakeObservation candidate = Objects.requireNonNull(
                                    observation.handshakeStarted(),
                                    "handshake observation");
                            if (candidate != NOOP_HANDSHAKE) {
                                HandshakeOutcome closedOutcome;
                                result.lifecycleLock.lock();
                                try {
                                    closedOutcome = result.outcome;
                                    if (closedOutcome == null) {
                                        result.observations.add(candidate);
                                        continue;
                                    }
                                } finally {
                                    result.lifecycleLock.unlock();
                                }
                                try {
                                    candidate.close(closedOutcome);
                                } catch (RuntimeException failure) {
                                    observerFailed("handshake close", failure);
                                } catch (Throwable failure) {
                                    throw failure;
                                }
                            }
                        } catch (RuntimeException failure) {
                            observerFailed("handshake start", failure);
                        } catch (Throwable failure) {
                            throw failure;
                        }
                    }
                } finally {
                    operationFinished();
                }
            }
            return result;
        }

        @Override
        public void protocolSelected(String protocol) {
            String selectedProtocol = requireIdentifier(protocol, "protocol");
            boolean drain;
            lifecycleLock.lock();
            try {
                if (closed || Objects.equals(this.protocol, selectedProtocol)) {
                    return;
                }
                this.protocol = selectedProtocol;
                drain = enqueueTransitionLocked(() -> {
                    for (ConnectionObservation observation : observations) {
                        try {
                            observation.protocolSelected(selectedProtocol);
                        } catch (RuntimeException failure) {
                            observerFailed("protocol selection", failure);
                        } catch (Throwable failure) {
                            throw failure;
                        }
                    }
                });
            } finally {
                lifecycleLock.unlock();
            }
            if (drain) {
                drainTransitions();
            }
        }

        @Override
        public StreamObservation streamOpened(Direction direction,
                                              Initiator initiator) {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(initiator, "initiator");
            CompositeStreamObservation result;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return NOOP_STREAM;
                }
                result = new CompositeStreamObservation(this);
                result.next = streams;
                if (streams != null) {
                    streams.previous = result;
                }
                streams = result;
                inFlightOperations++;
            } finally {
                lifecycleLock.unlock();
            }
            try {
                for (ConnectionObservation observation : observations) {
                    try {
                        StreamObservation candidate = Objects.requireNonNull(
                                observation.streamOpened(direction, initiator),
                                "stream observation");
                        if (candidate != NOOP_STREAM) {
                            StreamOutcome closedOutcome;
                            lifecycleLock.lock();
                            try {
                                closedOutcome = result.outcome;
                                if (closedOutcome == null) {
                                    result.addObservation(candidate);
                                    continue;
                                }
                            } finally {
                                lifecycleLock.unlock();
                            }
                            try {
                                candidate.close(closedOutcome);
                            } catch (RuntimeException failure) {
                                observerFailed("stream close", failure);
                            } catch (Throwable failure) {
                                throw failure;
                            }
                        }
                    } catch (RuntimeException failure) {
                        observerFailed("stream open", failure);
                    } catch (Throwable failure) {
                        throw failure;
                    }
                }
            } finally {
                operationFinished();
            }
            return result;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            List<Runnable> childClosures = new ArrayList<>();
            lifecycleLock.lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                connectionOutcome = outcome;
                inFlightOperations++;
                if (handshake != null) {
                    HandshakeOutcome handshakeOutcome = switch (outcome) {
                        case NORMAL, LOCAL_CLOSE -> HandshakeOutcome.CANCELLED;
                        case REMOTE_CLOSE, ERROR -> HandshakeOutcome.FAILURE;
                        case TIMEOUT -> HandshakeOutcome.TIMEOUT;
                    };
                    List<HandshakeObservation> handshakeObservations = handshake.prepareClose(handshakeOutcome);
                    if (handshakeObservations != null) {
                        childClosures.add(() -> handshake.dispatchClose(handshakeObservations, handshakeOutcome));
                    }
                }
                StreamOutcome streamOutcome = switch (outcome) {
                    case NORMAL, LOCAL_CLOSE, REMOTE_CLOSE -> StreamOutcome.CANCELLED;
                    case TIMEOUT, ERROR -> StreamOutcome.ERROR;
                };
                while (streams != null) {
                    CompositeStreamObservation stream = streams;
                    removeStreamLocked(stream);
                    if (stream.prepareClose(streamOutcome)) {
                        Object streamObservations = stream.takeObservations();
                        childClosures.add(() -> stream.dispatchClose(streamObservations, streamOutcome));
                    }
                }
            } finally {
                lifecycleLock.unlock();
            }
            try {
                childClosures.forEach(Runnable::run);
            } finally {
                operationFinished();
            }
        }

        private void closeHandshake(CompositeHandshakeObservation handshake,
                                    HandshakeOutcome outcome) {
            List<HandshakeObservation> observations;
            lifecycleLock.lock();
            try {
                observations = handshake.prepareClose(outcome);
                if (observations == null) {
                    return;
                }
                inFlightOperations++;
            } finally {
                lifecycleLock.unlock();
            }
            try {
                handshake.dispatchClose(observations, outcome);
            } finally {
                operationFinished();
            }
        }

        private void closeStream(CompositeStreamObservation stream, StreamOutcome outcome) {
            Object observations;
            lifecycleLock.lock();
            try {
                if (!stream.prepareClose(outcome)) {
                    return;
                }
                observations = stream.takeObservations();
                removeStreamLocked(stream);
                inFlightOperations++;
            } finally {
                lifecycleLock.unlock();
            }
            try {
                stream.dispatchClose(observations, outcome);
            } finally {
                operationFinished();
            }
        }

        private void removeStreamLocked(CompositeStreamObservation stream) {
            if (stream.previous == null) {
                streams = stream.next;
            } else {
                stream.previous.next = stream.next;
            }
            if (stream.next != null) {
                stream.next.previous = stream.previous;
            }
            stream.previous = null;
            stream.next = null;
        }

        private boolean enqueueTransitionLocked(Runnable transition) {
            transitions.addLast(transition);
            if (transitionDraining) {
                return false;
            }
            transitionDraining = true;
            return true;
        }

        private void drainTransitions() {
            Error fatalFailure = null;
            while (true) {
                Runnable transition;
                lifecycleLock.lock();
                try {
                    transition = transitions.pollFirst();
                    if (transition == null) {
                        transitionDraining = false;
                        break;
                    }
                } finally {
                    lifecycleLock.unlock();
                }
                try {
                    transition.run();
                } catch (RuntimeException failure) {
                    observerFailed("connection transition", failure);
                } catch (Error failure) {
                    if (fatalFailure == null) {
                        fatalFailure = failure;
                    } else if (failure != fatalFailure) {
                        fatalFailure.addSuppressed(failure);
                    }
                }
            }
            if (fatalFailure != null) {
                throw fatalFailure;
            }
        }

        private void operationFinished() {
            boolean drain;
            lifecycleLock.lock();
            try {
                inFlightOperations--;
                if (closed && inFlightOperations == 0 && !connectionCloseEnqueued) {
                    connectionCloseEnqueued = true;
                    ConnectionOutcome outcome = connectionOutcome;
                    drain = enqueueTransitionLocked(() -> {
                        for (ConnectionObservation observation : observations) {
                            try {
                                observation.close(outcome);
                            } catch (RuntimeException failure) {
                                observerFailed("connection close", failure);
                            } catch (Throwable failure) {
                                throw failure;
                            }
                        }
                    });
                } else {
                    drain = false;
                }
            } finally {
                lifecycleLock.unlock();
            }
            if (drain) {
                drainTransitions();
            }
        }

    }

    private static final class CompositeHandshakeObservation implements HandshakeObservation {
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final CompositeConnectionObservation owner;
        private final List<HandshakeObservation> observations = new ArrayList<>();
        private HandshakeOutcome outcome;

        private CompositeHandshakeObservation(CompositeConnectionObservation owner) {
            this.owner = owner;
        }

        @Override
        public void close(HandshakeOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            owner.closeHandshake(this, outcome);
        }

        private List<HandshakeObservation> prepareClose(HandshakeOutcome outcome) {
            lifecycleLock.lock();
            try {
                if (this.outcome != null) {
                    return null;
                }
                this.outcome = outcome;
                List<HandshakeObservation> result = List.copyOf(observations);
                observations.clear();
                return result;
            } finally {
                lifecycleLock.unlock();
            }
        }

        private void dispatchClose(List<HandshakeObservation> observations, HandshakeOutcome outcome) {
            for (HandshakeObservation observation : observations) {
                try {
                    observation.close(outcome);
                } catch (RuntimeException failure) {
                    observerFailed("handshake close", failure);
                } catch (Throwable failure) {
                    throw failure;
                }
            }
        }
    }

    private static final class CompositeStreamObservation implements StreamObservation {
        private final CompositeConnectionObservation owner;

        // Guarded by the owner's lifecycle lock. Holds a single delegate or a delegate array.
        private Object observations;
        private int observationCount;
        private CompositeStreamObservation previous;
        private CompositeStreamObservation next;
        private StreamOutcome outcome;

        private CompositeStreamObservation(CompositeConnectionObservation owner) {
            this.owner = owner;
            if (owner.observations.size() > 1) {
                this.observations = new StreamObservation[owner.observations.size()];
            }
        }

        @Override
        public void close(StreamOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            owner.closeStream(this, outcome);
        }

        private static void closeObservation(StreamObservation observation, StreamOutcome outcome) {
            try {
                observation.close(outcome);
            } catch (RuntimeException failure) {
                observerFailed("stream close", failure);
            } catch (Throwable failure) {
                throw failure;
            }
        }

        private void addObservation(StreamObservation observation) {
            if (observations instanceof StreamObservation[] array) {
                array[observationCount++] = observation;
            } else {
                observations = observation;
            }
        }

        private boolean prepareClose(StreamOutcome outcome) {
            if (this.outcome != null) {
                return false;
            }
            this.outcome = outcome;
            return true;
        }

        private Object takeObservations() {
            Object current = observations;
            observations = null;
            return current;
        }

        private void dispatchClose(Object observations, StreamOutcome outcome) {
            // prepareClose prevents further additions before transferring ownership out of the lock.
            if (observations instanceof StreamObservation observation) {
                closeObservation(observation, outcome);
            } else if (observations instanceof StreamObservation[] array) {
                for (int i = 0; i < observationCount; i++) {
                    StreamObservation observation = array[i];
                    array[i] = null;
                    closeObservation(observation, outcome);
                }
            }
        }
    }

    private static final class NoOpConnectionObservation implements ConnectionObservation {
        @Override
        public HandshakeObservation handshakeStarted() {
            return NOOP_HANDSHAKE;
        }

        @Override
        public void protocolSelected(String protocol) {
            requireIdentifier(protocol, "protocol");
        }

        @Override
        public StreamObservation streamOpened(Direction direction,
                                              Initiator initiator) {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(initiator, "initiator");
            return NOOP_STREAM;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
        }
    }
}
