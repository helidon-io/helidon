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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

class EmptyValue<T> implements Value<T> {
    private final String name;

    EmptyValue(String name) {
        this.name = name;
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
    public <N> Value<N> as(Function<T, N> mapper) {
        return (Value<N>) this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EmptyValue<?> that = (EmptyValue<?>) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
