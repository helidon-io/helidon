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
import java.util.concurrent.CompletionStage;

/**
 * Database executor.
 * <p>The database executor provides methods to create {@link DbStatement} instances for different types
 * of database statements.</p>
 * <p>The recommended approach is to use named statements, as that allows better metrics, tracing, logging etc.
 * In case an unnamed statement is used, a name must be generated
 * <p>There are five methods for each {@link DbStatementType}, example for query (the implementation
 * detail is for the default implementation, providers may differ):
 * <ol>
 *     <li>{@code DbStatement} {@link #createNamedQuery(String, String)} - full control over the name and content of the
 *     statement</li>
 *     <li>{@code DbStatement} {@link #createNamedQuery(String)} - use statement text from configuration</li>
 *     <li>{@code DbStatement} {@link #createQuery(String)} - use the provided statement, name is generated </li>
 *     <li>{@code DbRowResult} {@link #namedQuery(String, Object...)} - shortcut method to a named query with a list of
 *     parameters (or with no parameters at all)</li>
 *     <li>{@code DbRowResult} {@link #query(String, Object...)} - shortcut method to unnamed query with a list of parameters
 *     (or with no parameters at all)</li>
 * </ol>
 * The first three methods return a statement that can have parameters configured (and other details modified).
 * The last two methods directly execute the statement and provide appropriate response for future processing.
 * All the methods are non-blocking.
 */
public interface DbExecute {
    /*
     * QUERY
     */

    /**
     * Create a database query using a named statement passed as argument.
     *
     * @param statementName the name of the statement
     * @param statement the query statement
     * @return database statement that can process query returning multiple rows
     */
    DbStatement<?, DbRowResult<DbRow>> createNamedQuery(String statementName, String statement);

    /**
     * Create a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return database statement that can process query returning multiple rows
     */
    DbStatement<?, DbRowResult<DbRow>> createNamedQuery(String statementName);

    /**
     * Create a database query using a statement passed as an argument.
     *
     * @param statement the query statement to be executed
     * @return database statement that can process the query returning multiple rows
     */
    DbStatement<?, DbRowResult<DbRow>> createQuery(String statement);

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

    /*
     * GET
     */

    /**
     * Create a database query returning a single row using a named statement passed as an argument.
     *
     * @param statementName the name of the statement
     * @param statement the statement text
     * @return database statement that can process query returning a single row
     */
    DbStatement<?, CompletionStage<Optional<DbRow>>> createNamedGet(String statementName, String statement);

    /**
     * Create a database query returning a single row using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return database statement that can process query returning a single row
     */
    DbStatement<?, CompletionStage<Optional<DbRow>>> createNamedGet(String statementName);

    /**
     * Create a database query returning a single row using a statement passed as an argument.
     *
     * @param statement the query statement to be executed
     * @return database statement that can process query returning a single row
     */
    DbStatement<?, CompletionStage<Optional<DbRow>>> createGet(String statement);

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

    /*
     * INSERT
     */

    /**
     * Create an insert statement using a named statement passed as an argument.
     *
     * @param statementName the name of the statement
     * @param statement the statement text
     * @return database statement that can insert data
     */
    default DbStatement<?, CompletionStage<Long>> createNamedInsert(String statementName, String statement) {
        return createNamedDmlStatement(statementName, statement);
    }

    /**
     * Create an insert statement using a named statement.
     *
     * @param statementName the name of the statement
     * @return database statement that can insert data
     */
    DbStatement<?, CompletionStage<Long>> createNamedInsert(String statementName);

    /**
     * Create an insert statement using a statement text.
     *
     * @param statement the statement text
     * @return database statement that can insert data
     */
    DbStatement<?, CompletionStage<Long>> createInsert(String statement);

    /**
     * Create and execute insert statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows inserted into the database
     */
    default CompletionStage<Long> namedInsert(String statementName, Object... parameters) {
        return createNamedInsert(statementName).params(parameters).execute();
    }

    /**
     * Create and execute insert statement using a statement passed as an argument.
     *
     * @param statement  the insert statement to be executed
     * @param parameters query parameters to set
     * @return number of rows inserted into the database
     */
    default CompletionStage<Long> insert(String statement, Object... parameters) {
        return createInsert(statement).params(parameters).execute();
    }

    /*
     * UPDATE
     */

    /**
     * Create an update statement using a named statement passed as an argument.
     *
     * @param statementName the name of the statement
     * @param statement the statement text
     * @return database statement that can update data
     */
    default DbStatement<?, CompletionStage<Long>> createNamedUpdate(String statementName, String statement) {
        return createNamedDmlStatement(statementName, statement);
    }

    /**
     * Create an update statement using a named statement.
     *
     * @param statementName the name of the statement
     * @return database statement that can update data
     */
    DbStatement<?, CompletionStage<Long>> createNamedUpdate(String statementName);

    /**
     * Create an update statement using a statement text.
     *
     * @param statement the statement text
     * @return database statement that can update data
     */
    DbStatement<?, CompletionStage<Long>> createUpdate(String statement);

    /**
     * Create and execute update statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows updateed into the database
     */
    default CompletionStage<Long> namedUpdate(String statementName, Object... parameters) {
        return createNamedUpdate(statementName).params(parameters).execute();
    }

    /**
     * Create and execute update statement using a statement passed as an argument.
     *
     * @param statement  the update statement to be executed
     * @param parameters query parameters to set
     * @return number of rows updateed into the database
     */
    default CompletionStage<Long> update(String statement, Object... parameters) {
        return createUpdate(statement).params(parameters).execute();
    }

    /*
     * DELETE
     */

    /**
     * Create a delete statement using a named statement passed as an argument.
     *
     * @param statementName the name of the statement
     * @param statement the statement text
     * @return database statement that can delete data
     */
    default DbStatement<?, CompletionStage<Long>> createNamedDelete(String statementName, String statement) {
        return createNamedDmlStatement(statementName, statement);
    }

    /**
     * Create andelete statement using a named statement.
     *
     * @param statementName the name of the statement
     * @return database statement that can delete data
     */
    DbStatement<?, CompletionStage<Long>> createNamedDelete(String statementName);

    /**
     * Create a delete statement using a statement text.
     *
     * @param statement the statement text
     * @return database statement that can delete data
     */
    DbStatement<?, CompletionStage<Long>> createDelete(String statement);

    /**
     * Create and execute delete statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows deleted from the database
     */
    default CompletionStage<Long> namedDelete(String statementName, Object... parameters) {
        return createNamedDelete(statementName).params(parameters).execute();
    }

    /**
     * Create and execute delete statement using a statement passed as an argument.
     *
     * @param statement  the delete statement to be executed
     * @param parameters query parameters to set
     * @return number of rows deleted from the database
     */
    default CompletionStage<Long> delete(String statement, Object... parameters) {
        return createDelete(statement).params(parameters).execute();
    }

    /*
     * DML
     */

    /**
     * Create a data modification statement using a named statement passed as an argument.
     *
     * @param statementName the name of the statement
     * @param statement the statement text
     * @return data modification statement
     */
    DbStatement<?, CompletionStage<Long>> createNamedDmlStatement(String statementName, String statement);

    /**
     * Create a data modification statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return data modification statement
     */
    DbStatement<?, CompletionStage<Long>> createNamedDmlStatement(String statementName);

    /**
     * Create a data modification statement using a statement passed as an argument.
     *
     * @param statement the data modification statement to be executed
     * @return data modification statement
     */
    DbStatement<?, CompletionStage<Long>> createDmlStatement(String statement);

    /**
     * Create and execute a data modification statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows modified
     */
    default CompletionStage<Long> namedDml(String statementName, Object... parameters) {
        return createNamedDelete(statementName).params(parameters).execute();
    }

    /**
     * Create and execute data modification statement using a statement passed as an argument.
     *
     * @param statement  the delete statement to be executed
     * @param parameters query parameters to set
     * @return number of rows modified
     */
    default CompletionStage<Long> dml(String statement, Object... parameters) {
        return createDelete(statement).params(parameters).execute();
    }

    /*
     * UNKNOWN
     */

    /**
     * Create a generic database statement using a named statement passed as an argument.
     *
     * @param statementName the name of the statement
     * @param statement the statement text
     * @return generic database statement that can return any result
     */
    DbStatement<?, DbResult> createNamedStatement(String statementName, String statement);

    /**
     * Create a generic database statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return generic database statement that can return any result
     */
    DbStatement<?, DbResult> createNamedStatement(String statementName);

    /**
     * Create a generic database statement using a statement passed as an argument.
     *
     * @param statement the statement to be executed
     * @return generic database statement that can return any result
     */
    DbStatement<?, DbResult> createStatement(String statement);

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
