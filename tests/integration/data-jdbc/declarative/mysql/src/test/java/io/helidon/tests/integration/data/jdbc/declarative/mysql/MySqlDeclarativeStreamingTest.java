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
package io.helidon.tests.integration.data.jdbc.declarative.mysql;

import java.sql.Connection;

import io.helidon.config.Config;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcStreamingContract;

import com.mysql.cj.jdbc.result.ResultSetInternalMethods;
import com.mysql.cj.protocol.a.result.ResultsetRowsCursor;
import com.mysql.cj.protocol.a.result.ResultsetRowsStatic;
import com.mysql.cj.protocol.a.result.ResultsetRowsStreaming;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes the streaming contract using the real MySql driver.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlDeclarativeStreamingTest extends AbstractJdbcStreamingContract {
    @Container
    static final MySQLContainer<?> DATABASE = MySqlDeclarativeTestSupport.MYSQL;

    @Override
    protected Config databaseConfig() {
        return MySqlDeclarativeTestSupport.config();
    }

    @Override
    protected boolean declarative() {
        return true;
    }
    /**
     * Driver row containers distinguish buffered, cursor fetching and row streaming modes.
     */
    @Override
    protected void verifyDriverFetching(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("SELECT DATA_VALUE FROM ST_VALUE ORDER BY ID")) {
            for (int size : new int[] {0, 16, Integer.MIN_VALUE}) {
                statement.setFetchSize(size);
                try (var result = statement.executeQuery()) {
                    var rows = result.unwrap(ResultSetInternalMethods.class).getRows();
                    switch (size) {
                    case 0 -> assertThat(rows instanceof ResultsetRowsStatic, is(true));
                    case 16 -> assertThat(rows instanceof ResultsetRowsCursor, is(true));
                    case Integer.MIN_VALUE -> assertThat(rows instanceof ResultsetRowsStreaming, is(true));
                    default -> throw new AssertionError();
                    }
                    assertThat(result.next(), is(true));
                }
            }
        }
    }

}
