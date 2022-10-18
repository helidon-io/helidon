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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

/**
 * A {@link java.util.concurrent.ConcurrentSkipListMap} based {@link Headers} implementation with
 * case-insensitive keys and immutable {@link java.util.List} of values that needs to be copied on each write.
 */
public class HashHeaders implements Headers {
    private static final List<String> EMPTY_STRING_LIST = Collections.emptyList();

    private final ConcurrentMap<String, List<String>> content;

    /**
     * Creates a new instance.
     */
    protected HashHeaders() {
        this((Headers) null);
    }

    /**
     * Creates a new instance from provided data.
     * Initial data are copied.
     *
     * @param initialContent initial content.
     */
    protected HashHeaders(Map<String, List<String>> initialContent) {
        if (initialContent == null) {
            content = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
        } else {
            content = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, List<String>> entry : initialContent.entrySet()) {
                content.compute(
                        entry.getKey(),
                        (key, values) -> {
                            if (values == null) {
                                return Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
                            } else {
                                values.addAll(entry.getValue());
                                return values;

                            }
                        }
                );
            }
        }
    }

    /**
     * Creates a new instance from provided data.
     * Initial data is copied.
     *
     * @param initialContent initial content.
     */
    protected HashHeaders(Headers initialContent) {
        this(initialContent == null ? null : initialContent.toMap());
    }

    /**
     * Creates a new empty instance {@link HashHeaders}.
     *
     * @return a new instance of {@link HashHeaders}.
     */
    public static HashHeaders create() {
        return new HashHeaders();
    }

    /**
     * Creates a new instance {@link HashHeaders} from provided data. Initial data is copied.
     *
     * @param initialContent initial content.
     * @return a new instance of {@link HashHeaders} initialized with the given content.
     */
    public static HashHeaders create(Map<String, List<String>> initialContent) {
        return new HashHeaders(initialContent);
    }

    /**
     * Creates a new instance {@link HashHeaders} from provided data. Initial data is copied.
     *
     * @param initialContent initial content.
     * @return a new instance of {@link HashHeaders} initialized with the given content.
     */
    public static HashHeaders create(Headers initialContent) {
        return new HashHeaders(initialContent);
    }

    /**
     * Creates new instance of {@link HashHeaders} as a concatenation of provided headers.
     * Values for keys found across the provided headers are "concatenated" into a {@link List} entry for their respective key
     * in the created {@link HashHeaders} instance.
     *
     * @param headers headers to concatenate.
     * @return a new instance of {@link HashHeaders} that represents the concatenation of the provided headers.
     */
    public static HashHeaders concat(Headers... headers) {
        if (headers == null || headers.length == 0) {
            return new HashHeaders();
        }
        List<Map<String, List<String>>> hdrs = new ArrayList<>(headers.length);
        for (Headers h : headers) {
            if (h != null) {
                hdrs.add(h.toMap());
            }
        }
        return concat(hdrs);
    }

    /**
     * Creates new instance of {@link HashHeaders} as a concatenation of provided headers.
     * Values for keys found across the provided headers are "concatenated" into a {@link List} entry for their respective key
     * in the created {@link HashHeaders} instance.
     *
     * @param headers headers to concatenate.
     * @return a new instance of {@link HashHeaders} that represents the concatenation of the provided headers.
     */
    public static HashHeaders concat(Iterable<Headers> headers) {
        ArrayList<Map<String, List<String>>> hdrs = new ArrayList<>();
        for (Headers h : headers) {
            if (h != null) {
                hdrs.add(h.toMap());
            }
        }
        return concat(hdrs);
    }

    private static HashHeaders concat(List<Map<String, List<String>>> hdrs) {
        if (hdrs.isEmpty()) {
            return new HashHeaders();
        }
        if (hdrs.size() == 1) {
            return new HashHeaders(hdrs.get(0));
        }

        Map<String, List<String>> composer = new HashMap<>();
        for (Map<String, List<String>> hdr : hdrs) {
            for (Map.Entry<String, List<String>> entry : hdr.entrySet()) {
                List<String> strings = composer.computeIfAbsent(entry.getKey(), k -> new ArrayList<>(entry.getValue().size()));
                strings.addAll(entry.getValue());
            }
        }
        return new HashHeaders(composer);
    }

    private List<String> internalListCopy(String... values) {
        return Optional.ofNullable(values)
                .map(Arrays::asList)
                .filter(l -> !l.isEmpty())
                .map(Collections::unmodifiableList)
                .orElse(null);
    }

    private List<String> internalListCopy(Iterable<String> values) {
        if (values == null) {
            return null;
        } else {
            List<String> result;
            if (values instanceof Collection) {
                result = new ArrayList<>((Collection<String>) values);
            } else {
                result = new ArrayList<>();
                for (String value : values) {
                    result.add(value);
                }
            }
            if (result.isEmpty()) {
                return null;
            } else {
                return Collections.unmodifiableList(result);
            }
        }
    }

    @Override
    public Optional<String> first(String name) {
        Objects.requireNonNull(name, "Header 'name' is null!");
        return content.getOrDefault(name, EMPTY_STRING_LIST).stream().findFirst();
    }

    @Override
    public List<String> all(String name) {
        Objects.requireNonNull(name, "Header 'name' is null!");
        return content.getOrDefault(name, EMPTY_STRING_LIST);
    }

    @Override
    public List<String> put(String key, String... values) {
        List<String> vs = internalListCopy(values);
        List<String> result;
        if (vs == null) {
            result = content.remove(key);
        } else {
            result = content.put(key, vs);
        }
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        List<String> vs = internalListCopy(values);
        List<String> result;
        if (vs == null) {
            result = content.remove(key);
        } else {
            result = content.put(key, vs);
        }
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        List<String> vls = internalListCopy(values);
        List<String> result;
        if (vls != null) {
            result = content.putIfAbsent(key, vls);
        } else {
            result = content.get(key);
        }
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        List<String> vls = internalListCopy(values);
        List<String> result;
        if (vls != null) {
            result = content.putIfAbsent(key, vls);
        } else {
            result = content.get(key);
        }
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values) {
        List<String> result = content.computeIfAbsent(key, k -> internalListCopy(values.apply(k)));
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        List<String> result = content.computeIfAbsent(key, k -> {
            String v = value.apply(k);
            if (v == null) {
                return null;
            } else {
                return Collections.singletonList(v);
            }
        });
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public HashHeaders putAll(Headers headers) {
        if (headers == null) {
            return this;
        }

        for (Map.Entry<String, List<String>> entry : headers.toMap().entrySet()) {
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                content.put(entry.getKey(), Collections.unmodifiableList(values));
            }
        }
        return this;
    }

    @Override
    public Headers putAll(Parameters parameters) {
        if (parameters == null) {
            return this;
        }

        for (Map.Entry<String, List<String>> entry : parameters.toMap().entrySet()) {
            List<String> values  = entry.getValue();
            if (values != null && !values.isEmpty()) {
                content.put(entry.getKey(), Collections.unmodifiableList(values));
            }
        }
        return this;
    }

    @Override
    public HashHeaders add(String key, String... values) {
        Objects.requireNonNull(key, "Header 'key' is null!");
        if (values == null || values.length == 0) {
            // do not necessarily create an entry in the map, simply immediately return
            return this;
        }

        content.compute(key, (s, list) -> {
            if (list == null) {
                return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
            } else {
                ArrayList<String> newValues = new ArrayList<>(list.size() + values.length);
                newValues.addAll(list);
                newValues.addAll(Arrays.asList(values));
                return Collections.unmodifiableList(newValues);
            }
        });
        return this;
    }

    @Override
    public HashHeaders add(String key, Iterable<String> values) {
        Objects.requireNonNull(key, "Parameter 'key' is null!");
        List<String> vls = internalListCopy(values);
        if (vls == null) {
            // do not necessarily create an entry in the map, simply immediately return
            return this;
        }

        content.compute(key, (s, list) -> {
            if (list == null) {
                return Collections.unmodifiableList(vls);
            } else {
                ArrayList<String> newValues = new ArrayList<>(list.size() + vls.size());
                newValues.addAll(list);
                newValues.addAll(vls);
                return Collections.unmodifiableList(newValues);
            }
        });
        return this;
    }

    @Override
    public Headers addAll(Headers headers) {
        if (headers == null) {
            return this;
        }
        for (Map.Entry<String, List<String>> entry : headers.toMap().entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public HashHeaders addAll(Parameters parameters) {
        if (parameters == null) {
            return this;
        }
        Map<String, List<String>> map = parameters.toMap();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public List<String> remove(String key) {
        List<String> result = content.remove(key);
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public Map<String, List<String>> toMap() {
        // deep copy
        Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> entry : content.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    @Override
    public String toString() {
        return content.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HashHeaders)) {
            return false;
        }
        HashHeaders that = (HashHeaders) o;
        return content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return content.hashCode();
    }
}
