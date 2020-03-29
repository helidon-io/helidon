/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Class TimedHashMap.
 *
 * @param <K> Type of key.
 * @param <V> Type of value.
 */
public class TimedHashMap<K, V> extends ConcurrentHashMap<K, V> {
    private static final Logger LOGGER = Logger.getLogger(TimedHashMap.class.getName());

    private static final int THREAD_POOL_SIZE = 3;

    private static final ScheduledExecutorService SCHEDULER =
        Executors.newScheduledThreadPool(THREAD_POOL_SIZE);

    private final long ttlInMillis;

    private final Map<K, Long> created = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param ttlInMillis Time to live in millis for map entries.
     */
    public TimedHashMap(long ttlInMillis) {
        this.ttlInMillis = ttlInMillis;
        SCHEDULER.scheduleAtFixedRate(this::expireOldEntries, ttlInMillis,
                                      ttlInMillis, TimeUnit.MILLISECONDS);
    }

    private void expireOldEntries() {
        created.keySet()
               .stream()
               .filter(k -> System.currentTimeMillis() - created.get(k) > ttlInMillis)
               .collect(Collectors.toSet())
               .stream()
               .forEach(k -> {
                   LOGGER.fine("Removing expired key " + k);
                   remove(k);
                   created.remove(k);
               });
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public V put(K key, V value) {
        created.put(key, System.currentTimeMillis());
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.keySet()
         .stream()
         .forEach(k -> created.put(k, System.currentTimeMillis()));
        super.putAll(m);
    }

    @Override
    public V remove(Object key) {
        created.remove(key);
        return super.remove(key);
    }

    @Override
    public void clear() {
        created.clear();
        super.clear();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (!created.containsKey(key)) {
            created.put(key, System.currentTimeMillis());
        }
        return super.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        boolean removed = super.remove(key, value);
        if (removed) {
            created.remove(key);
        }
        return removed;
    }
}
