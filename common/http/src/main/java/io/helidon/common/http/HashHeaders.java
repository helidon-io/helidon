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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

/**
 * A {@link Map}-based {@link Headers} implementation with case-insensitive keys and immutable {@link List} of values that
 * needs to be copied on each write.
 */
public class HashHeaders extends HashParameters implements Headers {

    private static final Supplier<ConcurrentMap<String, List<String>>> CASE_SENSITIVE_MAP_FACTORY =
            () -> new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Creates a new empty instance.
     */
    protected HashHeaders() {
        super(CASE_SENSITIVE_MAP_FACTORY);
    }

    /**
     * Creates a new instance populated from the contents of the provided multi-map.
     *
     * @param initialContent multi-map containing the initial contents to populate the new instance
     */
    protected HashHeaders(Map<String, List<String>> initialContent) {
        super(initialContent, CASE_SENSITIVE_MAP_FACTORY);
    }

    /**
     * Creates a new instance populated from the contents of the provided {@code Parameters}.
     * @param initialContent {@code Parameters} containing the initial contents to populate the new instance
     */
    protected HashHeaders(Parameters initialContent) {
        super(initialContent, CASE_SENSITIVE_MAP_FACTORY);
    }

    /**
     * Creates a new, empty instance.
     *
     * @return empty instance
     */
    public static HashHeaders create() {
        return new HashHeaders();
    }

    /**
     * Creates a new instance populated with the specified multi-map's contents.
     *
     * @param initialContent multi-map containing initial contents for the new instance
     * @return new instance filled with a deep copy of the initial contents
     */
    public static HashHeaders create(Map<String, List<String>> initialContent) {
        return new HashHeaders(initialContent);
    }

    /**
     * Creates a new instance populated with the specified {@code Parameters} contents.
     *
     * @param initialContent {@code Parameters} containing initial contents for the new instance
     * @return new instance filled with the names and values of the specified initial contents
     */
    public static HashHeaders create(Parameters initialContent) {
        return new HashHeaders(initialContent);
    }

    /**
     * Concatenates the contents of the specified {@code Parameters} into a new {@code HashHeaders} instance.
     *
     * @param parameters zero or more {@code Parameters} instances
     * @return new {@code HashHeaders} containing the names and values from the specified initial parameters
     */
    public static HashHeaders concat(Parameters... parameters) {
        return concat(HashHeaders::new, HashHeaders::new, parameters);
    }

    /**
     * Concatenates the specified contents into a new {@code HashHeaders} instance.
     *
     * @param parameters zero or more {@code Parameters} instances
     * @return new {@code HashHeaders} containint the names and values from the specified initial content
     */
    public static HashHeaders concat(Iterable<Parameters> parameters) {
        List<Map<String, List<String>>> hdrs = new ArrayList<>();
        for (Parameters p : parameters) {
            if (p != null) {
                hdrs.add(p.toMap());
            }
        }
        return concat(hdrs, HashHeaders::new, HashHeaders::new);
    }

    @Override
    public boolean equals(Object o) {
        return equals(o, HashHeaders.class);
    }

    @Override
    public int hashCode() {
        return hashCode(HashHeaders.class.hashCode());
    }
}
