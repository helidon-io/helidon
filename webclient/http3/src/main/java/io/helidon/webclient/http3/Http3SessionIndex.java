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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded session index with route- and TLS-local mutation views.
 *
 * <p>Reads are safe without external coordination. The owner must serialize mutations.
 */
final class Http3SessionIndex<K, V, R, T> {
    private final int capacity;
    private final Map<K, V> sessions = new ConcurrentHashMap<>();
    private final LinkedHashMap<K, IndexedValue<V, R, T>> insertionOrder = new LinkedHashMap<>();
    private final Map<R, Set<K>> sessionsByRoute = new HashMap<>();
    private final Map<T, TlsBucket<K>> sessionsByTls = new HashMap<>();

    Http3SessionIndex(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("HTTP/3 session index capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    V get(K key) {
        return sessions.get(key);
    }

    Insertion<K, V> putIfAbsent(K key, V value, R route, T tls, long tlsGeneration) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(tls, "tls");
        V existing = sessions.get(key);
        if (existing != null) {
            return new Insertion<>(existing, null);
        }
        TlsBucket<K> tlsBucket = sessionsByTls.get(tls);
        if (tlsBucket != null && tlsBucket.generation != tlsGeneration) {
            throw new IllegalStateException("HTTP/3 TLS-session bucket contains multiple generations");
        }

        Entry<K, V> evicted = null;
        if (insertionOrder.size() >= capacity) {
            Map.Entry<K, IndexedValue<V, R, T>> oldest = insertionOrder.firstEntry();
            if (oldest == null) {
                throw new IllegalStateException("HTTP/3 session index accounting is empty at capacity");
            }
            evicted = remove(oldest.getKey(), oldest.getValue());
        }

        tlsBucket = sessionsByTls.computeIfAbsent(tls, _ -> new TlsBucket<>(tlsGeneration));
        sessions.put(key, value);
        insertionOrder.put(key, new IndexedValue<>(value, route, tls));
        sessionsByRoute.computeIfAbsent(route, _ -> new HashSet<>()).add(key);
        tlsBucket.sessions.add(key);
        return new Insertion<>(null, evicted);
    }

    boolean remove(K key, V value) {
        IndexedValue<V, R, T> indexed = insertionOrder.get(key);
        return indexed != null && indexed.value() == value && remove(key, indexed) != null;
    }

    List<Entry<K, V>> removeRoute(R route) {
        Set<K> routeSessions = sessionsByRoute.get(route);
        if (routeSessions == null) {
            return List.of();
        }
        List<Entry<K, V>> removed = new ArrayList<>(routeSessions.size());
        for (K key : List.copyOf(routeSessions)) {
            IndexedValue<V, R, T> indexed = insertionOrder.get(key);
            if (indexed != null) {
                removed.add(Objects.requireNonNull(remove(key, indexed)));
            }
        }
        return removed;
    }

    List<Entry<K, V>> removeStaleTlsGeneration(T tls, long currentGeneration) {
        TlsBucket<K> tlsBucket = sessionsByTls.get(tls);
        if (tlsBucket == null || tlsBucket.generation == currentGeneration) {
            return List.of();
        }
        List<Entry<K, V>> removed = new ArrayList<>(tlsBucket.sessions.size());
        for (K key : List.copyOf(tlsBucket.sessions)) {
            IndexedValue<V, R, T> indexed = insertionOrder.get(key);
            if (indexed != null) {
                removed.add(Objects.requireNonNull(remove(key, indexed)));
            }
        }
        return removed;
    }

    List<Entry<K, V>> clear() {
        List<Entry<K, V>> removed = new ArrayList<>(insertionOrder.size());
        insertionOrder.forEach((key, indexed) -> removed.add(new Entry<>(key, indexed.value())));
        sessions.clear();
        insertionOrder.clear();
        sessionsByRoute.clear();
        sessionsByTls.clear();
        return removed;
    }

    private Entry<K, V> remove(K key, IndexedValue<V, R, T> indexed) {
        if (!sessions.remove(key, indexed.value())) {
            return null;
        }
        insertionOrder.remove(key);
        Set<K> routeSessions = sessionsByRoute.get(indexed.route());
        if (routeSessions == null || !routeSessions.remove(key)) {
            throw new IllegalStateException("Missing HTTP/3 route-session accounting");
        }
        if (routeSessions.isEmpty()) {
            sessionsByRoute.remove(indexed.route());
        }
        TlsBucket<K> tlsBucket = sessionsByTls.get(indexed.tls());
        if (tlsBucket == null || !tlsBucket.sessions.remove(key)) {
            throw new IllegalStateException("Missing HTTP/3 TLS-session accounting");
        }
        if (tlsBucket.sessions.isEmpty()) {
            sessionsByTls.remove(indexed.tls());
        }
        return new Entry<>(key, indexed.value());
    }

    record Insertion<K, V>(V existing, Entry<K, V> evicted) {
    }

    record Entry<K, V>(K key, V value) {
    }

    private record IndexedValue<V, R, T>(V value, R route, T tls) {
    }

    private static final class TlsBucket<K> {
        private final long generation;
        private final Set<K> sessions = new HashSet<>();

        private TlsBucket(long generation) {
            this.generation = generation;
        }
    }
}
