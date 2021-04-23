/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

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
    DbStatementQuery createNamedQuery(String statementName, String statement);

    /**
     * Create a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return database statement that can process query returning multiple rows
     */
    DbStatementQuery createNamedQuery(String statementName);

    /**
     * Create a database query using a statement passed as an argument.
     *
     * @param statement the query statement to be executed
     * @return database statement that can process the query returning multiple rows
     */
    DbStatementQuery createQuery(String statement);

    /**
     * Create and execute a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return database query execution result which can contain multiple rows
     */
    default Multi<DbRow> namedQuery(String statementName, Object... parameters) {
        return createNamedQuery(statementName).params(parameters).execute();
    }

    /**
     * Create and execute a database query using a statement passed as an argument.
     *
     * @param statement  the query statement to be executed
     * @param parameters query parameters to set
     * @return database query execution result which can contain multiple rows
     */
    default Multi<DbRow> query(String statement, Object... parameters) {
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
    DbStatementGet createNamedGet(String statementName, String statement);

    /**
     * Create a database query returning a single row using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return database statement that can process query returning a single row
     */
    DbStatementGet createNamedGet(String statementName);

    /**
     * Create a database query returning a single row using a statement passed as an argument.
     *
     * @param statement the query statement to be executed
     * @return database statement that can process query returning a single row
     */
    DbStatementGet createGet(String statement);

    /**
     * Create and execute a database query using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return database query execution result which can contain single row
     */
    default Single<Optional<DbRow>> namedGet(String statementName, Object... parameters) {
        return createNamedGet(statementName).params(parameters).execute();
    }

    /**
     * Create and execute a database query using a statement passed as an argument.
     *
     * @param statement  the query statement to be executed
     * @param parameters query parameters to set
     * @return database query execution result which can contain single row
     */
    default Single<Optional<DbRow>> get(String statement, Object... parameters) {
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
    default DbStatementDml createNamedInsert(String statementName, String statement) {
        return createNamedDmlStatement(statementName, statement);
    }

    /**
     * Create an insert statement using a named statement.
     *
     * @param statementName the name of the statement
     * @return database statement that can insert data
     */
    DbStatementDml createNamedInsert(String statementName);

    /**
     * Create an insert statement using a statement text.
     *
     * @param statement the statement text
     * @return database statement that can insert data
     */
    DbStatementDml createInsert(String statement);

    /**
     * Create and execute insert statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows inserted into the database
     */
    default Single<Long> namedInsert(String statementName, Object... parameters) {
        return createNamedInsert(statementName).params(parameters).execute();
    }

    /**
     * Create and execute insert statement using a statement passed as an argument.
     *
     * @param statement  the insert statement to be executed
     * @param parameters query parameters to set
     * @return number of rows inserted into the database
     */
    default Single<Long> insert(String statement, Object... parameters) {
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
    default DbStatementDml createNamedUpdate(String statementName, String statement) {
        return createNamedDmlStatement(statementName, statement);
    }

    /**
     * Create an update statement using a named statement.
     *
     * @param statementName the name of the statement
     * @return database statement that can update data
     */
    DbStatementDml createNamedUpdate(String statementName);

    /**
     * Create an update statement using a statement text.
     *
     * @param statement the statement text
     * @return database statement that can update data
     */
    DbStatementDml createUpdate(String statement);

    /**
     * Create and execute update statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows updateed into the database
     */
    default Single<Long> namedUpdate(String statementName, Object... parameters) {
        return createNamedUpdate(statementName).params(parameters).execute();
    }

    /**
     * Create and execute update statement using a statement passed as an argument.
     *
     * @param statement  the update statement to be executed
     * @param parameters query parameters to set
     * @return number of rows updateed into the database
     */
    default Single<Long> update(String statement, Object... parameters) {
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
    default DbStatementDml createNamedDelete(String statementName, String statement) {
        return createNamedDmlStatement(statementName, statement);
    }

    /**
     * Create andelete statement using a named statement.
     *
     * @param statementName the name of the statement
     * @return database statement that can delete data
     */
    DbStatementDml createNamedDelete(String statementName);

    /**
     * Create a delete statement using a statement text.
     *
     * @param statement the statement text
     * @return database statement that can delete data
     */
    DbStatementDml createDelete(String statement);

    /**
     * Create and execute delete statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows deleted from the database
     */
    default Single<Long> namedDelete(String statementName, Object... parameters) {
        return createNamedDelete(statementName).params(parameters).execute();
    }

    /**
     * Create and execute delete statement using a statement passed as an argument.
     *
     * @param statement  the delete statement to be executed
     * @param parameters query parameters to set
     * @return number of rows deleted from the database
     */
    default Single<Long> delete(String statement, Object... parameters) {
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
    DbStatementDml createNamedDmlStatement(String statementName, String statement);

    /**
     * Create a data modification statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @return data modification statement
     */
    DbStatementDml createNamedDmlStatement(String statementName);

    /**
     * Create a data modification statement using a statement passed as an argument.
     *
     * @param statement the data modification statement to be executed
     * @return data modification statement
     */
    DbStatementDml createDmlStatement(String statement);

    /**
     * Create and execute a data modification statement using a statement defined in the configuration file.
     *
     * @param statementName the name of the configuration node with statement
     * @param parameters    query parameters to set
     * @return number of rows modified
     */
    default Single<Long> namedDml(String statementName, Object... parameters) {
        return createNamedDmlStatement(statementName).params(parameters).execute();
    }

    /**
     * Create and execute data modification statement using a statement passed as an argument.
     *
     * @param statement  the delete statement to be executed
     * @param parameters query parameters to set
     * @return number of rows modified
     */
    default Single<Long> dml(String statement, Object... parameters) {
        return createDmlStatement(statement).params(parameters).execute();
    }

    /*
     * Unwrap support
     */

    /**
     * Unwrap database executor internals.
     * Only database connection is supported. Any operations based on this connection are <b>blocking</b>.
     * Reactive support must be implemented in user code. This connection instance is being used to execute
     * all statements in current database executor context.
     * Mongo database driver internals support {@code MongoDatabase} class.
     *
     * @param <C> target class to be unwrapped
     * @param cls target class to be unwrapped
     * @return database executor internals future matching provided class
     * @throws UnsupportedOperationException when provided class is not supported
     */
    <C> Single<C> unwrap(Class<C> cls);

}
