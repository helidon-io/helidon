/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
    Object abacAttributeRaw(String key);

    /**
     * A collection of all property names in this container.
     *
     * @return collection of keys
     */
    Collection<String> abacAttributeNames();

    /**
     * Get the property (optional) value.
     *
     * @param key key (name) of the property
     * @return value of the property if exists or empty
     */
    default Optional<Object> abacAttribute(String key) {
        return Optional.ofNullable(abacAttributeRaw(key));
    }

    /**
     * Implements {@link AbacSupport} interface and supports adding attributes.
     * This class is mutable and thread safe.
     */
    class BasicAttributes implements AbacSupport {
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Map<String, Object> registry = new LinkedHashMap<>();

        private BasicAttributes() {
        }

        /**
         * Create empty basic attributes.
         *
         * @return basic attributes
         */
        public static BasicAttributes create() {
            return new BasicAttributes();
        }

        /**
         * Create basic attributes that have all attributes of the toCopy.
         *
         * @param toCopy abac support to copy
         * @return basic attributes with all attributes of the toCopy {@link io.helidon.security.util.AbacSupport}
         */
        public static BasicAttributes create(AbacSupport toCopy) {
            BasicAttributes ba = new BasicAttributes();
            toCopy.abacAttributeNames().forEach(key -> {
                ba.registry.put(key, toCopy.abacAttributeRaw(key));
            });
            return ba;
        }

        /**
         * Put a new attribute to this instance.
         *
         * @param classifier classifier (name) of the attribute
         * @param value      attribute value
         */
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
        public Collection<String> abacAttributeNames() {
            Lock lock = rwLock.readLock();
            lock.lock();
            try {
                return registry.keySet();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Object abacAttributeRaw(String key) {
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
