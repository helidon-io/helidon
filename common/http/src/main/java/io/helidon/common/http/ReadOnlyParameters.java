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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An immutable implementation of {@link Parameters}.
 *
 * @see Parameters
 */
public class ReadOnlyParameters implements Parameters {

    /**
     * Empty, immutable parameters.
     */
    private static final ReadOnlyParameters EMPTY = new ReadOnlyParameters((Parameters) null);

    private final Map<String, List<String>> data;

    /**
     * Creates an instance from provided multi-map.
     *
     * @param data multi-map data to copy.
     */
    public ReadOnlyParameters(Map<String, List<String>> data) {
        this(data == null ? Collections.emptySet() : data.entrySet());
    }

    /**
     * Creates an instance from provided multi-map.
     *
     * @param parameters parameters to copy.
     */
    public ReadOnlyParameters(Parameters parameters) {
        this((Iterable<Map.Entry<String, List<String>>>) parameters);
    }

    protected ReadOnlyParameters(Iterable<Map.Entry<String, List<String>>> data) {
        this.data = copyMultimapAsImmutable(data, this::emptyMap);
    }

    /**
     * Returns empty and immutable singleton.
     *
     * @return the parameters singleton instance which is empty and immutable.
     */
    public static ReadOnlyParameters empty() {
        return EMPTY;
    }

    /**
     * Returns a deep copy of provided multi-map which is completely unmodifiable.
     *
     * @param data data to copy, if {@code null} then returns empty map.
     * @return unmodifiable map, never {@code null}.
     */
    static Map<String, List<String>> copyMultimapAsImutable(Map<String, List<String>> data) {
        return copyMultimapAsImmutable(data == null ? null : data.entrySet(),
                                       ReadOnlyParameters::createEmptyMap);
    }

    /**
     * Returns a deep copy of provided data which is completely unmodifiable.
     *
     * @param data data to copy, if {@code null} then returns empty map.
     * @return unmodifiable map, never {@code null}.
     */
    static Map<String, List<String>> copyMultimapAsImmutable(Iterable<Map.Entry<String, List<String>>> data,
                                                             Supplier<? extends Map<String, List<String>>> mapFactory) {

        if (data == null) {
            return Collections.emptyMap();
        }

        Iterator<Map.Entry<String, List<String>>> entries = data.iterator();

        if (!entries.hasNext()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> h = mapFactory.get();
        while (entries.hasNext()) {
            Map.Entry<String, List<String>> entry = entries.next();
            if (entry != null) {
                h.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
        }

        return Collections.unmodifiableMap(h);
    }

    @Override
    public Optional<String> first(String name) {
        return Optional.ofNullable(data.get(name)).map(l ->
                !l.isEmpty() ? l.get(0) : null);
    }

    @Override
    public List<String> all(String name) {
        return Optional.ofNullable(data.get(name)).orElse(Collections.emptyList());
    }

    @Override
    public List<String> put(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyParameters putAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyParameters add(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyParameters add(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyParameters addAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> remove(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, List<String>> toMap() {
        Map<String, List<String>> h = emptyMapForCopy();
        data.forEach((k, v) -> h.put(k, new ArrayList<>(v)));
        return h;
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return data.entrySet().iterator();
    }

    /**
     * Creates an empty {@code Map} suitable (once populated) for read-only access.
     *
     * @return empty {@code Map}
     */
    protected Map<String, List<String>> emptyMap() {
        return createEmptyMap();
    }

    /**
     * Creates an empty {@code Map} suitable (once populated) for read-only access, pre-sized as specified.
     *
     * @return empty {@code Map}, possibly pre-sized as indicated
     */
    protected Map<String, List<String>> emptyMapForCopy() {
        return new HashMap<>(data.size());
    }

    private static Map<String, List<String>> createEmptyMap() {
        return new HashMap<>();
    }
}
