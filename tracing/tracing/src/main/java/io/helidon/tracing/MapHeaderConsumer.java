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
import java.util.stream.Stream;

class MapHeaderConsumer implements HeaderConsumer {
    private final Map<String, List<String>> headers;

    MapHeaderConsumer(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    @Override
    public Iterable<String> keys() {
        return headers.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(headers.get(key))
                .map(List::stream)
                .flatMap(Stream::findFirst);
    }

    @Override
    public Iterable<String> getAll(String key) {
        return headers.getOrDefault(key, List.of());
    }

    @Override
    public boolean contains(String key) {
        return headers.containsKey(key);
    }

    @Override
    public void setIfAbsent(String key, String... values) {
        headers.putIfAbsent(key, List.of(values));
    }

    @Override
    public void set(String key, String... values) {
        headers.put(key, List.of(values));
    }
}
