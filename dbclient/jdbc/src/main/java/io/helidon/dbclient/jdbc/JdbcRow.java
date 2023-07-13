/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.helidon.dbclient.DbContext;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowBase;
import io.helidon.dbclient.DbStatementException;

/**
 * JDBC {@link io.helidon.dbclient.DbRow} implementation.
 */
class JdbcRow extends DbRowBase {

    private JdbcRow(JdbcColumn[] columns, DbMapperManager dbMapperManager) {
        super(columns, dbMapperManager);
    }

    /**
     * Create single row from provided {@link ResultSet}.
     * New {@link JdbcColumn.MetaData} array is built and added to columns.
     * All values and metadata are taken without changing current cursor position.
     *
     * @param rs {@link ResultSet} with cursor set to row to be processed
     * @return updated builder instance.
     * @throws SQLException if a database access error occurs or this method is called on a closed result set
     */
    static JdbcRow create(ResultSet rs, DbContext context) throws SQLException {
        ResultSetMetaData rsMetaData = rs.getMetaData();
        int columnCount = rsMetaData.getColumnCount();
        JdbcColumn.MetaData[] metaData = new JdbcColumn.MetaData[columnCount];
        for (int i = 0; i < columnCount; i++) {
            metaData[i] = JdbcColumn.MetaData.create(rsMetaData, i + 1);
        }
        JdbcColumn[] columns = new JdbcColumn[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columns[i] = JdbcColumn.create(rs, metaData[i], context.mapperManager(), i + 1);
        }
        return new JdbcRow(columns, context.dbMapperManager());
    }

    /**
     * {@link java.util.Spliterator} implementation that supports {@link DbRow}.
     */
    static final class Spliterator extends Spliterators.AbstractSpliterator<DbRow> {

        private final ResultSet rs;
        private final Statement statement;
        private final DbExecuteContext context;
        private final CompletableFuture<Long> queryFuture;
        private long count;

        /**
         * Create a new instance.
         *
         * @param rs          result set
         * @param statement   statement
         * @param context     execution context
         * @param queryFuture query future
         */
        Spliterator(ResultSet rs,
                    Statement statement,
                    DbExecuteContext context,
                    CompletableFuture<Long> queryFuture) {
            super(Long.MAX_VALUE, java.util.Spliterator.ORDERED);
            this.rs = rs;
            this.context = context;
            this.statement = statement;
            this.queryFuture = queryFuture;
            this.count = 0L;
        }

        @Override
        public boolean tryAdvance(Consumer<? super DbRow> action) {
            try {
                if (rs.next()) {
                    action.accept(create(rs, context));
                    count++;
                    return true;
                } else {
                    return false;
                }
            } catch (SQLException ex) {
                throw new DbStatementException("Failed to retrieve next row from ResultSet",
                        context.statement(),
                        ex);
            }
        }

        /**
         * Close the query.
         */
        void close() {
            try {
                queryFuture.complete(count);
                rs.close();
            } catch (SQLException ex) {
                throw new DbStatementException("Failed to close ResultSet", context.statement(), ex);
            } finally {
                closeStatement();
            }
        }

        private void closeStatement() {
            try {
                statement.close();
            } catch (SQLException ex) {
                throw new DbStatementException("Failed to close Statement", context.statement(), ex);
            }
        }
    }
}
