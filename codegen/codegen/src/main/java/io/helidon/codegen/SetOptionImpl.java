/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

final class SetOptionImpl<T> implements Option<Set<T>> {
    private final String name;
    private final String description;
    private final Set<T> defaultValue;
    private final Function<String, T> mapFunction;
    private final GenericType<Set<T>> type;

    SetOptionImpl(String name,
                  String description,
                  Set<T> defaultValue,
                  Function<String, T> mapFunction,
                  GenericType<Set<T>> type) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.mapFunction = mapFunction;
        this.type = type;
    }

    @Override
    public GenericType<Set<T>> type() {
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
    public Set<T> defaultValue() {
        return defaultValue;
    }

    @Override
    public Optional<Set<T>> findValue(CodegenOptions options) {
        return options.option(name)
                .map(it -> it.split(","))
                .map(this::toSet);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SetOptionImpl<?> option)) {
            return false;
        }
        return Objects.equals(name, option.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    private Set<T> toSet(String[] strings) {
        return Stream.of(strings)
                .map(String::trim)
                .map(mapFunction)
                .collect(Collectors.toUnmodifiableSet());
    }
}
