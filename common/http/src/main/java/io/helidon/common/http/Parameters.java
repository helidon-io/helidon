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

package io.helidon.common.http;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Parameters represents {@code key : value} pairs where {@code key} is a {@code String} with potentially multiple values.
 * <p>
 * This structure represents query parameters, headers and path parameters in e.g. {@link HttpRequest}.
 * <p>
 * Interface focus on most convenient use cases in HTTP Request and Response processing, like
 * <pre>
 * {@code
 * // Get and map with default
 * .first("count").map(Integer::new).orElse(0);
 * // Find max in multiple values
 * .all("counts").stream().mapToInt(Integer::valueOf).max().orElse(0);
 * }
 * </pre>
 * <p>
 * Mutable operations are defined in two forms:
 * <ul>
 * <li>{@code put...} create or replace association.</li>
 * <li>{@code add...} create association or add values to existing association.</li>
 * </ul>
 * <p>
 * It is possible to use {@link #toMap()} method to get immutable map view of data.
 * <p>
 * Various static factory methods can be used to create common implementations.
 */
public interface Parameters {

    /**
     * Returns an unmodifiable view.
     *
     * @param parameters a parameters for unmodifiable view.
     * @return An unmodifiable view.
     * @throws NullPointerException if parameter {@code parameters} is null.
     */
    static Parameters toUnmodifiableParameters(Parameters parameters) {
        Objects.requireNonNull(parameters, "Parameter 'parameters' is null!");
        return new UnmodifiableParameters(parameters);
    }

    /**
     * Returns an {@link Optional} containing the first value of the given
     * parameter (and possibly multi-valued) parameter. If the parameter is
     * not present, then the returned Optional is empty.
     *
     * @param name the parameter name
     * @return an {@code Optional<V>} for the first named value
     * @throws NullPointerException if name is {@code null}
     */
    Optional<String> first(String name);

    /**
     * Returns an unmodifiable List of all of the values of the given named
     * parameter. Always returns a List, which may be empty if the parameter
     * is not present.
     *
     * @param name the parameter name
     * @return a {@code List} of values with zero or greater size
     * @throws NullPointerException if name is {@code null}
     */
    List<String> all(String name);

    /**
     * Associates specified values with the specified key (optional operation).
     * If parameters previously contained a mapping for the key, the old values fully replaced.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key
     * @return the previous values associated with key, or empty {@code List} if there was no mapping for key.
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    List<String> put(String key, String... values);

    /**
     * Associates specified values with the specified key (optional operation).
     * If parameters previously contained a mapping for the key, the old values fully replaced.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key. If {@code null} then association will be removed.
     * @return the previous values associated with key, or empty {@code List} if there was no mapping for key.
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    List<String> put(String key, Iterable<String> values);

    /**
     * If the specified key is not already associated with a value associates it with the given value and returns empty
     * {@code List}, else returns the current value  (optional operation).
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key
     * @return the previous values associated with key, or empty {@code List} if there was no mapping for key.
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    List<String> putIfAbsent(String key, String... values);

    /**
     * If the specified key is not already associated with a value associates it with the given value and returns empty
     * {@code List}, else returns the current value  (optional operation).
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key
     * @return the previous values associated with key, or empty {@code List} if there was no mapping for key.
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    List<String> putIfAbsent(String key, Iterable<String> values);

    /**
     * If the specified key is not already associated with a value computes new association using the given function and returns
     * empty {@code List}, else returns the current value  (optional operation).
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key
     * @return the current (potentially computed) values associated with key,
     * or empty {@code List} if function returns {@code null}
     * @throws NullPointerException          if the specified key is null
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters)
     * @throws IllegalStateException         if the computation detectably
     *                                       attempts a recursive update to this map that would
     *                                       otherwise never complete
     * @throws RuntimeException              or Error if the mappingFunction does so,
     *                                       in which case the mapping is left unestablished
     */
    List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values);

    /**
     * If the specified key is not already associated with a value computes new association using the given function and returns
     * empty {@code List}, else returns the current value  (optional operation).
     *
     * @param key   a key with which the specified value is to be associated
     * @param value a single value to be associated with the specified key
     * @return the current (potentially computed) values associated with key,
     * or empty {@code List} if function returns {@code null}
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     * @throws IllegalStateException         if the computation detectably
     *                                       attempts a recursive update to this map that would
     *                                       otherwise never complete
     * @throws RuntimeException              or Error if the mappingFunction does so,
     *                                       in which case the mapping is left unestablished
     */
    List<String> computeSingleIfAbsent(String key, Function<String, String> value);

    /**
     * Copies all of the mappings from the specified {@code parameters} to this instance replacing values of existing associations
     * (optional operation).
     *
     * @param parameters to copy.
     * @return this instance of {@link Parameters}
     * @throws NullPointerException          if the specified {@code parameters} are null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    Parameters putAll(Parameters parameters);

    /**
     * Adds specified values tu association with the specified key (optional operation).
     * If parameters doesn't contains mapping, new mapping is created.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be add to association with the specified key
     * @return this instance of {@link Parameters}
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    Parameters add(String key, String... values);

    /**
     * Adds specified values tu association with the specified key (optional operation).
     * If parameters doesn't contains mapping, new mapping is created.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be add to association with the specified key. If {@code null} then noting will be add.
     * @return this instance of {@link Parameters}
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    Parameters add(String key, Iterable<String> values);

    /**
     * Copies all of the mappings from the specified {@code parameters} to this instance adding values to existing associations
     * (optional operation).
     *
     * @param parameters to copy.
     * @return this instance of {@link Parameters}
     * @throws NullPointerException          if the specified {@code parameters} are null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Parameters).
     */
    Parameters addAll(Parameters parameters);

    /**
     * Removes the mapping for a key if it is present (optional operation).
     *
     * @param key key whose mapping is to be removed.
     * @return the previous value associated with key, or empty {@code List}.
     */
    List<String> remove(String key);

    /**
     * Returns a copy of parameters as a Map. This
     * interface should only be used when it is required to iterate over the
     * entire set of parameters.
     *
     * @return the {@code Map}
     */
    Map<String, List<String>> toMap();
}
