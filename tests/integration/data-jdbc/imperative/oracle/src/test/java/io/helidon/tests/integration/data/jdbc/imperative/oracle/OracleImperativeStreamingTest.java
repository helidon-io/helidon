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
package io.helidon.tests.integration.data.jdbc.imperative.oracle;

import java.sql.Connection;

import io.helidon.config.Config;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcStreamingContract;

import oracle.jdbc.OracleStatement;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes the streaming contract using the real Oracle driver.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleImperativeStreamingTest extends AbstractJdbcStreamingContract {
    @Container
    static final GenericContainer<?> DATABASE = OracleImperativeTestSupport.ORACLE;

    @Override
    protected Config databaseConfig() {
        return OracleImperativeTestSupport.config();
    }

    @Override
    protected boolean declarative() {
        return false;
    }
    /**
     * Explicit datasource prefetch reaches both the Oracle statement and its result set.
     */
    @Override
    protected void verifyDriverFetching(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("SELECT DATA_VALUE FROM ST_VALUE ORDER BY ID")) {
            assertThat(statement.unwrap(OracleStatement.class).getRowPrefetch(), is(16));
            try (var result = statement.executeQuery()) {
                assertThat(result.getFetchSize(), is(16));
                assertThat(result.next(), is(true));
            }
        }
    }

}
