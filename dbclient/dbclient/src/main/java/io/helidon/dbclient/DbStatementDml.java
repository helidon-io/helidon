/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

import java.util.List;

/**
 * Data Manipulation Language (DML) database statement.
 * A DML statement modifies records in the database and returns the number of modified records.
 */
public interface DbStatementDml extends DbStatement<DbStatementDml> {

    /**
     * Execute this statement using the parameters configured with {@code params} and {@code addParams} methods.
     *
     * @return the result of this statement
     */
    long execute();

    /**
     * Execute {@code INSERT} statement using the parameters configured with {@code params} and {@code addParams} methods
     * and return compound result with generated keys.
     *
     * @return the result of this statement with generated keys
     */
    DbResultDml insert();

    /**
     * Set auto-generated keys to be returned from the statement execution using {@link #insert()}.
     * Only one method from {@link #returnGeneratedKeys()} and {@link #returnColumns(List)} may be used.
     * This feature is database provider specific and some databases require specific columns to be set.
     *
     * @return updated db statement
     */
    DbStatementDml returnGeneratedKeys();

    /**
     * Set column names to be returned from the inserted row or rows from the statement execution using {@link #insert()}.
     * Only one method from {@link #returnGeneratedKeys()} and {@link #returnColumns(List)} may be used.
     * This feature is database provider specific.
     *
     * @param columnNames an array of column names indicating the columns that should be returned from the inserted row or rows
     * @return updated db statement
     */
    DbStatementDml returnColumns(List<String> columnNames);

}
