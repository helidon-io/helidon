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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

final class QuicTlsTicketStore<K> implements AutoCloseable {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<K, QuicTlsResumptionTicket> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final int capacity;
    private final long timeoutMillis;
    private final LongSupplier currentTimeMillis;
    private final boolean enabled;

    private boolean closed;

    QuicTlsTicketStore(int capacity, Duration timeout) {
        this(capacity, timeout, System::currentTimeMillis);
    }

    QuicTlsTicketStore(int capacity, Duration timeout, boolean enabled) {
        this(capacity, timeout, System::currentTimeMillis, enabled);
    }

    QuicTlsTicketStore(int capacity, Duration timeout, LongSupplier currentTimeMillis) {
        this(capacity, timeout, currentTimeMillis, true);
    }

    private QuicTlsTicketStore(int capacity, Duration timeout, LongSupplier currentTimeMillis, boolean enabled) {
        if (capacity < 0) {
            throw new IllegalArgumentException("TLS session cache size must not be negative: " + capacity);
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("TLS session timeout must not be negative: " + timeout);
        }
        this.capacity = capacity;
        long configuredTimeoutMillis;
        try {
            configuredTimeoutMillis = timeout.toMillis();
        } catch (ArithmeticException e) {
            configuredTimeoutMillis = Long.MAX_VALUE;
        }
        this.timeoutMillis = timeout.isZero() ? Long.MAX_VALUE : configuredTimeoutMillis;
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
        this.enabled = enabled;
    }

    static <K> QuicTlsTicketStore<K> disabled() {
        return new QuicTlsTicketStore<>(0, Duration.ZERO, System::currentTimeMillis, false);
    }

    boolean put(K key, QuicTlsResumptionTicket ticket) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ticket, "ticket");
        lock.lock();
        try {
            if (closed || !enabled) {
                return false;
            }
            long now = currentTimeMillis.getAsLong();
            if (ticket.expired(now, timeoutMillis)) {
                entries.remove(key);
                return false;
            }
            entries.put(key, ticket);
            while (capacity > 0 && entries.size() > capacity) {
                var iterator = entries.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
            return entries.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    Optional<QuicTlsResumptionTicket> get(K key) {
        Objects.requireNonNull(key, "key");
        lock.lock();
        try {
            if (closed || !enabled) {
                return Optional.empty();
            }
            long now = currentTimeMillis.getAsLong();
            QuicTlsResumptionTicket ticket = entries.get(key);
            if (ticket == null) {
                return Optional.empty();
            }
            if (ticket.expired(now, timeoutMillis)) {
                entries.remove(key);
                return Optional.empty();
            }
            return Optional.of(ticket);
        } finally {
            lock.unlock();
        }
    }

    boolean enabled() {
        return enabled;
    }

    int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    void clear() {
        lock.lock();
        try {
            entries.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            entries.clear();
        } finally {
            lock.unlock();
        }
    }
}
