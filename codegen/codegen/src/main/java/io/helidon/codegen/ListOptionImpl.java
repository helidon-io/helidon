/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

final class ListOptionImpl<T> implements Option<List<T>> {
    private final String name;
    private final String description;
    private final List<T> defaultValue;
    private final Function<String, T> mapFunction;
    private final GenericType<List<T>> type;

    ListOptionImpl(String name,
                   String description,
                   List<T> defaultValue,
                   Function<String, T> mapFunction,
                   GenericType<List<T>> type) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.mapFunction = mapFunction;
        this.type = type;
    }

    @Override
    public GenericType<List<T>> type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<T> defaultValue() {
        return defaultValue;
    }

    @Override
    public Optional<List<T>> findValue(CodegenOptions options) {
        return options.option(name)
                .map(it -> it.split(","))
                .map(this::toList);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ListOptionImpl<?> option)) {
            return false;
        }
        return Objects.equals(name, option.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    private List<T> toList(String[] strings) {
        return Stream.of(strings)
                .map(String::trim)
                .map(mapFunction)
                .toList();
    }
}
