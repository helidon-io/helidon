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

import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;

/**
 * Representation of a single row in a database (in SQL this would be a row, in a Document DB, this would be a single document).
 */
public interface DbRow {
    /**
     * Get a column in this row. Column is identified by its name.
     *
     * @param name column name
     * @return a column in this row
     */
    DbColumn column(String name);

    /**
     * Get a column in this row. Column is identified by its index.
     *
     * @param index column index starting from {@code 1}
     * @return a column in this row
     */
    DbColumn column(int index);

    /**
     * Iterate through each column in this row.
     *
     * @param columnAction what to do with each column
     */
    void forEach(Consumer<? super DbColumn> columnAction);

    /**
     * Get specific class instance representation of this row.
     * Mapper for target class must be already registered.
     *
     * @param <T>  type of the returned value
     * @param type class of the returned value type
     * @return instance of requested class containing this database row
     * @throws MapperException in case the mapping is not defined or fails
     */
    <T> T as(Class<T> type) throws MapperException;

    /**
     * Map this row to an object using a {@link io.helidon.dbclient.DbMapper}.
     *
     * @param type type that supports generic declarations
     * @param <T>  type to be returned
     * @return typed row
     * @throws MapperException in case the mapping is not defined or fails
     * @throws MapperException in case the mapping is not defined or fails
     */
    <T> T as(GenericType<T> type) throws MapperException;

    /**
     * Get specific class instance representation of this row.
     * Mapper for target class is provided as an argument.
     *
     * @param <T>    type of the returned value
     * @param mapper method to create an target class instance from {@link DbRow}
     * @return instance of requested class containing this database row
     */
    <T> T as(Function<DbRow, T> mapper);

}
