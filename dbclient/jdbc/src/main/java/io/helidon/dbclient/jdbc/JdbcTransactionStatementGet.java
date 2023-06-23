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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementGet;

class JdbcTransactionStatementGet extends JdbcTransactionStatement<DbStatementGet> implements DbStatementGet {

    private JdbcTransactionStatementGet(StatementContext context, TransactionContext transactionContext) {
        super(context, transactionContext);
    }

    @Override
    public Optional<DbRow> execute() {
        Connection connection = transactionContext().connection();
        try (Statement statement = prepare().createStatement(connection);
                ResultSet rs = prepare().executeQuery()) {
            if (rs.next()) {
                return Optional.of(JdbcRow.create(rs,
                                                  context().dbMapperManager(),
                                                  context().mapperManager()));
            } else {
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to execute Statement", context().statement(), ex);
        }
    }

    static JdbcTransactionStatementGet create(StatementContext context, TransactionContext transactionContext) {
        return new JdbcTransactionStatementGet(context, transactionContext);
    }

}
