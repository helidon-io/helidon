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

package io.helidon.common.mapper;

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.GenericType;

class BackedValueProvider implements ValueProvider {
    private final MapperManager mapperManager;
    private final String name;
    private final String value;

    BackedValueProvider(MapperManager mapperManager, String name, String value) {
        this.mapperManager = mapperManager;
        this.name = name;
        this.value = value;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> Value<T> as(Class<T> type) {
        return Value.create(name, mapperManager.map(value, String.class, type));
    }

    @Override
    public <T> Value<T> as(GenericType<T> genericType) {
        return Value.create(name, mapperManager.map(value, STRING_TYPE, genericType));
    }

    @Override
    public <T> Value<List<T>> asList(Class<T> elementType) {
        if (value.contains(",")) {
            String[] values = value.split(",");
            List<T> result = new LinkedList<>();
            for (String value : values) {
                T typedValue = mapperManager.map(value, String.class, elementType);
                result.add(typedValue);
            }
            return Value.create(name, result);
        } else {
            return Value.create(name, List.of(mapperManager.map(value, String.class, elementType)));
        }
    }
}
