/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

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

    private final String name;
    private final Map<String, List<String>> data;

    /**
     * Creates an instance from provided multi-map.
     *
     * @param data multi-map data to copy.
     */
    public ReadOnlyParameters(Map<String, List<String>> data) {
        this("ReadOnly", data);
    }

    /**
     * Creates an instance from provided multi-map.
     *
     * @param name name of these parameters
     * @param data multi-map data to copy.
     */
    public ReadOnlyParameters(String name, Map<String, List<String>> data) {
        this.name = name;
        this.data = copyMultimapAsImutable(data);
    }

    /**
     * Creates an instance from provided multi-map.
     *
     * @param parameters parameters to copy.
     */
    public ReadOnlyParameters(Parameters parameters) {
        this(parameters == null ? null : parameters.toMap());
    }

    /**
     * Creates an instance from provided multi-map.
     *
     * @param name name of these parameters
     * @param parameters parameters to copy.
     */
    public ReadOnlyParameters(String name, Parameters parameters) {
        this(name, parameters == null ? null : parameters.toMap());
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
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        } else {
            // Deep copy
            Map<String, List<String>> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            data.forEach((k, v) -> h.put(k, Collections.unmodifiableList(new ArrayList<>(v))));
            return Collections.unmodifiableMap(h);
        }
    }

    @Override
    public String name() {
        return name;
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
    public void putAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> remove(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, List<String>> toMap() {
        Map<String, List<String>> h = new HashMap<>(data.size());
        data.forEach((k, v) -> h.put(k, new ArrayList<>(v)));
        return h;
    }
}
