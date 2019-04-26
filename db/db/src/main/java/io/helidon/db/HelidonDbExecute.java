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
package io.helidon.db;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Database executor.
 * <p>The database executor provides methods to create {@link DbStatement} instances for different types
 * of database statements.</p>
 */
public interface HelidonDbExecute {

    /**
     * Create a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return database statement that can process query returning multiple rows
     */
    DbStatement<DbRowResult<DbRow>> createNamedQuery(String statementName);

    /**
     * Create a database query using a statement passed as an argument.
     *
     * @param statement the query statement to be executed
     * @return database statement that can process the query returning multiple rows
     */
    DbStatement<DbRowResult<DbRow>> createQuery(String statement);

    /**
     * Create a database query returning a single row using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return database statement that can process query returning a single row
     */
    DbStatement<CompletionStage<Optional<DbRow>>> createNamedGet(String statementName);

    /**
     * Create a database query returning a single row using a statement passed as an argument.
     *
     * @param statement the query statement to be executed
     * @return database statement that can process query returning a single row
     */
    DbStatement<CompletionStage<Optional<DbRow>>> createGet(String statement);

    /**
     * Create a generic database statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return generic database statement that can return any result
     */
    DbStatement<DbResult> createNamedStatement(String statementName);

    /**
     * Create a generic database statement using a statement passed as an argument.
     *
     * @param statement the statement to be executed
     * @return generic database statement that can return any result
     */
    DbStatement<DbResult> createStatement(String statement);

    /**
     * Create a data modification statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return data modification statement
     */
    DbStatement<CompletionStage<Long>> createNamedDmlStatement(String statementName);

    /**
     * Create a data modification statement using a statement passed as an argument.
     *
     * @param statement the data modification statement to be executed
     * @return data modification statement
     */
    DbStatement<CompletionStage<Long>> createDmlStatement(String statement);

    /**
     * Create and execute a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return database query execution result which can contain multiple rows
     */
    default DbRowResult<DbRow> namedQuery(String statementName, Object... parameters) {
        return createNamedQuery(statementName).params(parameters).execute();
    }

    /**
     * Create and execute a database query using a statement passed as an argument.
     *
     * @param statement  the query statement to be executed
     * @param parameters query parameters to set
     * @return database query execution result which can contain multiple rows
     */
    default DbRowResult<DbRow> query(String statement, Object... parameters) {
        return createQuery(statement).params(parameters).execute();
    }

    /**
     * Create and execute a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return database query execution result which can contain single row
     */
    default CompletionStage<Optional<DbRow>> namedGet(String statementName, Object... parameters) {
        return createNamedGet(statementName).params(parameters).execute();
    }

    /**
     * Create and execute a database query using a statement passed as an argument.
     *
     * @param statement  the query statement to be executed
     * @param parameters query parameters to set
     * @return database query execution result which can contain single row
     */
    default CompletionStage<Optional<DbRow>> get(String statement, Object... parameters) {
        return createGet(statement).params(parameters).execute();
    }

    /**
     * Create and execute insert statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows inserted into the database
     */
    default CompletionStage<Long> namedInsert(String statementName, Object... parameters) {
        return createNamedDmlStatement(statementName).params(parameters).execute();
    }

    /**
     * Create and execute insert statement using a statement passed as an argument.
     *
     * @param statement  the insert statement to be executed
     * @param parameters query parameters to set
     * @return number of rows inserted into the database
     */
    default CompletionStage<Long> insert(String statement, Object... parameters) {
        return createDmlStatement(statement).params(parameters).execute();
    }

    /**
     * Create and execute update statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows updated in the database
     */
    default CompletionStage<Long> namedUpdate(String statementName, Object... parameters) {
        return createNamedDmlStatement(statementName).params(parameters).execute();
    }

    /**
     * Create and execute update statement using a statement passed as an argument.
     *
     * @param statement  the update statement to be executed
     * @param parameters query parameters to set
     * @return number of rows updated in the database
     */
    default CompletionStage<Long> update(String statement, Object... parameters) {
        return createDmlStatement(statement).params(parameters).execute();
    }

    /**
     * Create and execute delete statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows deleted from the database
     */
    default CompletionStage<Long> namedDelete(String statementName, Object... parameters) {
        return createNamedDmlStatement(statementName).params(parameters).execute();
    }

    /**
     * Create and execute delete statement using a statement passed as an argument.
     *
     * @param statement  the delete statement to be executed
     * @param parameters query parameters to set
     * @return number of rows deleted from the database
     */
    default CompletionStage<Long> delete(String statement, Object... parameters) {
        return createDmlStatement(statement).params(parameters).execute();
    }

    /**
     * Create and execute common statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return generic statement execution result
     */
    default DbResult namedStatement(String statementName, Object... parameters) {
        return createNamedStatement(statementName).params(parameters).execute();
    }

    /**
     * Create and execute common statement using a statement passed as an argument.
     *
     * @param statement  the statement to be executed
     * @param parameters query parameters to set
     * @return generic statement execution result
     */
    default DbResult statement(String statement, Object... parameters) {
        return createStatement(statement).params(parameters).execute();
    }

}
