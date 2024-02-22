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
import java.util.NoSuchElementException;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

class ParametersEmpty implements Parameters {
    private final String component;

    ParametersEmpty(String component) {
        this.component = component;
    }

    @Override
    public List<String> all(String name) throws NoSuchElementException {
        throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
    }

    @Override
    public List<Value<String>> allValues(String name) throws NoSuchElementException {
        throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
    }

    @Override
    public String get(String name) throws NoSuchElementException {
        throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
    }

    @Override
    public boolean contains(String name) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public String component() {
        return component;
    }

    @Override
    public String toString() {
        return component + ": <empty>";
    }

    @Override
    public OptionalValue<String> first(String name) {
        return OptionalValue.create(MapperManager.global(), name, GenericType.STRING);
    }
}
