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

package io.helidon.declarative.tests.graphql;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.helidon.graphql.spi.CustomScalar;
import io.helidon.service.registry.Service;

@Service.Singleton
class StructuredValueScalar implements CustomScalar<StructuredValue> {
    @Override
    public Object serialize(StructuredValue value) {
        return describe(value.value());
    }

    @Override
    public StructuredValue parseValue(Object value) {
        return new StructuredValue(value);
    }

    static String describe(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return sorted.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=" + describe(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder result = new StringBuilder("[");
            boolean first = true;
            for (Object element : iterable) {
                if (first) {
                    first = false;
                } else {
                    result.append(", ");
                }
                result.append(describe(element));
            }
            return result.append(']').toString();
        }
        return String.valueOf(value);
    }
}
