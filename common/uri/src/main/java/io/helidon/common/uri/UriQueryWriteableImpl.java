/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

final class UriQueryWriteableImpl implements UriQueryWriteable {
    private final Map<String, List<String>> rawQueryParams = new HashMap<>();
    private final Map<String, List<String>> decodedQueryParams = new HashMap<>();

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
            for (String name : uriQuery.names()) {
                List<String> raw = uriQuery.getAllRaw(name);
                rawQueryParams.computeIfAbsent(name, it -> new ArrayList<>())
                        .addAll(raw);
                List<String> decoded = uriQuery.all(name);
                decodedQueryParams.computeIfAbsent(name, it -> new ArrayList<>())
                        .addAll(decoded);
            }
        }

        return this;
    }

    @Override
    public void clear() {
        this.rawQueryParams.clear();
        this.decodedQueryParams.clear();
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
            return OptionalValue.create(MapperManager.global(), name, GenericType.STRING, "uri", "query");
        }
        String value = values.isEmpty() ? "" : values.iterator().next();
        return OptionalValue.create(MapperManager.global(), name, value, GenericType.STRING, "uri", "query");
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
                .map(it -> Value.create(MapperManager.global(), name, it, GenericType.STRING, "uri", "query"))
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

        rawQueryParams.put(encodedName, encodedValues);
        decodedQueryParams.put(name, decodedValues);

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
        return this;
    }

    @Override
    public UriQueryWriteable remove(String name, Consumer<List<String>> removedConsumer) {
        rawQueryParams.remove(name);
        List<String> removed = decodedQueryParams.remove(name);
        if (removed != null) {
            removedConsumer.accept(removed);
        }
        return this;
    }

    @Override
    public void fromQueryString(String queryString) {
        String remaining = queryString;
        String next;
        int and;
        while (true) {
            and = remaining.indexOf('&');
            if (and == -1) {
                addRaw(remaining);
                break;
            }
            next = remaining.substring(0, and);
            remaining = remaining.substring(and + 1);
            addRaw(next);
        }
    }

    @Override
    public String toString() {
        return component() + ": decoded: " + decodedQueryParams + ", raw: " + rawQueryParams;
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
    }
}
