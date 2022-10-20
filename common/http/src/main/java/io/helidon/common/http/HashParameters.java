/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Map}-based {@link Parameters} implementation with keys and immutable {@link List} of values that needs to be copied
 * on each write.
 * <p>
 * By default, this implementation uses case-sensitive keys but a subclass can override the map factory methods
 * {@link #emptyMapForReads()} and {@link #emptyMapForUpdates()} to control the specific type of map used.
 * </p>
 */
public class HashParameters implements Parameters {

    private static final List<String> EMPTY_STRING_LIST = Collections.emptyList();

    private final ConcurrentMap<String, List<String>> content;

    /**
     * Creates a new instance.
     */
    protected HashParameters() {
        content = emptyMapForUpdates();
    }

    /**
     * Creates a new instance from provided data.
     * Initial data are copied.
     *
     * @param initialContent initial content.
     */
    protected HashParameters(Map<String, List<String>> initialContent) {
        this(initialContent == null ? null : initialContent.entrySet());
    }

    /**
     * Creates a new instance from provided data, typically either another {@code Parameters} instance or a map's entry set.
     * Initial data are copied.
     *
     * @param initialContent initial content
     */
    protected HashParameters(Iterable<Map.Entry<String, List<String>>> initialContent) {
        this();
        if (initialContent != null) {
            initialContent.forEach(entry ->
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
                ));
        }
    }

    /**
     * Creates a new instance from provided data.
     * Initial data is copied.
     *
     * @param initialContent initial content.
     */
    protected HashParameters(Parameters initialContent) {
        this((Iterable<Map.Entry<String, List<String>>>) initialContent);
    }

    /**
     * Creates a new empty instance {@link HashParameters}.
     *
     * @return a new instance of {@link HashParameters}.
     */
    public static HashParameters create() {
        return new HashParameters();
    }

    /**
     * Creates a new instance of {@link HashParameters} from a single provided Map. Initial data is copied.
     *
     * @param initialContent initial content.
     * @return a new instance of {@link HashParameters} initialized with the given content.
     */
    public static HashParameters create(Map<String, List<String>> initialContent) {
        return new HashParameters(initialContent == null ? Collections.emptySet() : initialContent.entrySet());
    }

    /**
     * Creates a new instance {@link HashParameters} from provided data. Initial data is copied.
     *
     * @param initialContent initial content.
     * @return a new instance of {@link HashParameters} initialized with the given content.
     */
    public static HashParameters create(Parameters initialContent) {
        return new HashParameters(initialContent);
    }

    /**
     * Creates a new instance of {@link HashParameters} from a single provided Parameter or Map. Initial data is copied.
     *
     * @param initialContent initial content.
     * @return a new instance of {@link HashParameters} initialized with the given content.
     */
    public static HashParameters create(Iterable<Map.Entry<String, List<String>>> initialContent) {
        return new HashParameters(initialContent);
    }

    /**
     * Creates new instance of {@link HashParameters} as a concatenation of provided parameters.
     * Values for keys found across the provided parameters are "concatenated" into a {@link List} entry for their respective key
     * in the created {@link HashParameters} instance.
     *
     * @param parameters parameters to concatenate.
     * @return a new instance of {@link HashParameters} that represents the concatenation of the provided parameters.
     */
    public static HashParameters concat(Parameters... parameters) {
        return concat(new ArrayIterable<>(parameters));
    }

    /**
     * Creates new instance of {@link HashParameters} as a concatenation of provided parameters.
     * Values for keys found across the provided parameters are "concatenated" into a {@link List} entry for their respective key
     * in the created {@link HashParameters} instance.
     *
     * @param parameters parameters to concatenate.
     * @return a new instance of {@link HashParameters} that represents the concatenation of the provided parameters.
     */
    public static HashParameters concat(Iterable<Parameters> parameters) {
        return concat(parameters, HashParameters::new, HashParameters::new);
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return content.entrySet().iterator();
    }

    /**
     * Creates a new instance of the correct {@link HashParameters} subclass as a concatenation of the provided sources.
     * Values for keys found across the sources are "concatenated" into a {@link List} entry for their respective key
     * in the created {@link HashParameters} (or subclass) instance.
     *
     * @param contentSources {@code Iterable} over the sources whose contents are to be combined
     * @param emptyFactory factory for an empty named value lists implementation
     * @param singletonFactory factory for a named value lists implementation preloaded with one source's data
     * @return new named value lists implementation containing the combined data from the sources
     * @param <T> type of the named value lists implementation to create
     */
    protected static <T extends HashParameters> T concat(
            Iterable<? extends Iterable<Map.Entry<String, List<String>>>> contentSources,
            Supplier<T> emptyFactory,
            Function<Iterable<Map.Entry<String, List<String>>>, T> singletonFactory) {

        Iterator<? extends Iterable<Map.Entry<String, List<String>>>> sources = contentSources.iterator();
        if (!sources.hasNext()) {
            return emptyFactory.get();
        }
        Iterable<Map.Entry<String, List<String>>> source = sources.next();
        if (!sources.hasNext()) {
            return singletonFactory.apply(source);
        }

        Map<String, List<String>> composer = new HashMap<>(); // source was initialized above
        do {
            if (source != null) {
                source.forEach(entry -> {
                    List<String> strings = composer.computeIfAbsent(entry.getKey(),
                                                                    k -> new ArrayList<>(entry.getValue().size()));
                    strings.addAll(entry.getValue());
                });
            }
            if (!sources.hasNext()) {
                break;
            }
            source = sources.next();
        } while (true);

        return singletonFactory.apply(composer.entrySet());
    }

    /**
     * Returns an empty {@code Map} suitable for case-sensitivity or case-insensitivity and
     * for read-write access.
     * <p>
     *     Typical implementations should return a concurrent implementation.
     * </p>
     *
     * @return empty {@code Map} implementation with correct case-sensitivity behavior and suitable for read-write access
     */
    protected ConcurrentMap<String, List<String>> emptyMapForUpdates() {
        return new ConcurrentSkipListMap<>();
    }

    /**
     * Returns an empty {@code Map} suitable for case-sensitivity or case-insensitivity and optimized for read-only access.
     * <p>
     *     Typical implementations should not return a concurrent implementation.
     * </p>
     *
     * @return empty {@code Map} implementation with correct case-sensitivity behavior and optimized for read-only access
     */
    protected Map<String, List<String>> emptyMapForReads() {
        return new HashMap<>();
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
        Objects.requireNonNull(name, "Parameter 'name' is null!");
        return content.getOrDefault(name, EMPTY_STRING_LIST).stream().findFirst();
    }

    @Override
    public List<String> all(String name) {
        Objects.requireNonNull(name, "Parameter 'name' is null!");
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
    public HashParameters putAll(Parameters parameters) {
        if (parameters == null) {
            return this;
        }

        for (Map.Entry<String, List<String>> entry : parameters.toMap().entrySet()) {
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                content.put(entry.getKey(), Collections.unmodifiableList(values));
            }
        }
        return this;
    }

    @Override
    public HashParameters add(String key, String... values) {
        Objects.requireNonNull(key, "Parameter 'key' is null!");
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
    public HashParameters add(String key, Iterable<String> values) {
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
    public HashParameters addAll(Parameters parameters) {
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
        Map<String, List<String>> result = emptyMapForReads();
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HashParameters that = (HashParameters) o;
        return content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return 37 * getClass().hashCode() + content.hashCode();
    }

    /**
     * {@code Iterable} around an array (to avoid uses of {@code Array.asList}) to get an {@code Iterable}.
     *
     * @param <T> type of the array elements
     */
    protected static class ArrayIterable<T> implements Iterable<T> {

        private final T[] content;

        /**
         * Creates a new instance using the specified content.
         *
         * @param content the array over which the {@code Iterable} traverses
         */
        protected ArrayIterable(T[] content) {
            this.content = content;
        }

        @Override
        public Iterator<T> iterator() {
            return content == null ? Collections.emptyIterator() : new Iterator<T>() {

                private int slot = 0;
                @Override
                public boolean hasNext() {
                    return slot < content.length;
                }

                @Override
                public T next() {
                    if (slot >= content.length) {
                        throw new NoSuchElementException();
                    }
                    return content[slot++];
                }
            };
        }
    }
}
