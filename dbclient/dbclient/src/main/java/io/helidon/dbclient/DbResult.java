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

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * {@link DbExecute#createNamedStatement(String)} (and other generic statements) execution result.
 * This is used when we do not know in advance whether we execute a query or a DML statement (such as insert, update, delete).
 * <p>
 * This class represents a future of two possible types - either a DML result returning the
 * number of changed rows (objects - depending on database type), or a query result returning the
 * {@link io.helidon.dbclient.DbRowResult}.
 * <p>
 * One of the consumers on this interface is called as soon as it is known what type of statement was
 * executed - for SQL this would be when we finish execution of the prepared statement.
 * <p>
 * Alternative (or in combination) is to use the methods that return {@link java.util.concurrent.CompletionStage}
 *  to process the results when (and if) they are done.
 */
public interface DbResult {
    /**
     * For DML statements, number of changed rows/objects is provided as soon as the statement completes.
     *
     * @param consumer consumer that eventually receives the count
     * @return DbResult to continue with processing a possible query result
     */
    DbResult whenDml(Consumer<Long> consumer);

    /**
     * For query statements, {@link io.helidon.dbclient.DbRowResult} is provided as soon as the statement completes.
     * For example in SQL, this would be the time we get the ResultSet from the database. Nevertheless the
     * rows may not be read ({@link io.helidon.dbclient.DbRowResult} itself represents a future of rows)
     *
     * @param consumer consumer that eventually processes the query result
     * @return DbResult to continue with processing a possible dml result
     */
    DbResult whenRs(Consumer<DbRowResult<DbRow>> consumer);

    /**
     * In case any exception occurs during processing, the handler is invoked.
     *
     * @param exceptionHandler handler to handle exceptional cases when processing the asynchronous request
     * @return DbResult ot continue with other methods
     */
    DbResult exceptionally(Consumer<Throwable> exceptionHandler);

    /**
     * This future completes if (and only if) the statement was a DML statement.
     * In case of any exception before the identification of statement type, all of
     * {@link #dmlFuture()}, {@link #rsFuture()} finish exceptionally, and {@link #exceptionFuture()} completes with the
     * exception.
     * In case the exception occurs after the identification of statement type, such as when
     * processing a result set of a query, only the {@link #exceptionFuture()}
     * completes. Exceptions that occur during processing of result set are handled by
     * methods in the {@link io.helidon.dbclient.DbRowResult}.
     *
     * @return future for the DML result
     */
    CompletionStage<Long> dmlFuture();

    /**
     * This future completes if (and only if) the statement was a query statement.
     * In case of any exception before the identification of statement type, all of
     * {@link #dmlFuture()}, {@link #rsFuture()} finish exceptionally, and {@link #exceptionFuture()} completes with the
     * exception.
     * In case the exception occurs after the identification of statement type, such as when
     * processing a result set of a query, only the {@link #exceptionFuture()}
     * completes. Exceptions that occur during processing of result set are handled by
     * methods in the {@link io.helidon.dbclient.DbRowResult}.
     *
     * @return future for the query result
     */
    CompletionStage<DbRowResult<DbRow>> rsFuture();

    /**
     * This future completes if (and only if) the statement finished with an exception, either
     * when executing the statement, or when processing the result set.
     *
     * @return future for an exceptional result
     */
    CompletionStage<Throwable> exceptionFuture();
}
