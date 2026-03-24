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

package io.helidon.common.mapper;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.service.registry.Services;

// obtains mappers only when needed
class ValueBackedNoMappers<T> implements OptionalValue<T> {
    private final String name;
    private final T value;
    private final GenericType<T> type;
    private final String[] qualifiers;

    ValueBackedNoMappers(String name, T value, GenericType<T> type, String[] qualifiers) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);

        this.name = name;
        this.value = value;
        this.type = type;
        this.qualifiers = qualifiers;
    }

    ValueBackedNoMappers(String name, T value, String[] qualifiers) {
        this(name, value, null, qualifiers);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<T> asOptional() throws MapperException {
        return Optional.of(value);
    }

    @Override
    public <N> OptionalValue<N> as(Function<? super T, ? extends N> mapper) {
        N result = mapper.apply(value);
        return new ValueBackedNoMappers<>(name(), result, qualifiers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <N> OptionalValue<N> as(Class<N> type) throws MapperException {
        if (type.isAssignableFrom(this.value.getClass())) {
            // this is a valid value, no need to map it
            return (OptionalValue<N>) this;
        }
        GenericType<T> myType = this.type == null ? GenericType.create(value) : this.type;
        Mappers mappers = Services.get(Mappers.class);
        return OptionalValue.create(mappers, name, mappers.map(value, myType, GenericType.create(type), qualifiers));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <N> OptionalValue<N> as(GenericType<N> type) throws MapperException {
        if (type.isClass() && type.rawType().isAssignableFrom(this.value.getClass())) {
            // this is a valid value, no need to map it
            return (OptionalValue<N>) this;
        }
        GenericType<T> myType = this.type == null ? GenericType.create(value) : this.type;
        Mappers mappers = Services.get(Mappers.class);
        return OptionalValue.create(mappers, name, mappers.map(value, myType, type, qualifiers));
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Value<?> other)) {
            return false;
        }

        return Objects.equals(name(), other.name())
                && Objects.equals(asOptional(), other.asOptional());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
