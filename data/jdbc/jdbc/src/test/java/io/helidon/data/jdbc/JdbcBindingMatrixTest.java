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
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("helidon:api:internal")
class JdbcBindingMatrixTest {
    private static final String SQL = "UPDATE TEST_VALUE SET VALUE = ?";

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
    }

    @Test
    void bindsEverySupportedReferenceScalarAndCanonicalTypedNull() throws Exception {
        assertBinding(Boolean.TRUE, JDBCType.BOOLEAN);
        assertBinding((byte) 2, JDBCType.TINYINT);
        assertBinding((short) 3, JDBCType.SMALLINT);
        assertBinding(4, JDBCType.INTEGER);
        assertBinding(5L, JDBCType.BIGINT);
        assertBinding(6.5F, JDBCType.REAL);
        assertBinding(7.5D, JDBCType.DOUBLE);
        assertBinding(new BigDecimal("8.50"), JDBCType.DECIMAL);
        assertBinding("value", JDBCType.VARCHAR);
        assertBinding(new byte[] {9, 10}, JDBCType.VARBINARY);
        assertBinding(LocalDate.of(2026, 7, 27), JDBCType.DATE);
        assertBinding(LocalTime.of(10, 11, 12), JDBCType.TIME);
        assertBinding(LocalDateTime.of(2026, 7, 27, 10, 11, 12), JDBCType.TIMESTAMP);
        assertBinding(OffsetTime.parse("10:11:12+05:30"), JDBCType.TIME_WITH_TIMEZONE);
        assertBinding(OffsetDateTime.parse("2026-07-27T10:11:12+05:30"), JDBCType.TIMESTAMP_WITH_TIMEZONE);
        assertBinding(java.sql.Date.valueOf("2026-07-27"), JDBCType.DATE);
        assertBinding(Time.valueOf("10:11:12"), JDBCType.TIME);
        assertBinding(Timestamp.valueOf("2026-07-27 10:11:12"), JDBCType.TIMESTAMP);
    }

    /**
     * Proves hostile application text does not alter the SQL prepared by the
     * driver and reaches JDBC only as a bound value.
     *
     * @throws Exception if the mocked JDBC boundary reports a checked failure
     */
    @Test
    void keepsHostileBindValueOutOfPreparedSql() throws Exception {
        String originalSql = SQL;
        String hostilePayload = "alpha'); DROP TABLE TEST_VALUE; -- :value ?";
        prepareOperation();

        new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create(originalSql)
                .bind(1, hostilePayload)
                .execute();

        verify(connection).prepareStatement(originalSql);
        verify(statement).setObject(1, hostilePayload);
    }

    @Test
    void validatesEveryBindPositionBeforeConnectionAcquisition() throws Exception {
        JdbcClient.Statement operation = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create("UPDATE TEST_VALUE SET VALUE = ? WHERE ID = ?")
                .bind(1, "value");

        assertThrows(IllegalStateException.class, operation::execute);

        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsNullTypesThatRequireDatabaseTypeNames() throws Exception {
        JdbcClient client = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());

        for (JDBCType type : List.of(JDBCType.ARRAY,
                                     JDBCType.DISTINCT,
                                     JDBCType.JAVA_OBJECT,
                                     JDBCType.REF,
                                     JDBCType.STRUCT)) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                            () -> JdbcClient.bindNull(client.create(SQL), 1, type));
            assertThat(failure.getMessage(),
                       is("The JDBC client cannot bind a null value of type '" + type
                                  + "' without a database type name."));
        }

        verify(dataSource, never()).getConnection();
    }

    private void assertBinding(Object value, JDBCType nullType) throws Exception {
        prepareOperation();
        new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider())
                .create(SQL)
                .bind(1, value)
                .execute();
        if (value instanceof byte[] bytes) {
            verify(statement).setBytes(1, bytes);
        } else {
            verify(statement).setObject(1, value);
        }
        verify(statement, never()).setNull(1, nullType.getVendorTypeNumber());

        prepareOperation();
        JdbcClient.bindNull(new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider()).create(SQL),
                            1,
                            nullType)
                .execute();
        verify(statement).setNull(1, nullType.getVendorTypeNumber());
        verify(statement, never()).setObject(1, null);
    }

    private void prepareOperation() throws Exception {
        reset(dataSource, connection, statement);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getMoreResults()).thenReturn(false);
    }
}
