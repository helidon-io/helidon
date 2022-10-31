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
package io.helidon.tracing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

/**
 * Side-effect-safe implementation of {@link HeaderConsumer}.
 * <p>
 *     This class has historically modified the map that is passed as the initial headers.
 *     If that map was not case-insensitive (as headers should be), then this class would not honor
 *     case-insensitive conventions.
 * </p>
 * <p>
 *     To implement case-insensitivity when the provided headers map is not itself case-insensitive,
 *     this class maintains a "shadow" map and uses that for key-based operations that must not depend
 *     on case.
 * </p>
 * <p>
 *     For efficiency, callers should provide a case-insensitive map as the initial headers.
 * </p>
 */
class MapHeaderConsumer implements HeaderConsumer {
    private final Map<String, List<String>> delegate;
    private final Map<String, List<String>> caseInsensitiveShadow;
    private final Map<String, List<String>> mapToQuery;

    MapHeaderConsumer(Map<String, List<String>> headers) {
        delegate = headers;
        if (isCaseInsensitive(headers)) {
            caseInsensitiveShadow = null;
            mapToQuery = delegate;
        } else {
            caseInsensitiveShadow = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
            caseInsensitiveShadow.putAll(headers);
            mapToQuery = caseInsensitiveShadow;
        }
    }

    @Override
    public Iterable<String> keys() {
        return delegate.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(mapToQuery.get(key))
                .map(List::stream)
                .flatMap(Stream::findFirst);
    }

    @Override
    public Iterable<String> getAll(String key) {
        return mapToQuery.getOrDefault(key, List.of());
    }

    @Override
    public boolean contains(String key) {
        return mapToQuery.containsKey(key);
    }

    @Override
    public void setIfAbsent(String key, String... values) {
        List<String> valueList = List.of(values);
        delegate.putIfAbsent(key, valueList);
        if (caseInsensitiveShadow != null) {
            caseInsensitiveShadow.putIfAbsent(key, valueList);
        }
    }

    @Override
    public void set(String key, String... values) {
        List<String> valueList = List.of(values);
        delegate.put(key, valueList);
        if (caseInsensitiveShadow != null) {
            caseInsensitiveShadow.put(key, valueList);
        }
    }

    private static boolean isCaseInsensitive(Map<String, List<String>> headers) {
        return (headers instanceof TreeMap
                        && ((TreeMap<?, ?>) headers).comparator() == String.CASE_INSENSITIVE_ORDER)
                || (headers instanceof ConcurrentSkipListMap<?, ?>
                        && ((ConcurrentSkipListMap<?, ?>) headers).comparator() == String.CASE_INSENSITIVE_ORDER);
    }
}
