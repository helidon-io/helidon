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

import java.util.function.Consumer;

/**
 * {@link io.helidon.db.DbStatementGeneric} execution result.
 * This is used when we do not know in advance whether we execute a query or a DML statement (such as insert, update, delete).
 * <p>
 * This class represents a future of two possible types - either a DML result returning the
 * number of changed rows (objects - depending on database type), or a query result returning the
 * {@link io.helidon.db.DbRowResult}.
 * <p>
 * One of the methods on this interface is called as soon as it is known what type of statement was
 * executed - for SQL this would be when we finish execution of the prepared statement.
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
     * For query statements, {@link io.helidon.db.DbRowResult} is provided as soon as the statement completes.
     * For example in SQL, this would be the time we get the ResultSet from the database. Nevertheless the
     * rows may not be read ({@link io.helidon.db.DbRowResult} itself represents a future of rows)
     *
     * @param consumer consumer that eventually processes the query result
     * @return DbResult to continue with processing a possible dml result
     */
    DbResult whenRs(Consumer<DbRowResult<DbRow>> consumer);

}
