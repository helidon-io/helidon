/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.dbclient.DbStatementType;

/**
 * Stuff needed by each and every statement.
 */
class JdbcStatementContext {

    private final DbStatementType statementType;
    private final String statementName;
    private final String statement;

    private JdbcStatementContext(DbStatementType statementType, String statementName, String statement) {
        this.statementType = statementType;
        this.statementName = statementName;
        this.statement = statement;
    }

    static JdbcStatementContext create(DbStatementType statementType, String statementName, String statement) {
        return new JdbcStatementContext(statementType, statementName, statement);
    }

    DbStatementType statementType() {
        return statementType;
    }

    String statementName() {
        return statementName;
    }

    String statement() {
        return statement;
    }

}
