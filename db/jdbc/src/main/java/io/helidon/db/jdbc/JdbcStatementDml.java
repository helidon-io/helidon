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
package io.helidon.db.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

import io.helidon.common.mapper.MapperManager;
import io.helidon.db.DbMapperManager;

class JdbcStatementDml extends JdbcStatement<JdbcStatementDml, CompletionStage<Long>> {

    private final ExecutorService executorService;

    JdbcStatementDml(ConnectionPool config,
                     ExecutorService executorService,
                     String statementName,
                     String statement,
                     DbMapperManager dbMapperManager,
                     MapperManager mapperManager) {
        super(config, statementName, statement, dbMapperManager, mapperManager);
        this.executorService = executorService;
    }

    @Override
    public CompletionStage<Long> execute() {
        //TODO invoke interceptors

        CompletableFuture<Long> result = new CompletableFuture<>();
        executorService.submit(() -> {
            try (Connection connection = connection()) {
                PreparedStatement preparedStatement = build(connection);
                result.complete(preparedStatement.executeLargeUpdate());
                preparedStatement.close();
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

}
