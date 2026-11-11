/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

final class UriQueryWriteableImpl implements UriQueryWriteable {
    private final Map<String, List<String>> rawQueryParams = new HashMap<>();
    private final Map<String, List<String>> decodedQueryParams = new HashMap<>();

    private Map<String, Set<String>> rawNamesByDecodedName;

    UriQueryWriteableImpl() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UriQuery that)) {
            return false;
        }
        if (!Objects.equals(this.names(), that.names())) {
            return false;
        }

        for (String name : this.names()) {
            if (!Objects.equals(this.all(name), that.all(name))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(decodedQueryParams);
    }

    @Override
    public UriQueryWriteable from(UriQuery uriQuery) {
        if (uriQuery instanceof UriQuery.Copyable copyable) {
            uriQuery = Objects.requireNonNull(copyable.copy(), "Query copy must not be null");
        }
        if (uriQuery instanceof UriQueryWriteableImpl impl) {
            impl.rawQueryParams.forEach((key, value) -> {
                rawQueryParams.computeIfAbsent(key, it -> new ArrayList<>())
                        .addAll(value);
            });
            impl.decodedQueryParams.forEach((key, value) -> {
                decodedQueryParams.computeIfAbsent(key, it -> new ArrayList<>())
                        .addAll(value);
            });
        } else {
            if (uriQuery instanceof UriQueryImpl impl) {
                // The original query string can differ from values edited through its raw parameter lists.
                impl.rawParameters().forEach((name, values) -> rawQueryParams
                        .computeIfAbsent(name, _ -> new ArrayList<>())
                        .addAll(values));
            } else {
                rawParametersForCopy(uriQuery).forEach((name, values) -> rawQueryParams
                        .computeIfAbsent(name, _ -> new ArrayList<>())
                        .addAll(values));
            }
            for (String name : uriQuery.names()) {
                List<String> decoded = uriQuery.all(name);
                decodedQueryParams.computeIfAbsent(name, it -> new ArrayList<>())
                        .addAll(decoded);
            }
        }
        Map<String, Set<String>> rawNames = rawNamesByDecodedName;
        if (rawNames != null) {
            rawNames.clear();
            rawQueryParams.keySet().forEach(rawName -> rawNames
                    .computeIfAbsent(UriEncoding.decodeUri(rawName), it -> new HashSet<>())
                    .add(rawName));
        }

        return this;
    }

    @Override
    public void clear() {
        this.rawQueryParams.clear();
        this.decodedQueryParams.clear();
        this.rawNamesByDecodedName = null;
    }

    @Override
    public String rawValue() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<String>> entry : rawQueryParams.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                sb.append('&');
                sb.append(name);
                sb.append('=');
                sb.append(value);
            }
        }
        if (sb.isEmpty()) {
            return "";
        }
        sb.deleteCharAt(0);
        return sb.toString();
    }

    @Override
    public String value() {
        if (decodedQueryParams.isEmpty()) {
            return "";
        }

        List<String> params = new ArrayList<>(decodedQueryParams.size());

        decodedQueryParams.forEach((name, values) -> {
            params.add(name + "=" + String.join(",", values));
        });

        return String.join("&", params);
    }

    @Override
    public OptionalValue<String> first(String name) {
        List<String> values = decodedQueryParams.get(name);
        if (values == null) {
            return OptionalValue.createEmpty(name, "uri", "query");
        }
        String value = values.isEmpty() ? "" : values.iterator().next();
        return OptionalValue.create(name, value, GenericType.STRING, "uri", "query");
    }

    @Override
    public String getRaw(String name) throws NoSuchElementException {
        List<String> values = rawQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values.isEmpty() ? "" : values.iterator().next();
    }

    @Override
    public List<String> getAllRaw(String name) throws NoSuchElementException {
        List<String> values = rawQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values;
    }

    @Override
    public List<String> all(String name) throws NoSuchElementException {
        List<String> values = decodedQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values;
    }

    @Override
    public List<Value<String>> allValues(String name) throws NoSuchElementException {
        List<String> values = decodedQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values.stream()
                .map(it -> Value.create(name, it, GenericType.STRING, "uri", "query"))
                .collect(Collectors.toList());
    }

    @Override
    public String get(String name) throws NoSuchElementException {
        List<String> values = decodedQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values.isEmpty() ? "" : values.iterator().next();
    }

    @Override
    public boolean contains(String name) {
        return rawQueryParams.containsKey(name);
    }

    @Override
    public boolean isEmpty() {
        return decodedQueryParams.isEmpty();
    }

    @Override
    public int size() {
        return decodedQueryParams.size();
    }

    @Override
    public Set<String> names() {
        return decodedQueryParams.keySet();
    }

    @Override
    public String component() {
        return "uri-query";
    }

    @Override
    public UriQueryWriteable set(String name, String... values) {
        String encodedName = UriEncoding.encode(name, UriEncoding.Type.QUERY_PARAM_SPACE_ENCODED);

        List<String> decodedValues = new ArrayList<>(values.length);
        List<String> encodedValues = new ArrayList<>(values.length);

        for (String value : values) {
            decodedValues.add(value);
            encodedValues.add(UriEncoding.encode(value, UriEncoding.Type.QUERY_PARAM_SPACE_ENCODED));
        }

        List<String> existingDecodedValues = decodedQueryParams.get(name);
        List<String> existingRawValues = rawQueryParams.get(encodedName);
        if (existingDecodedValues != null
                && (existingRawValues == null
                            || existingRawValues.isEmpty()
                            || existingRawValues.size() != existingDecodedValues.size())) {
            removeRawAliases(name);
        }
        rawQueryParams.put(encodedName, encodedValues);
        decodedQueryParams.put(name, decodedValues);
        indexRawName(name, encodedName);

        return this;
    }

    @Override
    public UriQueryWriteable add(String name, String value) {
        String encodedName = UriEncoding.encodeUri(name);
        String encodedValue = UriEncoding.encodeUri(value);

        rawQueryParams.computeIfAbsent(encodedName, it -> new ArrayList<>(1))
                .add(encodedValue);
        decodedQueryParams.computeIfAbsent(name, it -> new ArrayList<>(1))
                .add(value);
        indexRawName(name, encodedName);

        return this;
    }

    @Override
    public UriQueryWriteable setIfAbsent(String name, String... value) {
        if (rawQueryParams.containsKey(name)) {
            return this;
        }
        return set(name, value);
    }

    @Override
    public UriQueryWriteable remove(String name) {
        rawQueryParams.remove(name);
        decodedQueryParams.remove(name);
        unindexRawName(name);
        return this;
    }

    @Override
    public UriQueryWriteable remove(String name, Consumer<List<String>> removedConsumer) {
        rawQueryParams.remove(name);
        List<String> removed = decodedQueryParams.remove(name);
        unindexRawName(name);
        if (removed != null) {
            removedConsumer.accept(removed);
        }
        return this;
    }

    @Override
    public void fromQueryString(String queryString) {
        int start = 0;
        while (true) {
            int and = queryString.indexOf('&', start);
            if (and == -1) {
                addRaw(queryString.substring(start));
                break;
            }
            addRaw(queryString.substring(start, and));
            start = and + 1;
        }
    }

    @Override
    public String toString() {
        return component() + ": decoded: " + decodedQueryParams + ", raw: " + rawQueryParams;
    }

    private static Map<String, List<String>> rawParametersForCopy(UriQuery source) {
        String query = source.rawValue();
        if (query.isEmpty() && source.names().isEmpty()) {
            return Map.of();
        }
        Set<String> serializedNames = new HashSet<>();
        if (!query.isEmpty()) {
            int start = 0;
            while (true) {
                int and = query.indexOf('&', start);
                String segment = and == -1 ? query.substring(start) : query.substring(start, and);
                int eq = segment.indexOf('=');
                String name = eq == -1 ? segment : segment.substring(0, eq);
                serializedNames.add(name);
                if (and == -1) {
                    break;
                }
                start = and + 1;
            }
        }

        Map<String, List<String>> current = new HashMap<>();
        Set<String> decodedNames = new HashSet<>();
        for (String name : serializedNames) {
            try {
                current.put(name, source.getAllRaw(name));
                decodedNames.add(UriEncoding.decodeQuery(name));
            } catch (NoSuchElementException _) {
                // Decorators may rewrite serialized names. Preserve their legacy lookup behavior as one complete copy.
                Map<String, List<String>> legacy = new HashMap<>();
                for (String legacyName : source.names()) {
                    String encodedName = UriEncoding.encode(legacyName, UriEncoding.Type.QUERY_PARAM_SPACE_ENCODED);
                    legacy.put(encodedName, source.getAllRaw(legacyName));
                }
                return legacy;
            }
        }
        // Newly added parameters and empty raw lists may be absent from the captured serialization.
        for (String name : source.names()) {
            if (!current.containsKey(name)) {
                String encodedName = UriEncoding.encode(name, UriEncoding.Type.QUERY_PARAM_SPACE_ENCODED);
                if (current.containsKey(encodedName)) {
                    continue;
                }
                try {
                    List<String> values = source.getAllRaw(name);
                    // Do not infer a second raw alias for a name already represented in the serialization.
                    if (values.isEmpty() || !decodedNames.contains(name)) {
                        current.put(encodedName, values);
                    }
                } catch (NoSuchElementException _) {
                    // A decoded parameter name need not be a raw lookup key.
                }
            }
        }
        return current;
    }

    private void addRaw(String next) {
        int eq = next.indexOf('=');
        if (eq == -1) {
            addRaw(next, "");
        } else {
            String name = next.substring(0, eq);
            String value = next.substring(eq + 1);
            addRaw(name, value);
        }
    }

    private void addRaw(String encodedName, String encodedValue) {
        String decodedName = UriEncoding.decodeUri(encodedName);
        String decodedValue = UriEncoding.decodeUri(encodedValue);

        rawQueryParams.computeIfAbsent(encodedName, it -> new ArrayList<>(1))
                .add(encodedValue);
        decodedQueryParams.computeIfAbsent(decodedName, it -> new ArrayList<>(1))
                .add(decodedValue);
        indexRawName(decodedName, encodedName);
    }

    private void removeRawAliases(String name) {
        Map<String, Set<String>> rawNames = rawNamesByDecodedName;
        if (rawNames == null) {
            rawNames = new HashMap<>();
            for (String rawName : rawQueryParams.keySet()) {
                rawNames.computeIfAbsent(UriEncoding.decodeUri(rawName), it -> new HashSet<>())
                        .add(rawName);
            }
            rawNamesByDecodedName = rawNames;
        }
        Set<String> aliases = rawNames.remove(name);
        if (aliases != null) {
            aliases.forEach(rawQueryParams::remove);
        }
    }

    private void indexRawName(String decodedName, String rawName) {
        Map<String, Set<String>> rawNames = rawNamesByDecodedName;
        if (rawNames != null) {
            rawNames.computeIfAbsent(decodedName, it -> new HashSet<>())
                    .add(rawName);
        }
    }

    private void unindexRawName(String rawName) {
        Map<String, Set<String>> rawNames = rawNamesByDecodedName;
        if (rawNames == null) {
            return;
        }
        String decodedName = UriEncoding.decodeUri(rawName);
        Set<String> aliases = rawNames.get(decodedName);
        if (aliases != null) {
            aliases.remove(rawName);
            if (aliases.isEmpty()) {
                rawNames.remove(decodedName);
            }
        }
    }
}
