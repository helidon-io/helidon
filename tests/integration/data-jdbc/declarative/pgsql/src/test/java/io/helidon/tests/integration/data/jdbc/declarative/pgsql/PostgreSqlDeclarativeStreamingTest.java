/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.data.jdbc.declarative.pgsql;

import java.sql.Connection;

import io.helidon.config.Config;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcStreamingContract;

import org.postgresql.jdbc.PgResultSet;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes the streaming contract using the real PostgreSql driver.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlDeclarativeStreamingTest extends AbstractJdbcStreamingContract {
    @Container
    static final JdbcDatabaseContainer<?> DATABASE = PostgreSqlDeclarativeTestSupport.POSTGRESQL;

    @Override
    protected Config databaseConfig() {
        return PostgreSqlDeclarativeTestSupport.config();
    }

    @Override
    protected boolean declarative() {
        return true;
    }
    /**
     * The driver reports buffered fetches in auto commit and blocks within an explicit transaction.
     */
    @Override
    protected void verifyDriverFetching(Connection connection) throws Exception {
        for (boolean autoCommit : new boolean[] {true, false}) {
            connection.setAutoCommit(autoCommit);
            try (var statement = connection.prepareStatement("SELECT DATA_VALUE FROM ST_VALUE ORDER BY ID");
                 var result = statement.executeQuery()) {
                assertThat(result.next(), is(true));
                assertThat(result.unwrap(PgResultSet.class).getLastUsedFetchSize(), is(autoCommit ? 4096 : 16));
            } finally {
                if (!autoCommit) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            }
        }
    }

}
