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
package io.helidon.data.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcPublicBoundaryNullValidationTest {

    private static final String SQL = "UPDATE TEST_VALUE SET VALUE = VALUE";

    /**
     * Verifies that a null generated column is rejected before the finalized
     * generated key stage reports its lifecycle state.
     */
    @Test
    void validatesGeneratedColumnBeforeStageState() {
        JdbcClient.GeneratedKeys generatedKeys = newStatement().generatedKeys();
        generatedKeys.map(_ -> "value");

        assertNullFailure("The generated column name must not be null.",
                          () -> generatedKeys.addColumn(null));
    }

    /**
     * Verifies that a null generated key mapper is rejected before the
     * finalized generated key stage reports its lifecycle state.
     */
    @Test
    void validatesGeneratedKeyMapperBeforeStageState() {
        JdbcClient.GeneratedKeys generatedKeys = newStatement().generatedKeys();
        generatedKeys.map(_ -> "value");

        assertNullFailure("The generated key mapper must not be null.",
                          () -> generatedKeys.map(null));
    }

    /**
     * Verifies that query mapper arguments are rejected before a completed
     * statement reports its terminal lifecycle state.
     */
    @Test
    void validatesQueryMappersBeforeStatementState() {
        JdbcStatement statement = terminalStatement();

        assertNullFailure("The row mapper must not be null.",
                          () -> statement.map((JdbcClient.RowMapper<Object>) null));
        assertNullFailure("The scalar type must not be null.",
                          () -> statement.map((Class<Object>) null));
    }

    /**
     * Verifies that row reference arguments are rejected before an expired
     * row reports that its callback scope has ended.
     */
    @Test
    void validatesRowArgumentsBeforeRowState() throws SQLException {
        JdbcRow row = expiredRow();

        assertNullFailure("The target type must not be null.", () -> row.optional(1, null));
        assertNullFailure("The column label must not be null.", () -> row.optional((String) null, String.class));
        assertNullFailure("The target type must not be null.", () -> row.optional("VALUE", null));
        assertNullFailure("The target type must not be null.", () -> row.get(1, null));
        assertNullFailure("The column label must not be null.", () -> row.get((String) null, String.class));
        assertNullFailure("The target type must not be null.", () -> row.get("VALUE", null));
    }

    /**
     * Creates a statement whose terminal execution fails before acquiring a
     * connection while still claiming the statement stage.
     *
     * @return claimed statement stage
     */
    private static JdbcStatement terminalStatement() {
        JdbcStatement statement = newStatement();
        assertThrows(DataException.class, statement::execute);
        return statement;
    }

    /**
     * Creates a statement whose runner cannot acquire a lease or access a database.
     *
     * @return statement under test
     */
    private static JdbcStatement newStatement() {
        DataSource dataSource = mock(DataSource.class);
        JdbcConnectionLease.Provider unavailable = _ -> {
            throw new DataException("The test connection is unavailable.");
        };
        return new JdbcStatement(new JdbcRunner(dataSource, unavailable), SQL, 0);
    }

    /**
     * Creates a row whose callback scope has already ended.
     *
     * @return expired row under test
     * @throws SQLException when mocked result metadata cannot be read
     */
    private static JdbcRow expiredRow() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        JdbcOperation operation = new JdbcOperation("SELECT VALUE",
                                                    new JdbcOperation.Bind[0],
                                                    JdbcPreparationPlan.query());
        JdbcRow row = new JdbcRow(resultSet, JdbcColumnLayout.create(metadata, operation), operation);
        row.expire();
        return row;
    }

    /**
     * Verifies a public boundary reports the expected null failure.
     *
     * @param message expected diagnostic
     * @param executable boundary invocation
     */
    private static void assertNullFailure(String message, Executable executable) {
        NullPointerException failure = assertThrows(NullPointerException.class, executable);
        assertThat(failure.getMessage(), is(message));
    }
}
