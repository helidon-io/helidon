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
package io.helidon.data.jdbc.tests;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class ConfiguredBindingTest {

    @Test
    void missingAndAdditionalInputsFailBeforeAcquisitionIncludingNullableInputs() {
        DataSource source = mock(DataSource.class);
        for (String sql : List.of("select :name", "select :name, :email, :additional")) {
            ConfiguredBindingRepository repository = new ConfiguredBindingRepository__Jdbc(JdbcClient.builder()
                    .dataSource(source).statements(Map.of("find", sql)).build());
            if (sql.contains("additional")) {
                assertThrows(IllegalStateException.class, () -> repository.find("name", "email"));
                assertThrows(IllegalStateException.class, () -> repository.find(null, null));
            } else {
                assertThrows(IllegalArgumentException.class, () -> repository.find("name", "email"));
                assertThrows(IllegalArgumentException.class, () -> repository.find(null, null));
            }
        }
        verifyZeroInteractions(source);
    }

    @Test
    void renamedGeneratedParametersRemainBoundValues() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        String sql = "select ?, ?, ?, ?, ?";
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("mapped");
        JdbcClient client = JdbcClient.builder().dataSource(source).statements(Map.of("collision",
                "select :rows, :jdbcClient, :SQL_COLLISION, :result, :statement")).build();
        ConfiguredBindingRepository repository = new ConfiguredBindingRepository__Jdbc(client);
        assertThat(repository.collision("SQL_CANARY", "CLIENT_CANARY", "STATEMENT_CANARY", "RESULT", "ROWS"),
                is("mapped"));
        verify(statement).setString(1, "ROWS");
        verify(statement).setString(2, "CLIENT_CANARY");
        verify(statement).setString(3, "SQL_CANARY");
        verify(statement).setString(4, "RESULT");
        verify(statement).setString(5, "STATEMENT_CANARY");
        // Advancing the JDBC result channel closes the current result set under the JDBC contract.
        verify(statement).getMoreResults();
        verify(statement).close();
        verify(connection).close();
    }
}
