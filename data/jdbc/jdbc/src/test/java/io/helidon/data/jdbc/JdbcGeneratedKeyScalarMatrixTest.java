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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    /**
     * Verifies that generated-key scalar mapping uses each supported JDBC
     * extraction path and returns the detached column-one value.
     */
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
        assertGeneratedKey(Date.valueOf("2026-07-27"), Date.class);
        assertGeneratedKey(Time.valueOf("10:11:12"), Time.class);
        assertGeneratedKey(Timestamp.valueOf("2026-07-27 10:11:12"), Timestamp.class);
    }

    /**
     * Verifies generated-key cardinality and distinguishes required SQL NULL
     * from absent, singular, and non-unique key rows.
     */
    @Test
    void enforcesGeneratedKeyCardinalityAndRequiredNullSemantics() throws Exception {
        prepareOperation();
        when(resultSet.next()).thenReturn(false);
        assertThrows(NoResultException.class, () -> generatedLongKeys().one());

        prepareOperation();
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getLong(1)).thenReturn(1L, 2L);
        assertThrows(NonUniqueResultException.class, () -> generatedLongKeys().one());

        prepareOperation();
        when(resultSet.wasNull()).thenReturn(true);
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
        when(resultSet.wasNull()).thenReturn(true);
        assertThat(optionalGeneratedLongKey(), is(Optional.empty()));

        prepareOperation();
        when(resultSet.getLong(1)).thenReturn(10L);
        assertThat(optionalGeneratedLongKey(), is(Optional.of(10L)));
    }

    /**
     * Verifies that default and named generated keys use their exact JDBC
     * preparation overloads while retaining scalar key extraction.
     */
    @Test
    void usesTheExactJdbcPreparationOverloadForDefaultAndNamedKeys() throws Exception {
        prepareOperation();
        when(resultSet.getLong(1)).thenReturn(10L);

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
        when(resultSet.getLong(1)).thenReturn(11L);
        when(statement.getMoreResults()).thenReturn(false);

        long key = JdbcTestClients.create(dataSource)
                .create(SQL)
                .generatedKeys()
                .addColumn("ID")
                .addColumn("VERSION")
                .map(row -> row.get(1, Long.class))
                .one();

        assertThat(key, is(11L));
        verify(connection).prepareStatement(eq(SQL), aryEq(new String[] {"ID", "VERSION"}));
    }

    private <T> void assertGeneratedKey(T expected, Class<T> type) throws Exception {
        prepareOperation();
        if (type == Boolean.class) {
            when(resultSet.getBoolean(1)).thenReturn((Boolean) expected);
        } else if (type == Byte.class) {
            when(resultSet.getByte(1)).thenReturn((Byte) expected);
        } else if (type == Short.class) {
            when(resultSet.getShort(1)).thenReturn((Short) expected);
        } else if (type == Integer.class) {
            when(resultSet.getInt(1)).thenReturn((Integer) expected);
        } else if (type == Long.class) {
            when(resultSet.getLong(1)).thenReturn((Long) expected);
        } else if (type == Float.class) {
            when(resultSet.getFloat(1)).thenReturn((Float) expected);
        } else if (type == Double.class) {
            when(resultSet.getDouble(1)).thenReturn((Double) expected);
        } else if (type == BigDecimal.class) {
            when(resultSet.getBigDecimal(1)).thenReturn((BigDecimal) expected);
        } else if (type == String.class) {
            when(resultSet.getString(1)).thenReturn((String) expected);
        } else if (type == byte[].class) {
            when(resultSet.getBytes(1)).thenReturn((byte[]) expected);
        } else if (type == Date.class) {
            when(resultSet.getDate(1)).thenReturn((Date) expected);
        } else if (type == Time.class) {
            when(resultSet.getTime(1)).thenReturn((Time) expected);
        } else if (type == Timestamp.class) {
            when(resultSet.getTimestamp(1)).thenReturn((Timestamp) expected);
        } else {
            when(resultSet.getObject(1, type)).thenReturn(expected);
        }

        T actual = JdbcTestClients.create(dataSource)
                .create(SQL)
                .generatedKeys()
                .map(row -> row.get(1, type))
                .one();

        assertThat(actual, is(expected));
        if (type == Boolean.class) {
            verify(resultSet).getBoolean(1);
            verify(resultSet).wasNull();
        } else if (type == Byte.class) {
            verify(resultSet).getByte(1);
            verify(resultSet).wasNull();
        } else if (type == Short.class) {
            verify(resultSet).getShort(1);
            verify(resultSet).wasNull();
        } else if (type == Integer.class) {
            verify(resultSet).getInt(1);
            verify(resultSet).wasNull();
        } else if (type == Long.class) {
            verify(resultSet).getLong(1);
            verify(resultSet).wasNull();
        } else if (type == Float.class) {
            verify(resultSet).getFloat(1);
            verify(resultSet).wasNull();
        } else if (type == Double.class) {
            verify(resultSet).getDouble(1);
            verify(resultSet).wasNull();
        } else if (type == BigDecimal.class) {
            verify(resultSet).getBigDecimal(1);
        } else if (type == String.class) {
            verify(resultSet).getString(1);
        } else if (type == byte[].class) {
            assertThat((byte[]) actual, is((byte[]) expected));
            verify(resultSet).getBytes(1);
        } else if (type == Date.class) {
            verify(resultSet).getDate(1);
        } else if (type == Time.class) {
            verify(resultSet).getTime(1);
        } else if (type == Timestamp.class) {
            verify(resultSet).getTimestamp(1);
        } else {
            verify(resultSet).getObject(1, type);
        }
    }

    private Optional<Long> optionalGeneratedLongKey() {
        return JdbcTestClients.create(dataSource)
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
        return JdbcTestClients.create(dataSource)
                .create(SQL)
                .generatedKeys()
                .map(row -> row.get(1, Long.class));
    }
}
