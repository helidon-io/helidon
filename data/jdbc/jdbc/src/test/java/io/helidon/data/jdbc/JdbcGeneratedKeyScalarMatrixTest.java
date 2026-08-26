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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcGeneratedKeyScalarMatrixTest {
    private static final String SQL = "INSERT INTO TEST_VALUE DEFAULT VALUES";

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private ResultSetMetaData metadata;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        metadata = mock(ResultSetMetaData.class);
    }

    @Test
    void mapsEverySupportedScalarFromGeneratedKeyColumnOne() throws Exception {
        assertGeneratedKey(Boolean.TRUE, Boolean.class);
        assertGeneratedKey((byte) 2, Byte.class);
        assertGeneratedKey((short) 3, Short.class);
        assertGeneratedKey(4, Integer.class);
        assertGeneratedKey(5L, Long.class);
        assertGeneratedKey(6.5F, Float.class);
        assertGeneratedKey(7.5D, Double.class);
        assertGeneratedKey(new BigDecimal("8.50"), BigDecimal.class);
        assertGeneratedKey("value", String.class);
        assertGeneratedKey(new byte[] {9, 10}, byte[].class);
        assertGeneratedKey(LocalDate.of(2026, 7, 27), LocalDate.class);
        assertGeneratedKey(LocalTime.of(10, 11, 12), LocalTime.class);
        assertGeneratedKey(LocalDateTime.of(2026, 7, 27, 10, 11, 12), LocalDateTime.class);
        assertGeneratedKey(OffsetTime.parse("10:11:12+05:30"), OffsetTime.class);
        assertGeneratedKey(OffsetDateTime.parse("2026-07-27T10:11:12+05:30"), OffsetDateTime.class);
        assertGeneratedKey(java.sql.Date.valueOf("2026-07-27"), java.sql.Date.class);
        assertGeneratedKey(Time.valueOf("10:11:12"), Time.class);
        assertGeneratedKey(Timestamp.valueOf("2026-07-27 10:11:12"), Timestamp.class);
    }

    @Test
    void enforcesGeneratedKeyCardinalityAndRequiredNullSemantics() throws Exception {
        prepareOperation();
        when(resultSet.next()).thenReturn(false);
        assertThrows(NoResultException.class, () -> generatedLongKeys().one());

        prepareOperation();
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getObject(1, Long.class)).thenReturn(1L, 2L);
        assertThrows(NonUniqueResultException.class, () -> generatedLongKeys().one());

        prepareOperation();
        assertThrows(DataException.class, () -> generatedLongKeys().one());

        prepareOperation();
        when(resultSet.next()).thenReturn(false);
        assertThat(generatedLongKeys().optional().isEmpty(), is(true));
    }

    /**
     * Proves the generated optional-scalar shape collapses an absent key row
     * and a SQL-null key while retaining a non-null generated key.
     */
    @Test
    void collapsesNoRowAndSqlNullForOptionalGeneratedScalarKeys() throws Exception {
        prepareOperation();
        when(resultSet.next()).thenReturn(false);
        assertThat(optionalGeneratedLongKey(), is(Optional.empty()));

        prepareOperation();
        assertThat(optionalGeneratedLongKey(), is(Optional.empty()));

        prepareOperation();
        when(resultSet.getObject(1, Long.class)).thenReturn(10L);
        assertThat(optionalGeneratedLongKey(), is(Optional.of(10L)));
    }

    @Test
    void usesTheExactJdbcPreparationOverloadForDefaultAndNamedKeys() throws Exception {
        prepareOperation();
        when(resultSet.getObject(1, Long.class)).thenReturn(10L);

        generatedLongKeys().one();

        verify(connection).prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS);

        reset(dataSource, connection, statement, resultSet, metadata);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(eq(SQL), aryEq(new String[] {"ID", "VERSION"}))).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1, Long.class)).thenReturn(11L);
        when(statement.getMoreResults()).thenReturn(false);

        long key = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create(SQL)
                .generatedKeys()
                .addColumn("ID")
                .addColumn("VERSION")
                .map(row -> row.required(1, Long.class))
                .one();

        assertThat(key, is(11L));
        verify(connection).prepareStatement(eq(SQL), aryEq(new String[] {"ID", "VERSION"}));
    }

    private <T> void assertGeneratedKey(T expected, Class<T> type) throws Exception {
        prepareOperation();
        if (type == byte[].class) {
            when(resultSet.getBytes(1)).thenReturn((byte[]) expected);
        } else {
            when(resultSet.getObject(1, type)).thenReturn(expected);
        }

        T actual = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create(SQL)
                .generatedKeys()
                .map(row -> row.required(1, type))
                .one();

        if (type == byte[].class) {
            assertThat((byte[]) actual, is((byte[]) expected));
            verify(resultSet).getBytes(1);
        } else {
            assertThat(actual, is(expected));
            verify(resultSet).getObject(1, type);
        }
    }

    private Optional<Long> optionalGeneratedLongKey() {
        return new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create(SQL)
                .generatedKeys()
                .map(row -> row.optional(1, Long.class))
                .optional()
                .flatMap(value -> value);
    }

    private void prepareOperation() throws Exception {
        reset(dataSource, connection, statement, resultSet, metadata);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(statement.getMoreResults()).thenReturn(false);
    }

    private JdbcClient.Rows<Long> generatedLongKeys() {
        return new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create(SQL)
                .generatedKeys()
                .map(row -> row.required(1, Long.class));
    }
}
