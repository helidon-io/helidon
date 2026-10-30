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
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedAliasBindingTest {
    /**
     * Executes generated aliases through the provider and verifies typed setters and operation resource ownership.
     */
    @Test
    void bindsNullAtEveryPhysicalPositionAndClosesOwnedResources() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("UPDATE CONTACT SET NAME = ?, LABEL = ? WHERE ID = ?")).thenReturn(statement);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        AliasBindingRepository repository = new AliasBindingRepository__Jdbc(client);

        verify(source, never()).getConnection();
        assertThat(repository.update(42L, null), is(1L));
        verify(statement).setNull(1, Types.VARCHAR);
        verify(statement).setNull(2, Types.VARCHAR);
        verify(statement).setLong(3, 42L);
        verify(statement).execute();
        verify(statement).close();
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    /**
     * Preserves bound values through generated identifier renaming and cleans up after a setter failure.
     */
    @Test
    void preservesValuesAndClosesResourcesOnBindingFailure() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("UPDATE CONTACT SET NAME = ?, LABEL = ? WHERE ID = ?")).thenReturn(statement);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        AliasBindingRepository repository = new AliasBindingRepository__Jdbc(JdbcClient.builder().dataSource(source).build());
        String input = "' OR 1=1";
        assertThat(repository.update(42L, input), is(1L));
        verify(statement).setString(1, input);
        verify(statement).setString(2, input);
        verify(statement).setLong(3, 42L);
        doThrow(new SQLException("The driver rejected the parameter.")).when(statement).setString(1, input);
        assertThrows(DataException.class, () -> repository.update(42L, input));
        verify(statement, times(2)).close();
        verify(connection, times(2)).close();
        verify(statement).execute();
    }
}
