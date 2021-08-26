/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.Value;
import io.helidon.common.mapper.ValueProvider;

class ParameterValueProvider implements ValueProvider {
    private final String name;
    private final List<String> values;
    private final MapperManager mapperManager;

    ParameterValueProvider(String name, List<String> values) {
        this.name = name;
        this.values = values;
        this.mapperManager = MapperManager.create();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> Value<T> as(Class<T> type) {
        return Value.create(name, mapperManager.map(values.get(0), String.class, type));
    }

    @Override
    public <T> Value<T> as(GenericType<T> genericType) {
        return Value.create(name, mapperManager.map(values.get(0), STRING_TYPE, genericType));
    }

    @Override
    public <T> Value<List<T>> asList(Class<T> elementType) {
        List<T> result = new LinkedList<>();

        for (String value : values) {
            result.add(mapperManager.map(value, String.class, elementType));
        }

        return Value.create(name, result);
    }
}
