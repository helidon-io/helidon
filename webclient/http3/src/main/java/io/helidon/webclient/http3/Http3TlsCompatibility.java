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

package io.helidon.webclient.http3;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import io.helidon.common.tls.Tls;

/**
 * Bounded client-local QUIC compatibility cache keyed by TLS identity and generation.
 *
 * <p>Hits are lock-free. Cold entries, reloads, and bounded replacement are serialized per client.
 */
final class Http3TlsCompatibility implements AutoCloseable {
    private static final int DEFAULT_CAPACITY = 16;

    private final Predicate<Tls> compatibility;
    private final AtomicReferenceArray<State> states;
    private final ReentrantLock mutationLock = new ReentrantLock();
    private int replacementCursor;
    private volatile boolean closed;

    Http3TlsCompatibility(Predicate<Tls> compatibility) {
        this(compatibility, DEFAULT_CAPACITY);
    }

    Http3TlsCompatibility(Predicate<Tls> compatibility, int capacity) {
        this.compatibility = Objects.requireNonNull(compatibility, "compatibility");
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        states = new AtomicReferenceArray<>(capacity);
    }

    boolean compatible(Tls tls) {
        Objects.requireNonNull(tls, "tls");
        retry:
        while (true) {
            if (closed) {
                return false;
            }
            long tlsGeneration = tls.generation();
            for (int i = 0; i < states.length(); i++) {
                State current = states.get(i);
                if (current != null && current.tls() == tls && current.generation() == tlsGeneration) {
                    boolean compatible = current.compatible();
                    if (tlsGeneration == tls.generation()) {
                        if (closed) {
                            return false;
                        }
                        return compatible;
                    }
                    continue retry;
                }
            }

            boolean compatible = compatibility.test(tls);
            if (closed) {
                return false;
            }
            if (tlsGeneration != tls.generation()) {
                continue;
            }

            State candidate = new State(tls, tlsGeneration, compatible);
            mutationLock.lock();
            try {
                if (closed) {
                    return false;
                }
                if (tlsGeneration != tls.generation()) {
                    continue;
                }

                for (int i = 0; i < states.length(); i++) {
                    State current = states.get(i);
                    if (current != null && current.tls() == tls) {
                        if (current.generation() == tlsGeneration) {
                            if (tlsGeneration == tls.generation()) {
                                return current.compatible();
                            }
                            continue retry;
                        }
                        states.set(i, candidate);
                        if (tlsGeneration == tls.generation()) {
                            return compatible;
                        }
                        continue retry;
                    }
                }

                for (int i = 0; i < states.length(); i++) {
                    if (states.get(i) == null) {
                        states.set(i, candidate);
                        if (tlsGeneration == tls.generation()) {
                            return compatible;
                        }
                        continue retry;
                    }
                }

                int replacementIndex = Math.floorMod(replacementCursor++, states.length());
                states.set(replacementIndex, candidate);
                if (tlsGeneration == tls.generation()) {
                    return compatible;
                }
            } finally {
                mutationLock.unlock();
            }
        }
    }

    @Override
    public void close() {
        mutationLock.lock();
        try {
            closed = true;
            for (int i = 0; i < states.length(); i++) {
                states.set(i, null);
            }
            replacementCursor = 0;
        } finally {
            mutationLock.unlock();
        }
    }

    private record State(Tls tls, long generation, boolean compatible) {
    }
}
