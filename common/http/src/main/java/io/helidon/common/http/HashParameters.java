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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Map}-based {@link Parameters} implementation with keys and immutable {@link List} of values that needs to be copied
 * on each write. By default, this implementation uses case-sensitive keys and a {@code ConcurrentSkipListMap} but
 * a subclass can furnish its own map factory to control what specific type of map is used.
 */
public class HashParameters implements Parameters {

    private static final List<String> EMPTY_STRING_LIST = Collections.emptyList();

    private final Map<String, List<String>> content;

    private final Supplier<? extends Map<String, List<String>>> emptyMapFactory;

    /**
     * Creates a new instance.
     */
    protected HashParameters() {
        this((Parameters) null);
    }

    /**
     * Creates a new instance using the provided factory to create the map for holding the data.
     *
     * @param emptyMapFactory supplier of a new map instance
     */
    protected HashParameters(Supplier<? extends Map<String, List<String>>> emptyMapFactory) {
        this((Parameters) null, emptyMapFactory);
    }

    /**
     * Creates a new instance from provided data, using a map with case-sensitive keys.
     * Initial data are copied.
     *
     * @param initialContent initial content.
     */
    protected HashParameters(Map<String, List<String>> initialContent) {
        this(initialContent, ConcurrentSkipListMap::new);
    }

    /**
     * Creates a new instance from provided data and using the specified factory to instantiate a concurrent map to hold the data.
     * Initial data are copied.
     *
     * @param initialContent initial content
     * @param emptyMapFactory factory for an empty map to hold the data
     */
    protected HashParameters(Map<String, List<String>> initialContent, Supplier<? extends Map<String, List<String>>> emptyMapFactory) {
        this.emptyMapFactory = emptyMapFactory;
        if (initialContent == null) {
            content = emptyMapFactory.get();
        } else {
            content = emptyMapFactory.get();
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
    protected HashParameters(Parameters initialContent) {
        this(initialContent, ConcurrentSkipListMap::new);
    }

    protected HashParameters(Parameters initialContent, Supplier<? extends Map<String, List<String>>> emptyMapFactory) {
        this(initialContent == null ? null : initialContent.toMap(), emptyMapFactory);
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
     * Creates a new instance {@link HashParameters} from provided data. Initial data is copied.
     *
     * @param initialContent initial content.
     * @return a new instance of {@link HashParameters} initialized with the given content.
     */
    public static HashParameters create(Map<String, List<String>> initialContent) {
        return new HashParameters(initialContent);
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
     * Creates new instance of {@link HashParameters} as a concatenation of provided parameters.
     * Values for keys found across the provided parameters are "concatenated" into a {@link List} entry for their respective key
     * in the created {@link HashParameters} instance.
     *
     * @param parameters parameters to concatenate.
     * @return a new instance of {@link HashParameters} that represents the concatenation of the provided parameters.
     */
    public static HashParameters concat(Parameters... parameters) {
        return concat(HashParameters::new, HashParameters::new, parameters);
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

    protected static <T extends HashParameters> T concat(Supplier<T> emptyFactory,
                                                         Function<Map<String, List<String>>, T> singletonFactory,
                                                         Parameters... parameters) {

        if (parameters == null || parameters.length == 0) {
            return emptyFactory.get();
        }
        List<Map<String, List<String>>> prms = new ArrayList<>(parameters.length);
        for (Parameters p : parameters) {
            if (p != null) {
                prms.add(p.toMap());
            }
        }
        return concat(prms, emptyFactory, singletonFactory);
    }

    protected static <T extends HashParameters> T concat(Iterable<Parameters> parameters,
                                                         Supplier<T> emptyFactory,
                                                         Function<Map<String, List<String>>, T> singletoneFactory) {
        ArrayList<Map<String, List<String>>> prms = new ArrayList<>();
        for (Parameters p : parameters) {
            if (p != null) {
                prms.add(p.toMap());
            }
        }
        return concat(prms, emptyFactory, singletoneFactory);
    }

    protected static <T extends HashParameters> T concat(List<Map<String, List<String>>> prms,
                                                         Supplier<T> emptyFactory,
                                                         Function<Map<String, List<String>>, T> singletonFactory) {

        if (prms.isEmpty()) {
            return emptyFactory.get();
        }
        if (prms.size() == 1) {
            return singletonFactory.apply(prms.get(0));
        }

        Map<String, List<String>> composer = new HashMap<>();
        for (Map<String, List<String>> prm : prms) {
            for (Map.Entry<String, List<String>> entry : prm.entrySet()) {
                List<String> strings = composer.computeIfAbsent(entry.getKey(), k -> new ArrayList<>(entry.getValue().size()));
                strings.addAll(entry.getValue());
            }
        }
        return singletonFactory.apply(composer);
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
        Map<String, List<String>> result = emptyMapFactory.get();
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
        return equals(o, HashParameters.class);
    }

    protected <T extends HashParameters> boolean equals(Object o, Class<T> cl) {
        if (this == o) {
            return true;
        }
        if (!cl.isInstance(o)) {
            return false;
        }
        HashParameters that = (HashParameters) o;
        return content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return hashCode(HashParameters.class.hashCode());
    }

    protected int hashCode(int extra) {
        return extra + 37 * content.hashCode();
    }
}
