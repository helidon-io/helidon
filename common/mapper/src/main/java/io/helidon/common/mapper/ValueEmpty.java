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

package io.helidon.common.mapper;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.GenericType;

class ValueEmpty<T> implements OptionalValue<T> {
    private final Mappers mapperManager;
    private final GenericType<T> type;
    private final String name;
    private final String[] qualifiers;

    ValueEmpty(Mappers mapperManager, GenericType<T> type, String name, String[] qualifiers) {
        this.mapperManager = mapperManager;
        this.type = type;
        this.name = name;
        this.qualifiers = qualifiers;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<T> asOptional() throws MapperException {
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <N> OptionalValue<N> as(Function<? super T, ? extends N> mapper) {
        return (OptionalValue<N>) this;
    }

    @Override
    public <N> OptionalValue<N> as(Class<N> type) throws MapperException {
        GenericType<N> wantedType = GenericType.create(type);
        if (mapperManager.mapper(this.type, wantedType, qualifiers).isPresent()) {
            return new ValueEmpty<>(mapperManager, wantedType, name, qualifiers);
        }
        throw new MapperException(this.type, wantedType, "Cannot find mapper for " + name());
    }

    @Override
    public <N> OptionalValue<N> as(GenericType<N> type) throws MapperException {
        if (mapperManager.mapper(this.type, type, qualifiers).isPresent()) {
            return new ValueEmpty<>(mapperManager, type, name, qualifiers);
        }
        throw new MapperException(this.type, type, "Cannot find mapper for " + name());
    }

    @Override
    public T get() {
        throw new NoSuchElementException("No value present for " + name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OptionalValue<?> other)) {
            return false;
        }

        return Objects.equals(name(), other.name())
                && Objects.equals(asOptional(), other.asOptional());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
