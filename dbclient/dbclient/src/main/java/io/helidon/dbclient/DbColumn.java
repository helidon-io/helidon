/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;

/**
 * Column data and metadata.
 */
public interface DbColumn {
    /**
     * Typed value of this column.
     * This method can return a correct result only if the type is the same as {@link #javaType()} or there is a
     * {@link io.helidon.common.mapper.Mapper} registered that can map it.
     *
     * @param type class of the type that should be returned (must be supported by the underlying data type)
     * @param <T>  type of the returned value
     * @return value of this column correctly typed
     * @throws MapperException in case the type is not the underlying {@link #javaType()} and
     *                         there is no mapper registered for it
     */
    <T> T as(Class<T> type) throws MapperException;

    /**
     * Value of this column as a generic type.
     * This method can return a correct result only if the type represents a class, or if there is a
     * {@link io.helidon.common.mapper.Mapper} registered that can map underlying {@link #javaType()} to the type requested.
     *
     * @param type requested type
     * @param <T>  type of the returned value
     * @return value mapped to the expected type if possible
     * @throws MapperException in case the mapping cannot be done
     */
    <T> T as(GenericType<T> type) throws MapperException;

    /**
     * Untyped value of this column, returns java type as provided by the underlying database driver.
     *
     * @return value of this column
     */
    default Object value() {
        return as(javaType());
    }

    /**
     * Type of the column as would be returned by the underlying database driver.
     *
     * @return class of the type
     * @see #dbType()
     */
    Class<?> javaType();

    /**
     * Type of the column in the language of the database.
     * <p>
     * Example for SQL - if a column is declared as {@code VARCHAR(256)} in the database,
     * this method would return {@code VARCHAR} and method {@link #javaType()} would return {@link String}.
     *
     * @return column type as the database understands it
     */
    String dbType();

    /**
     * Column name.
     *
     * @return name of this column
     */
    String name();

    /**
     * Precision of this column.
     * <p>
     * Precision depends on data type:
     * <ul>
     * <li>Numeric: The maximal number of digits of the number</li>
     * <li>String/Character: The maximal length</li>
     * <li>Binary: The maximal number of bytes</li>
     * <li>Other: Implementation specific</li>
     * </ul>
     *
     * @return precision of this column or {@code empty} if precision is not available
     */
    default Optional<Integer> precision() {
        return Optional.empty();
    }

    /**
     * Scale of this column.
     * <p>
     * Scale is the number of digits in a decimal number to the right of the decimal separator.
     *
     * @return scale of this column or {@code empty} if scale is not available
     */
    default Optional<Integer> scale() {
        return Optional.empty();
    }
}
