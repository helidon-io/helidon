/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.helidon.common.uri.UriEncoding.decodeUri;

// must be lazily populated to prevent perf overhead when queries are ignored
final class UriQueryImpl implements UriQuery {
    private final String query;

    private Map<String, List<String>> rawQueryParams;
    private Map<String, List<String>> decodedQueryParams;

    UriQueryImpl(String query) {
        this.query = query;
    }

    @Override
    public String rawValue() {
        return query;
    }

    @Override
    public String value() {
        ensureDecoded();

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
    public String getRaw(String name) throws NoSuchElementException {
        ensureRaw();
        List<String> values = rawQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values.isEmpty() ? "" : values.iterator().next();
    }

    @Override
    public List<String> getAllRaw(String name) throws NoSuchElementException {
        ensureRaw();
        List<String> values = rawQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values;
    }

    @Override
    public List<String> all(String name) throws NoSuchElementException {
        ensureDecoded();
        List<String> values = decodedQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values;
    }

    @Override
    public String value(String name) throws NoSuchElementException {
        ensureDecoded();
        List<String> values = decodedQueryParams.get(name);
        if (values == null) {
            throw new NoSuchElementException("Query parameter \"" + name + "\" is not available");
        }
        return values.isEmpty() ? "" : values.iterator().next();
    }

    @Override
    public boolean contains(String name) {
        ensureDecoded();
        return decodedQueryParams.containsKey(name);
    }

    @Override
    public boolean isEmpty() {
        // we now have a guarantee this is not an empty query
        // null strings are not allowed, and empty string returns empty()
        return false;
    }

    @Override
    public int size() {
        ensureDecoded();
        return decodedQueryParams.size();
    }

    @Override
    public Set<String> names() {
        ensureDecoded();
        return decodedQueryParams.keySet();
    }

    @Override
    public String component() {
        return "uri-query";
    }

    @Override
    public String toString() {
        return "?" + rawValue();
    }

    private void ensureDecoded() {
        if (decodedQueryParams == null) {
            Map<String, List<String>> newQueryParams = new HashMap<>();

            String remaining = query;
            String next;
            int and;
            while (true) {
                and = remaining.indexOf('&');
                if (and == -1) {
                    addDecoded(newQueryParams, remaining);
                    break;
                }
                next = remaining.substring(0, and);
                remaining = remaining.substring(and + 1);
                addDecoded(newQueryParams, next);
            }

            decodedQueryParams = newQueryParams;
        }
    }

    private void addDecoded(Map<String, List<String>> newQueryParams, String next) {
        int eq = next.indexOf('=');
        if (eq == -1) {
            newQueryParams.putIfAbsent(decodeUri(next), new LinkedList<>());
        } else {
            String name = next.substring(0, eq);
            String value = next.substring(eq + 1);
            newQueryParams.computeIfAbsent(decodeUri(name), it -> new LinkedList<>()).add(decodeUri(value));
        }
    }

    private void ensureRaw() {
        if (rawQueryParams == null) {
            Map<String, List<String>> newQueryParams = new HashMap<>();

            String remaining = query;
            String next;
            int and;
            while (true) {
                and = remaining.indexOf('&');
                if (and == -1) {
                    addRaw(newQueryParams, remaining);
                    break;
                }
                next = remaining.substring(0, and);
                remaining = remaining.substring(and + 1);
                addRaw(newQueryParams, next);
            }

            rawQueryParams = newQueryParams;
        }
    }

    private void addRaw(Map<String, List<String>> newQueryParams, String next) {
        int eq = next.indexOf('=');
        if (eq == -1) {
            newQueryParams.putIfAbsent(next, new LinkedList<>());
        } else {
            String name = next.substring(0, eq);
            String value = next.substring(eq + 1);
            newQueryParams.computeIfAbsent(name, it -> new LinkedList<>()).add(value);
        }
    }
}
