/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.common.DbStatementContext;

class JdbcStatementDml extends JdbcStatement<DbStatementDml, Single<Long>> implements DbStatementDml {

    JdbcStatementDml(JdbcExecuteContext executeContext,
                     DbStatementContext statementContext) {
        super(executeContext, statementContext);
    }

    @Override
    protected Single<Long> doExecute(Single<DbClientServiceContext> dbContextFuture,
                                     CompletableFuture<Void> statementFuture,
                                     CompletableFuture<Long> queryFuture) {

        executeContext().addFuture(queryFuture);

        // query and statement future must always complete either OK, or exceptionally
        dbContextFuture.exceptionally(throwable -> {
            statementFuture.completeExceptionally(throwable);
            queryFuture.completeExceptionally(throwable);
            return null;
        });

        return dbContextFuture
                .flatMapSingle(dbContext -> doExecute(dbContext, statementFuture, queryFuture));
    }

    private Single<Long> doExecute(DbClientServiceContext dbContext,
                                   CompletableFuture<Void> statementFuture,
                                   CompletableFuture<Long> queryFuture) {

        return Single.create(connection())
                .flatMapSingle(connection -> doExecute(dbContext, connection, statementFuture, queryFuture));
    }

    private Single<Long> doExecute(DbClientServiceContext dbContext,
                                   Connection connection,
                                   CompletableFuture<Void> statementFuture,
                                   CompletableFuture<Long> queryFuture) {

        executorService().submit(() -> callStatement(dbContext, connection, statementFuture, queryFuture));

        // the query future is reused, as it completes with the number of updated records
        return Single.create(queryFuture);
    }

    private void callStatement(DbClientServiceContext dbContext,
                               Connection connection,
                               CompletableFuture<Void> statementFuture,
                               CompletableFuture<Long> queryFuture) {
        try {
            PreparedStatement preparedStatement = build(connection, dbContext);
            long count = preparedStatement.executeLargeUpdate();
            statementFuture.complete(null);
            queryFuture.complete(count);
            preparedStatement.close();
        } catch (Exception e) {
            statementFuture.completeExceptionally(e);
            queryFuture.completeExceptionally(e);
        }
    }
}
