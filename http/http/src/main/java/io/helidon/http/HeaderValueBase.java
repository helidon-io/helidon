/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.Value;

abstract class HeaderValueBase implements Header {
    private static final String[] QUALIFIER = new String[] {"http", "header"};
    private final HeaderName name;
    private final String actualName;
    private final String firstValue;
    private final boolean changing;
    private final boolean sensitive;

    HeaderValueBase(HeaderName name, boolean changing, boolean sensitive, String value) {
        this.name = name;
        this.actualName = name.defaultCase();
        this.changing = changing;
        this.sensitive = sensitive;
        this.firstValue = value;
    }

    @Override
    public String name() {
        return actualName;
    }

    @Override
    public HeaderName headerName() {
        return name;
    }

    @Override
    public String get() {
        return firstValue;
    }

    @Override
    public String getString() {
        // optimize for string values, no need to use mapping
        return get();
    }

    @Override
    public <T> T get(Class<T> type) {
        return MapperManager.global().map(get(), String.class, type, QUALIFIER);
    }

    @Override
    public <N> Value<N> as(Function<? super String, ? extends N> mapper) {
        return Value.create(MapperManager.global(), name(), mapper.apply(get()), QUALIFIER);
    }

    @Override
    public <N> Value<N> as(Class<N> type) throws MapperException {
        return asString().as(type);
    }

    @Override
    public Value<String> asString() {
        return new UnmappedValue<>(GenericType.STRING, name(), get());
    }

    @Override
    public <N> Value<N> as(GenericType<N> type) throws MapperException {
        return asString().as(type);
    }

    @Override
    public Optional<String> asOptional() throws MapperException {
        return asString().asOptional();
    }

    @Override
    public Value<Boolean> asBoolean() {
        return asString().asBoolean();
    }

    @Override
    public Value<Integer> asInt() {
        return asString().asInt();
    }

    @Override
    public Value<Long> asLong() {
        return asString().asLong();
    }

    @Override
    public Value<Double> asDouble() {
        return asString().asDouble();
    }

    @Override
    public abstract int valueCount();

    @Override
    public boolean sensitive() {
        return sensitive;
    }

    @Override
    public boolean changing() {
        return changing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(changing, sensitive, actualName, allValues());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeaderValueBase that)) {
            return false;
        }
        return changing == that.changing
                && sensitive == that.sensitive
                && actualName.equals(that.actualName)
                && valueCount() == that.valueCount()
                && allValues().equals(that.allValues());
    }

    @Override
    public String toString() {
        return "HttpHeaderImpl["
                + "name=" + name + ", "
                + "values=" + allValues() + ", "
                + "changing=" + changing + ", "
                + "sensitive=" + sensitive + ']';
    }

    @SuppressWarnings("removal")
    private static final class UnmappedValue<T> implements Value<T> {
        private final String name;
        private final T value;
        private final GenericType<T> type;
        private final boolean isClass;
        private final Class<?> theClass;

        private UnmappedValue(GenericType<T> type, String name, T value) {
            this.type = type;
            this.name = name;
            this.value = value;
            this.isClass = type.isClass();
            this.theClass = type.rawType();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public T get() {
            return value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <N> Value<N> as(Class<N> type) throws MapperException {
            if (isType(type)) {
                return (Value<N>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .as(type);
        }

        @Override
        public <N> Value<N> as(GenericType<N> type) throws MapperException {
            if (type.equals(this.type)) {
                //noinspection unchecked
                return (Value<N>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .as(type);
        }

        @Override
        public <N> Value<N> as(Function<? super T, ? extends N> mapper) {
            N value = mapper.apply(this.value);
            return new UnmappedValue<>(GenericType.create(value), name, value);
        }

        @Override
        public Optional<T> asOptional() throws MapperException {
            return Optional.of(value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Value<Boolean> asBoolean() {
            if (isType(Boolean.class) || isType(boolean.class)) {
                return (Value<Boolean>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .asBoolean();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Value<String> asString() {
            if (isType(String.class)) {
                return (Value<String>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .asString();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Value<Integer> asInt() {
            if (isType(Integer.class) || isType(int.class)) {
                return (Value<Integer>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .asInt();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Value<Long> asLong() {
            if (isType(Long.class) || isType(long.class)) {
                return (Value<Long>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .asLong();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Value<Double> asDouble() {
            if (isType(Double.class) || isType(double.class)) {
                return (Value<Double>) this;
            }
            return Value.create(MapperManager.global(), name, value, this.type, QUALIFIER)
                    .asDouble();
        }

        private boolean isType(Class<?> desired) {
            return isClass && desired == theClass;
        }
    }
}
