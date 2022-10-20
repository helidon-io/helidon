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
import java.util.TreeMap;

/**
 * An immutable implementation of {@link Headers}.
 * <p>
 *     Factories for multi-maps passed to superclass constructors use {@code TreeSet} instead of a concurrency-tolerant
 *     implementation because this is a read-only implementation.
 * </p>
 *
 *
 * @see Headers
 */
public class ReadOnlyHeaders extends ReadOnlyParameters implements Headers {

    private static final ReadOnlyHeaders EMPTY = new ReadOnlyHeaders((Parameters) null);

    /**
     * Returns an empty and immutable singleton.
     *
     * @return the headers singleton (empty and immutable)
     */
    public static ReadOnlyHeaders empty() {
        return EMPTY;
    }

    /**
     * Creates a new {@code ReadOnlyHeaders} instance with the specified multi-map contents as initial contents.
     *
     * @param initialContent multi-map contains name/values-list pairs for the initial content
     * @return new instance with the specified initial content
     */
    protected static ReadOnlyHeaders create(Map<String, List<String>> initialContent) {
        return new ReadOnlyHeaders(initialContent);
    }

    /**
     * Creates a new instance populated with the specified {@code Parameters} settings.
     *
     * @param initialContent {@code Parameters} to be used as the initial content for the new instance
     * @return new instance with specified initial content
     * @deprecated Use
     */
    public static ReadOnlyHeaders create(Parameters initialContent) {
        return new ReadOnlyHeaders(initialContent);
    }

    /**
     * Returns an immutable deep copy of the provided multimap.
     *
     * @param data multi-map data to copy.
     */
    protected ReadOnlyHeaders(Map<String, List<String>> data) {
        super(data);
    }

    /**
     * Returns an immutable deep copy of the provided {@link Parameters} instance.
     *
     * @param parameters parameters to copy.
     */
    protected ReadOnlyHeaders(Parameters parameters) {
        super(parameters);
    }

    @Override
    protected Map<String, List<String>> emptyMap() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    protected Map<String, List<String>> emptyMapForCopy() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
}
