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

import java.sql.Connection;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("helidon:api:internal")
class JdbcBindSnapshotTest {
    private static final String UPDATE_SQL = "UPDATE TEST_VALUE SET VALUE = ?";

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private JdbcClient client;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(UPDATE_SQL)).thenReturn(preparedStatement);
        when(preparedStatement.execute()).thenReturn(false);
        when(preparedStatement.getLargeUpdateCount()).thenReturn(1L, -1L);
        client = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());
    }

    @Test
    void capturesByteArrayWhenBound() throws Exception {
        byte[] value = {1, 2, 3};
        JdbcClient.Statement statement = client.create(UPDATE_SQL).bind(1, value);

        value[0] = 9;
        statement.execute();

        ArgumentCaptor<byte[]> captured = ArgumentCaptor.forClass(byte[].class);
        verify(preparedStatement).setBytes(eq(1), captured.capture());
        assertThat(captured.getValue(), is(new byte[] {1, 2, 3}));
        assertThat(captured.getValue(), not(sameInstance(value)));
    }

    /**
     * Proves that binding snapshots a mutable JDBC date before application
     * mutation and supplies the detached value through {@code setDate}.
     */
    @Test
    void capturesDateWhenBound() throws Exception {
        Date value = Date.valueOf("2026-07-27");
        Date expected = Date.valueOf(value.toLocalDate());
        JdbcClient.Statement statement = client.create(UPDATE_SQL).bind(1, value);

        value.setTime(Date.valueOf("2030-01-01").getTime());
        statement.execute();

        ArgumentCaptor<Date> captured = ArgumentCaptor.forClass(Date.class);
        verify(preparedStatement).setDate(eq(1), captured.capture());
        assertThat(captured.getValue(), is(expected));
        assertThat(captured.getValue(), not(sameInstance(value)));
    }

    /**
     * Proves that binding snapshots a mutable JDBC time before application
     * mutation and supplies the detached value through {@code setTime}.
     */
    @Test
    void capturesTimeWhenBound() throws Exception {
        Time value = Time.valueOf("10:11:12");
        Time expected = new Time(value.getTime());
        JdbcClient.Statement statement = client.create(UPDATE_SQL).bind(1, value);

        value.setTime(Time.valueOf("20:21:22").getTime());
        statement.execute();

        ArgumentCaptor<Time> captured = ArgumentCaptor.forClass(Time.class);
        verify(preparedStatement).setTime(eq(1), captured.capture());
        assertThat(captured.getValue(), is(expected));
        assertThat(captured.getValue(), not(sameInstance(value)));
    }

    /**
     * Proves that binding snapshots a mutable JDBC timestamp before application
     * mutation and preserves its nanosecond precision through {@code setTimestamp}.
     */
    @Test
    void capturesTimestampAndNanosecondsWhenBound() throws Exception {
        Timestamp value = Timestamp.valueOf("2026-07-27 10:11:12.123456789");
        Timestamp expected = Timestamp.valueOf(value.toLocalDateTime());
        JdbcClient.Statement statement = client.create(UPDATE_SQL).bind(1, value);

        value.setTime(Timestamp.valueOf("2030-01-01 20:21:22").getTime());
        value.setNanos(987654321);
        statement.execute();

        ArgumentCaptor<Timestamp> captured = ArgumentCaptor.forClass(Timestamp.class);
        verify(preparedStatement).setTimestamp(eq(1), captured.capture());
        assertThat(captured.getValue(), is(expected));
        assertThat(captured.getValue(), not(sameInstance(value)));
        assertThat(captured.getValue().getNanos(), is(123456789));
    }

    /**
     * Proves that a known immutable scalar is retained by reference and reaches
     * the driver through its dedicated string setter.
     */
    @Test
    void retainsKnownImmutableScalarByReference() throws Exception {
        String value = "stable";

        client.create(UPDATE_SQL).bind(1, value).execute();

        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        verify(preparedStatement).setString(eq(1), captured.capture());
        assertThat(captured.getValue(), sameInstance(value));
    }

    @Test
    void terminalExecutionCannotObserveConcurrentSourceMutation() throws Exception {
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch continueAcquisition = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(_ -> {
            acquisitionStarted.countDown();
            if (!continueAcquisition.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to continue JDBC connection acquisition");
            }
            return connection;
        });
        byte[] value = {4, 5, 6};
        JdbcClient.Statement statement = client.create(UPDATE_SQL).bind(1, value);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Long> result = executor.submit(statement::execute);
            assertThat(acquisitionStarted.await(10, TimeUnit.SECONDS), is(true));
            value[0] = 9;
            continueAcquisition.countDown();

            assertThat(result.get(10, TimeUnit.SECONDS), is(1L));
        } finally {
            continueAcquisition.countDown();
        }

        ArgumentCaptor<byte[]> captured = ArgumentCaptor.forClass(byte[].class);
        verify(preparedStatement).setBytes(eq(1), captured.capture());
        assertThat(captured.getValue(), is(new byte[] {4, 5, 6}));
    }

    @Test
    @SuppressWarnings("helidon:api:internal")
    void typedNullUsesOnlyTheDeclaredJdbcType() throws Exception {
        JdbcClient.bindNull(client.create(UPDATE_SQL), 1, JDBCType.TIMESTAMP).execute();

        verify(preparedStatement).setNull(1, JDBCType.TIMESTAMP.getVendorTypeNumber());
        verify(preparedStatement, never()).setObject(anyInt(), any());
        verify(preparedStatement, never()).setBytes(anyInt(), any());
    }

}
