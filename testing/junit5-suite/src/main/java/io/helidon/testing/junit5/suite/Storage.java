/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.testing.junit5.suite;

/**
 * Suite specific storage.
 */
public interface Storage {

    /**
     * Retrieve value matching provided {@code key} from the storage.
     *
     * @param key key of the value
     * @param cls target type of the value
     * @return value from the storage or {@code null} when provided {@code key} was not found
     * @param <T> target type of the value
     */
    <T> T get(String key, Class<T> cls);

    /**
     * Retrieve value provided {@code key} from the storage or {@code defaultValue} when storage
     * does not contain provided {@code key}.
     *
     * @param key key of the value
     * @param cls target type of the value
     * @param defaultValue value to return when provided {@code key} was not found
     * @return value from the storage or {@code null} when no such value is stored
     * @param <T> target type of the value
     */
    <T> T getOrDefault(String key, Class<T> cls, T defaultValue);

    /**
     * Store value under provided {@code key} in the storage.
     * Value will not be stored when already exists.
     *
     * @param key key of the value
     * @param value value to store
     */
    void put(String key, Object value);

    /**
     * Store value under provided {@code key} in the storage.
     * Value will replace stored value nder provided {@code key} when already exists.
     *
     * @param key key of the value
     * @param value value to store
     */
    void putOrReplace(String key, Object value);

}
