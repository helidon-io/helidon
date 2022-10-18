/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
import java.util.stream.Collectors;

/**
 * {@code Headers} represents {@code key : value} pairs where {@code key} is a {@code String} with potentially multiple values.
 * Look-ups use case-insensitive matching.
 * <p>
 * This structure represents headers in e.g. {@link HttpRequest}.
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
public interface Headers {

    /**
     * Returns an unmodifiable view.
     *
     * @param headers a {@code Headers} instance to be accessed in a read-only way
     * @return An unmodifiable view.
     * @throws NullPointerException if parameter {@code headers} is null
     */
    static Headers toUnmodifiableHeaders(Headers headers) {
        Objects.requireNonNull(headers, "Parameter 'headers' is null!");
        return new ReadOnlyHeaders(headers);
    }

    /**
     * Returns an unmodifiable view.
     *
     * @param parameters a parameters for unmodifiable view.
     * @return An unmodifiable view.
     * @throws NullPointerException if parameter {@code parameters} is null.
     * @deprecated Use {@link #toUnmodifiableHeaders(Headers)} instead
     */
    @Deprecated(since = "3.0.2", forRemoval = true)
    static Parameters toUnmodifiableParameters(Parameters parameters) {
        Objects.requireNonNull(parameters, "Parameter 'parameters' is null!");
        return new UnmodifiableParameters(parameters);
    }

    /**
     * Returns an {@link Optional} containing the first value of the given
     * header (and possibly multi-valued) parameter. If the header is
     * not present, then the returned Optional is empty.
     *
     * @param name the header name
     * @return an {@code Optional<V>} for the first named value
     * @throws NullPointerException if name is {@code null}
     */
    Optional<String> first(String name);

    /**
     * Returns an unmodifiable {@link List} of all header fields - each element represents a value of a single header field
     * in the request. Consider to use {@link #value(String)} or {@link #values(String)} method instead.
     * <p>
     * Always returns a List, which may be empty if the parameter is not present.
     *
     * @param headerName the header name
     * @return a {@code List} of values with zero or greater size
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #value(String)
     * @see #values(String)
     */
    List<String> all(String headerName);

    /**
     * Associates specified values with the specified key (optional operation).
     * If the {@code Headers} previously contained a mapping for the key, the old values fully replaced.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key
     * @return the previous values associated with key, or empty {@code List} if there was no mapping for key.
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
     */
    List<String> put(String key, String... values);

    /**
     * Associates specified values with the specified key (optional operation).
     * If the {@code Headers} previously contained a mapping for the key, the old values fully replaced.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be associated with the specified key. If {@code null} then association will be removed.
     * @return the previous values associated with key, or empty {@code List} if there was no mapping for key.
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
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
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
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
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
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
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers)
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
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
     * @throws IllegalStateException         if the computation detectably
     *                                       attempts a recursive update to this map that would
     *                                       otherwise never complete
     * @throws RuntimeException              or Error if the mappingFunction does so,
     *                                       in which case the mapping is left unestablished
     */
    List<String> computeSingleIfAbsent(String key, Function<String, String> value);

    /**
     * Copies all of the mappings from the specified {@code headers} to this instance replacing values of existing associations
     * (optional operation).
     *
     * @param headers to copy.
     * @return this instance of {@link Headers}
     * @throws NullPointerException          if the specified {@code parameters} are null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
     */
    Headers putAll(Headers headers);

    /**
     * Adds specified values to association with the specified key (optional operation).
     * If headers doesn't contains mapping, new mapping is created.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be add to association with the specified key
     * @return this instance of {@link Headers}
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable headers).
     */
    Headers add(String key, String... values);

    /**
     * Adds specified values to association with the specified key (optional operation).
     * If headers doesn't contains mapping, new mapping is created.
     *
     * @param key    key with which the specified value is to be associated
     * @param values value to be add to association with the specified key. If {@code null} then noting will be add.
     * @return this instance of {@link Headers}
     * @throws NullPointerException          if the specified key is null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
     */
    Headers add(String key, Iterable<String> values);

    /**
     * Copies all of the mappings from the specified {@code headers} to this instance adding values to existing associations
     * (optional operation).
     *
     * @param headers to copy.
     * @return this instance of {@link Headers}
     * @throws NullPointerException          if the specified {@code parameters} are null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
     */
    Headers addAll(Headers headers);

    /**
     * Removes the mapping for a key if it is present (optional operation).
     *
     * @param key key whose mapping is to be removed.
     * @return the previous value associated with key, or empty {@code List}.
     */
    List<String> remove(String key);

    /**
     * Returns a copy of headers as a Map. This
     * interface should only be used when it is required to iterate over the
     * entire set of headers.
     *
     * @return the {@code Map}
     */
    Map<String, List<String>> toMap();

    /**
     * Returns a header value as a single {@link String} potentially concatenated using comma character
     * from {@link #all(String) all} header fields.
     * <p>
     * Accordingly to <a href="https://tools.ietf.org/html/rfc2616#section-4.2">RFC2616, Message Headers</a>:
     * <blockquote>
     * Multiple message-header fields with the same field-name MAY be
     * present in a message if and only if the entire field-value for that
     * header field is defined as a comma-separated list [i.e., #(values)].
     * It MUST be possible to combine the multiple header fields into one
     * "field-name: field-value" pair, without changing the semantics of the
     * message, by appending each subsequent field-value to the first, each
     * separated by a comma.
     * </blockquote>
     *
     * @param headerName the header name
     * @return all header values concatenated using comma separator
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #all(String)
     * @see #values(String)
     */
    default Optional<String> value(String headerName) {
        List<String> hdrs = all(headerName);
        if (hdrs.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(hdrs.stream().collect(Collectors.joining(",")));
        }
    }

    /**
     * Returns an unmodifiable {@link List} of all comma separated header value parts - <b>Such segmentation is NOT valid for
     * all header semantics, however it is very common</b>. Refer to actual header semantics standard/description before use.
     * <p>
     * Result is composed from all header fields with requested {@code headerName} where each header value is tokenized by
     * a comma character. Tokenization respects value quoting by <i>double-quote</i> character.
     * <p>
     * Always returns a List, which may be empty if the parameter is not present.
     *
     * @param headerName the header name
     * @return a {@code List} of values with zero or greater size, never {@code null}
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #all(String)
     * @see #value(String)
     */
    default List<String> values(String headerName) {
        return all(headerName).stream()
                .flatMap(val -> Utils.tokenize(',', "\"", true, val).stream())
                .collect(Collectors.toList());
    }

    /**
     * Copies all the parameters into the current {@code Headers} instance.
     *
     * @param parameters to copy.
     * @return this instance of {@link Headers}
     * @throws NullPointerException if the specified {@code parameters} are null
     * @throws UnsupportedOperationException if the {@code putAll} operation is not supported (unmodifiable Headers).
     * @deprecated Use {@link #putAll(Headers)} instead.
     */
    @Deprecated(since = "3.0.2", forRemoval = true)
    Headers putAll(Parameters parameters);

    /**
     * Copies all of the mappings from the specified {@code parameters} to this instance adding values to existing associations
     * (optional operation).
     *
     * @param parameters to copy.
     * @return this instance of {@link Headers}
     * @throws NullPointerException          if the specified {@code parameters} are null.
     * @throws UnsupportedOperationException if put operation is not supported (unmodifiable Headers).
     * @deprecated Use {@link #addAll(Headers)} instead.
     */
    @Deprecated(since = "3.0.2", forRemoval = true)
    Headers addAll(Parameters parameters);
}
