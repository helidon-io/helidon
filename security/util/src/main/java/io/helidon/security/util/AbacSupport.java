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

package io.helidon.security.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A simple registry of keys to values for reading.
 */
public interface AbacSupport {
    /**
     * Return the actual property value or null if not present.
     * This is the only method that needs to be implemented.
     *
     * @param key key (name) of the property
     * @return value of the property or null
     */
    Object getAttributeRaw(String key);

    /**
     * A collection of all property names in this container.
     *
     * @return collection of keys
     */
    Collection<String> getAttributeNames();

    /**
     * Get the property (optional) value.
     *
     * @param key key (name) of the property
     * @return value of the property if exists or empty
     */
    default Optional<Object> getAttribute(String key) {
        return Optional.ofNullable(getAttributeRaw(key));
    }

    /**
     * Implements {@link AbacSupport} interface and supports adding attributes.
     */
    class BasicAttributes implements AbacSupport {
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Map<String, Object> registry = new LinkedHashMap<>();

        public BasicAttributes() {
        }

        public BasicAttributes(AbacSupport toCopy) {
            toCopy.getAttributeNames().forEach(key -> {
                this.registry.put(key, toCopy.getAttributeRaw(key));
            });
        }

        public void put(String classifier, Object value) {
            Lock lock = rwLock.writeLock();

            lock.lock();
            try {
                registry.put(classifier, value);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Collection<String> getAttributeNames() {
            Lock lock = rwLock.readLock();
            lock.lock();
            try {
                return registry.keySet();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Object getAttributeRaw(String key) {
            Lock lock = rwLock.readLock();
            lock.lock();

            try {
                return registry.get(key);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public String toString() {
            return "BasicAttributes{"
                    + "registry=" + registry
                    + '}';
        }
    }
}
