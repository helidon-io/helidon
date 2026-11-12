/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class JdbcOutParameters implements JdbcClient.OutParameters {
    private final Map<String, Class<?>> types;
    private final Map<String, Object> values;

    private JdbcOutParameters(Map<String, Class<?>> types, Map<String, Object> values) {
        this.types = Map.copyOf(types);
        // SQL NULL is represented by absence from the value map, independently of the type map.
        this.values = Map.copyOf(values);
    }

    static JdbcOutParameters capture(JdbcClient.CallResult result, List<JdbcCallOutput> outputs) {
        Map<String, Class<?>> types = new HashMap<>();
        Map<String, Object> values = new HashMap<>();
        for (JdbcCallOutput output : outputs) {
            Class<?> type = JdbcCallOutput.canonical(output.type());
            types.put(output.name(), type);
            result.optional(output.name(), type).ifPresent(value ->
                    values.put(output.name(), value instanceof byte[] bytes ? bytes.clone() : value));
        }
        return new JdbcOutParameters(types, values);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output class must not be null.");
        return optional(name, type).orElseThrow(() -> new IllegalArgumentException("The output contains SQL NULL."));
    }

    @Override
    public <T> Optional<T> optional(String name, Class<T> type) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output class must not be null.");
        Class<?> canonical = types.get(name);
        if (canonical == null || canonical != JdbcScalarAccess.normalized(type)) {
            throw new IllegalArgumentException("The output name or canonical class is invalid.");
        }
        Object value = values.get(name);
        @SuppressWarnings("unchecked")
        T detached = (T) (value instanceof byte[] bytes ? bytes.clone() : value);
        return Optional.ofNullable(detached);
    }
}
