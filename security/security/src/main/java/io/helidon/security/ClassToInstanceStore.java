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

package io.helidon.security;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Map of classes to their instances.
 * This class is mutable and not thread safe.
 *
 * @param <T> Type all classes in this instance must extend
 */
public final class ClassToInstanceStore<T> {
    private final Map<Class<? extends T>, T> backingMap = new IdentityHashMap<>();

    /**
     * Create a new instance based on explicit instances.
     * This method creates a MUTABLE instance (contrary to such methods on java collections).
     *
     * @param instances instances to add to the new store
     * @param <T>       type of the store
     * @return new store with instances inserted as when calling {@link #putInstance(Object)} for each of them
     */
    @SafeVarargs
    public static <T> ClassToInstanceStore<T> create(T... instances) {
        ClassToInstanceStore<T> result = new ClassToInstanceStore<>();
        for (T instance : instances) {
            result.putInstance(instance);
        }
        return result;
    }

    /**
     * Get an instance from the store.
     *
     * @param clazz Class under which we have created the mapping (String and CharSequence are two distinct mappings!)
     * @param <U>   Type we are expecting
     * @return Instance of the class or null if no mapping exists
     */
    public <U extends T> Optional<U> getInstance(Class<U> clazz) {
        return Optional.ofNullable(clazz.cast(backingMap.get(clazz)));
    }

    /**
     * Put an explicit class to instance mapping.
     *
     * @param clazz    Class for which to create a mapping (String and CharSequence create two distinct mappings!)
     * @param instance Instance of the interface/class
     * @param <U>      Type we are expecting
     * @return Instance of the class if a mapping previously existed or null for no existing mapping
     */
    public <U extends T> Optional<U> putInstance(Class<? extends U> clazz, U instance) {
        return Optional.ofNullable(clazz.cast(backingMap.put(clazz, instance)));
    }

    /**
     * Remove an instance from the store and return current value.
     *
     * @param clazz Class of object to remove
     * @param <U>   Type of the object
     * @return instance that was removed (if there was one)
     */
    public <U extends T> Optional<U> removeInstance(Class<U> clazz) {
        return Optional.ofNullable(clazz.cast(backingMap.remove(clazz)));
    }

    /**
     * Put all mappings from another instance.
     *
     * @param toCopy store to copy into this store
     */
    public void putAll(ClassToInstanceStore<? extends T> toCopy) {
        this.backingMap.putAll(toCopy.backingMap);
    }

    /**
     * Check if this store contains a mapping for a class.
     *
     * @param clazz class to check
     * @return true if there is a mapping for the class
     */
    public boolean containsKey(Class<? extends T> clazz) {
        return backingMap.containsKey(clazz);
    }

    /**
     * Check if this store is empty.
     *
     * @return true if there are no mappings in this store
     */
    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    /**
     * Put an instance with implicit class mapping into this store. Please use {@link #putInstance(Class, Object)} when
     * feasible as it provides more explicit mapping.
     * This is a helper method for instances obtained by other means or where we are sure of the class of the instance.
     *
     * @param instance instance to map to its class (obtained through {@link Object#getClass()}
     * @param <U>      Type of this instance
     * @return existing value for the class of this instance
     */
    @SuppressWarnings("unchecked")
    public <U extends T> Optional<U> putInstance(U instance) {
        return putInstance((Class<? extends U>) instance.getClass(), instance);
    }

    /**
     * Get all values of this store.
     *
     * @return collection of values
     */
    public Collection<T> values() {
        return backingMap.values();
    }

    /**
     * Get all keys of this store.
     *
     * @return collection of classes used for mapping to instances
     */
    public Collection<Class<? extends T>> keys() {
        return backingMap.keySet();
    }

    /**
     * Returns the toString on underlying map.
     *
     * @return String representation of the mapping
     */
    @Override
    public String toString() {
        return backingMap.toString();
    }
}
