/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.function.Function;

import static io.helidon.common.http.ReadOnlyParameters.copyMultimapAsImmutable;

/**
 * An immutable implementation of {@link Headers}.
 *
 * @see Headers
 */
public class ReadOnlyHeaders extends HashHeaders {

    private static final ReadOnlyHeaders EMPTY = new ReadOnlyHeaders((Headers) null);

    /**
     * Creates an instance from provided multi-map.
     *
     * @param data multi-map data to copy.
     */
    public ReadOnlyHeaders(Map<String, List<String>> data) {
        super(copyMultimapAsImmutable(data));
    }

    /**
     * Creates an instance from provided {@code Headers}.
     *
     * @param headers headers to copy.
     */
    public ReadOnlyHeaders(Headers headers) {
        this(headers == null ? null : headers.toMap());
    }

    /**
     * Returns empty and immutable singleton.
     *
     * @return the headers singleton instance which is empty and immutable.
     */
    public static ReadOnlyHeaders empty() {
        return EMPTY;
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
    public ReadOnlyHeaders putAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyHeaders add(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyHeaders add(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReadOnlyHeaders addAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> remove(String key) {
        throw new UnsupportedOperationException();
    }


}
