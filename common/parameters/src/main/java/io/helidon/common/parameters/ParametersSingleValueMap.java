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

package io.helidon.common.parameters;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

class ParametersSingleValueMap implements Parameters {
    private final MapperManager mapperManager;
    private final String component;
    private final Map<String, String> params;
    private final String[] qualifiers;

    ParametersSingleValueMap(MapperManager mapperManager, String component, Map<String, String> params) {
        this.qualifiers = component.split("/");
        this.mapperManager = mapperManager;
        this.component = component;
        this.params = params;
    }

    @Override
    public List<String> all(String name) throws NoSuchElementException {
        String value = params.get(name);
        if (value == null) {
            throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
        }
        return List.of(value);
    }

    @Override
    public List<Value<String>> allValues(String name) throws NoSuchElementException {
        String value = params.get(name);
        if (value == null) {
            throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
        }
        return List.of(Value.create(mapperManager, name, value, qualifiers));
    }

    @Override
    public String get(String name) throws NoSuchElementException {
        String value = params.get(name);
        if (value == null) {
            throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
        }
        return value;
    }

    @Override
    public boolean contains(String name) {
        return params.containsKey(name);
    }

    @Override
    public int size() {
        return params.size();
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(params.keySet());
    }

    @Override
    public String component() {
        return component;
    }

    @Override
    public String toString() {
        return component + ": " + params;
    }

    @Override
    public OptionalValue<String> first(String name) {
        if (contains(name)) {
            return OptionalValue.create(mapperManager, name, get(name), GenericType.STRING, qualifiers);
        }
        return OptionalValue.create(mapperManager, name, GenericType.STRING, qualifiers);
    }
}
