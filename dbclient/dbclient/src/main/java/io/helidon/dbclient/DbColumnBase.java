/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.OptionalValue;

/**
 * Base {@link DbColumn} implementation.
 */
public abstract class DbColumnBase implements DbColumn {

    private final Object value;
    private final MapperManager mapperManager;
    private final String[] mappingQualifiers;

    /**
     * Create a new instance.
     *
     * @param value             value
     * @param mapperManager     mapper manager
     * @param mappingQualifiers mapping qualifiers
     */
    protected DbColumnBase(Object value, MapperManager mapperManager, String... mappingQualifiers) {
        this.value = value;
        this.mapperManager = mapperManager;
        this.mappingQualifiers = mappingQualifiers;
    }

    /**
     * Get raw value of the database column.
     *
     * @return raw value of the column
     */
    protected Object rawValue() {
        return value;
    }

    @Override
    public <T> T get(Class<T> type) throws MapperException {
        if (null == value) {
            return null;
        }
        if (type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        return map(value, type);
    }

    @Override
    public <T> T get(GenericType<T> type) throws MapperException {
        if (null == value) {
            return null;
        }
        if (type.isClass()) {
            Class<?> theClass = type.rawType();
            if (theClass.isAssignableFrom(value.getClass())) {
                return type.cast(value);
            }
        }
        return map(value, type);
    }

    @Override
    public <N> OptionalValue<N> as(Class<N> type) throws MapperException {
        if (value == null) {
            return OptionalValue.create(mapperManager, name(), type, "dbclient", "column");
        }
        return OptionalValue.create(mapperManager, name(), get(type), "dbclient", "column");
    }

    @Override
    public <N> OptionalValue<N> as(GenericType<N> type) throws MapperException {
        if (value == null) {
            return OptionalValue.create(mapperManager, name(), type, "dbclient", "column");
        }
        return OptionalValue.create(mapperManager, name(), get(type), "dbclient", "column");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <N> OptionalValue<N> as(Function<? super Object, ? extends N> mapper) {
        if (value == null) {
            return OptionalValue.create(mapperManager, name(), (Class<N>) javaType(), "dbclient", "column");
        }
        return OptionalValue.create(mapperManager, name(), mapper.apply(value), "dbclient", "column");
    }

    @Override
    public Optional<Object> asOptional() throws MapperException {
        return Optional.ofNullable(value);
    }

    @Override
    public OptionalValue<Boolean> asBoolean() {
        return as(Boolean.class);
    }

    @Override
    public OptionalValue<String> asString() {
        return as(String.class);
    }

    @Override
    public OptionalValue<Integer> asInt() {
        return as(Integer.class);
    }

    @Override
    public OptionalValue<Long> asLong() {
        return as(Long.class);
    }

    @Override
    public OptionalValue<Double> asDouble() {
        return as(Double.class);
    }

    /**
     * Map value to target type using {@link io.helidon.common.mapper.Mapper}.
     *
     * @param value source value
     * @param type target type
     * @param <SRC> type of the source value
     * @param <T> type of the target value
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    @SuppressWarnings("unchecked")
    protected <SRC, T> T map(SRC value, GenericType<T> type) {
        Class<SRC> theClass = (Class<SRC>) value.getClass();
        return mapperManager.map(value, GenericType.create(theClass), type, mappingQualifiers);
    }

    /**
     * Map value to target type using {@link io.helidon.common.mapper.Mapper}.
     * {@link String#valueOf(Object)} is used as fallback option when {@code Mapper} fails.
     *
     * @param value source value
     * @param type target type
     * @param <SRC> type of the source value
     * @param <T> type of the target value
     * @return result of the mapping
     */
    @SuppressWarnings("unchecked")
    protected <SRC, T> T map(SRC value, Class<T> type) {
        Class<SRC> theClass = (Class<SRC>) value.getClass();
        try {
            return mapperManager.map(value, theClass, type, mappingQualifiers);
        } catch (MapperException e) {
            if (type.equals(String.class)) {
                return (T) String.valueOf(value);
            }
            throw e;
        }
    }
}
