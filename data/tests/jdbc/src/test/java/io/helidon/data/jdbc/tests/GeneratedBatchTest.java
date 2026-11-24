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

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.BatchCollisionRepository.Configured;
import io.helidon.data.jdbc.tests.BatchCollisionRepository.Literal;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class GeneratedBatchTest {
    @Test
    void adapterTypeNamesPreserveOriginalBindingsAndInheritedAliases() throws SQLException {
        for (String variant : List.of("literal-original", "configured-original", "literal-alias", "configured-alias")) {
            boolean configured = variant.startsWith("configured");
            boolean original = variant.endsWith("original");
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeBatch()).thenReturn(new int[] {1, 1});
            JdbcClient client = JdbcClient.builder().dataSource(source).build();
            List<String> values = Arrays.asList("payload", null);
            JdbcClient.BatchResult result;
            if (configured) {
                Configured repository = new BatchCollisionRepository_Configured__Jdbc(client, Config.empty());
                result = original ? repository.originalAdapterNames(values, null)
                        : repository.adapterTypes(values, new int[] {7, 8}, null, "bridge", "prefix");
            } else {
                Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
                result = original ? repository.originalAdapterNames(values, null)
                        : repository.adapterTypes(values, new int[] {7, 8}, null, "bridge", "prefix");
            }

            assertThat("The " + variant + " batch must report both items.", result.totalUpdateCount().orElseThrow(), is(2L));
            verify(connection).prepareStatement(original
                    ? "INSERT INTO T VALUES (?, ?)" : "INSERT INTO T VALUES (?, ?, ?, ?, ?, ?)");
            InOrder order = inOrder(statement);
            order.verify(statement).setString(1, "payload");
            if (original) {
                order.verify(statement).setNull(2, Types.VARCHAR);
            } else {
                order.verify(statement).setInt(2, 7);
                order.verify(statement).setNull(3, Types.VARCHAR);
                order.verify(statement).setString(4, "bridge");
                order.verify(statement).setString(5, "prefix");
                order.verify(statement).setString(6, "payload");
            }
            order.verify(statement).addBatch();
            order.verify(statement).setNull(1, Types.VARCHAR);
            if (original) {
                order.verify(statement).setNull(2, Types.VARCHAR);
            } else {
                order.verify(statement).setInt(2, 8);
                order.verify(statement).setNull(3, Types.VARCHAR);
                order.verify(statement).setString(4, "bridge");
                order.verify(statement).setString(5, "prefix");
                order.verify(statement).setNull(6, Types.VARCHAR);
            }
            order.verify(statement).addBatch();
            order.verify(statement).executeBatch();
            order.verify(statement).close();
            verify(connection).close();
            verify(connection, never()).commit();
            verify(connection, never()).rollback();
        }
    }

    /**
     * Recreating a repository adopts replacement SQL while existing definitions keep their original SQL.
     */
    @Test
    void configuredDefinitionsResolveOnceAndKeepTheirSqlAcrossReplacement() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1});
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Config config = mock(Config.class);
        AtomicReference<String> sql = new AtomicReference<>("INSERT INTO T VALUES (:name, :email)");
        when(config.get(anyString())).thenAnswer(call -> Config.just(ConfigSources.create(Map.of("app.batch", sql.get())))
                .get((String) call.getArgument(0)));
        Configured original = new BatchCollisionRepository_Configured__Jdbc(client, config);
        original.scalar(List.of("first"), null);
        sql.set("INSERT INTO T VALUES (:email, :name, :name)");
        Configured replacement = new BatchCollisionRepository_Configured__Jdbc(client, config);
        replacement.scalar(List.of("new"), null);
        original.scalar(List.of("old"), null);
        verify(config, times(2)).get("app.batch");
        verify(connection, times(2)).prepareStatement("INSERT INTO T VALUES (?, ?)");
        verify(connection).prepareStatement("INSERT INTO T VALUES (?, ?, ?)");
        verify(statement).setString(1, "first");
        verify(statement).setString(1, "old");
        verify(statement).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(statement).setString(2, "new");
        verify(statement).setString(3, "new");
        verify(statement, times(3)).executeBatch();
        verify(connection, times(3)).close();
    }

    /**
     * Invalid definitions fail before even empty sources reach the batch terminal.
     */
    @Test
    void configuredActivationRejectsInvalidLayoutsAndSanitizesConfigurationFailures() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Iterable<String> forbidden = () -> {
            throw new AssertionError("Invalid definitions must not traverse sources.");
        };
        for (Map<String, String> values : List.of(
                Map.of("app.batch", "INSERT INTO T VALUES (:name, ?)"),
                Map.of("app.batch", "INSERT INTO T VALUES (?)"),
                Map.of("app.batch", "INSERT INTO T VALUES (?, ?, ?)"),
                Map.of("app.batch", "INSERT INTO T VALUES (:missing, :email)"),
                Map.of("app.batch", "INSERT INTO T VALUES (:name, :email, :extra)"),
                Map.of("app.duplicates", "UPDATE T SET NAME = :name WHERE ID = :id AND NAME = :name"),
                Map.of("app.alias", "UPDATE T SET NAME = ? WHERE ID = ?"),
                Map.of("app.batch", " "))) {
            Config config = Config.just(ConfigSources.create(values));
            assertThrows(IllegalArgumentException.class,
                    () -> new BatchCollisionRepository_Configured__Jdbc(client, config).scalar(forbidden, null));
            assertThrows(IllegalArgumentException.class,
                    () -> new BatchCollisionRepository_Configured__Jdbc(client, config).scalar(List.of(), null));
        }
        Config object = Config.just(ConfigSources.create(Map.of("app.batch.child", "SQL_CANARY")));
        assertThrows(DataException.class, () -> new BatchCollisionRepository_Configured__Jdbc(client, object));
        Config list = mock(Config.class);
        Config node = mock(Config.class);
        when(list.get(anyString())).thenReturn(node);
        when(node.exists()).thenReturn(true);
        when(node.type()).thenReturn(Config.Type.LIST);
        assertThrows(DataException.class, () -> new BatchCollisionRepository_Configured__Jdbc(client, list));
        Config broken = mock(Config.class);
        when(broken.get(anyString())).thenThrow(new ConfigException("SQL_CANARY"));
        DataException failure = assertThrows(DataException.class,
                                             () -> new BatchCollisionRepository_Configured__Jdbc(client, broken));
        assertConfidential(failure);
        assertThat(failure.getCause(), is((Throwable) null));
        assertThat(failure.getSuppressed().length, is(0));
        verifyZeroInteractions(source);
    }

    @Test
    void configuredCapturePreservesOrderedDuplicateSlotsAndTypedNulls() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        Configured repository = new BatchCollisionRepository_Configured__Jdbc(
                JdbcClient.builder().dataSource(source).build(), Config.empty());
        assertThat(repository.arrays(new String[0], null, new long[0]).totalUpdateCount().orElseThrow(), is(0L));
        assertThrows(IllegalArgumentException.class, () -> repository.arrays(new String[] {"prefix", "late"}, null, 1));
        NullPointerException missingRecord = assertThrows(NullPointerException.class,
                () -> repository.records(Arrays.asList((Configured.Change) null).iterator()));
        assertThat(missingRecord.getMessage(), is("The batch record element must not be null."));
        assertThrows(IllegalStateException.class,
                () -> repository.records(List.of(new Configured.Change("fail", 1L)).iterator()));
        verifyZeroInteractions(source);
        when(statement.executeBatch()).thenReturn(new int[] {1});
        repository.duplicates(List.of(new Configured.Replacement("replacement")),
                List.of(new Configured.Expected(7, "expected")).iterator());
        verify(statement).setString(1, "replacement");
        verify(statement).setLong(2, 7);
        verify(statement).setString(3, "expected");
        Configured.Change.ACCESSES.set(0);
        repository.records(List.of(new Configured.Change(null, null)).iterator());
        assertThat(Configured.Change.ACCESSES.get(), is(1));
        verify(statement).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(statement).setNull(2, JDBCType.BIGINT.getVendorTypeNumber());
        verify(statement, times(2)).executeBatch();
    }

    @Test
    void literalAndConfiguredCaptureShareBinarySnapshotsWithinEachItem() throws SQLException {
        for (String variant : List.of("literal", "literal-sized", "configured", "configured-sized")) {
            boolean configured = variant.startsWith("configured");
            boolean sized = variant.endsWith("sized");
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            byte[] value = new byte[65536];
            List<byte[]> captured = new ArrayList<>();
            when(source.getConnection()).thenAnswer(_ -> {
                assertThat(value[0], is((byte) 9));
                return connection;
            });
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeBatch()).thenReturn(sized ? new int[] {1} : new int[] {1, 1, 1});
            doAnswer(invocation -> {
                captured.add(invocation.getArgument(1));
                return null;
            }).when(statement).setBytes(anyInt(), any(byte[].class));
            Iterator<byte[]> values = new Iterator<>() {
                private int position;

                @Override
                public boolean hasNext() {
                    if (position == 3) {
                        value[0] = 9;
                        return false;
                    }
                    return true;
                }

                @Override
                public byte[] next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    if (++position == 3) {
                        return null;
                    }
                    value[0] = (byte) position;
                    return value;
                }
            };
            JdbcClient client = JdbcClient.builder().dataSource(source).build();
            JdbcClient.BatchResult result;
            if (configured) {
                Configured repository = new BatchCollisionRepository_Configured__Jdbc(client, Config.empty());
                result = sized ? repository.binarySized(values) : repository.binary(values);
            } else {
                Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
                result = sized ? repository.binarySized(values) : repository.binary(values);
            }

            assertThat(variant, result.entries().stream().map(JdbcClient.BatchResult.Entry::itemIndex).toList(),
                       is(List.of(0, 1, 2)));
            assertThat(captured.size(), is(4));
            assertThat(captured.get(0)[0], is((byte) 1));
            assertThat(captured.get(2)[0], is((byte) 2));
            assertThat(captured.get(0), not(sameInstance(value)));
            assertThat(captured.get(2), not(sameInstance(value)));
            assertThat(captured.get(1), sameInstance(captured.get(0)));
            assertThat(captured.get(3), sameInstance(captured.get(2)));
            assertThat(captured.get(2), not(sameInstance(captured.get(0))));
            verify(statement).setNull(1, Types.VARBINARY);
            verify(statement).setNull(2, Types.VARBINARY);
            verify(statement, times(sized ? 3 : 1)).executeBatch();
            verify(statement, times(sized ? 2 : 0)).clearBatch();
            verify(source).getConnection();
            verify(statement).close();
            verify(connection).close();
        }
    }

    /**
     * A later source element may mutate an earlier payload only after that payload has been captured.
     */
    @Test
    void configuredCaptureSnapshotsRepeatedBinaryValuesBeforeAcquisition() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        byte[] first = {1};
        when(source.getConnection()).thenAnswer(_ -> {
            assertThat(first[0], is((byte) 9));
            return connection;
        });
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1, 1});
        Configured repository = new BatchCollisionRepository_Configured__Jdbc(
                JdbcClient.builder().dataSource(source).build(), Config.empty());
        repository.binary(new Iterator<>() {
            private int position;

            @Override
            public boolean hasNext() {
                return position < 2;
            }

            @Override
            public byte[] next() {
                if (position++ == 0) {
                    return first;
                }
                first[0] = 9;
                return new byte[] {2};
            }
        });
        verify(statement).setBytes(1, new byte[] {1});
        verify(statement).setBytes(2, new byte[] {1});
        verify(statement).setBytes(1, new byte[] {2});
        verify(statement).setBytes(2, new byte[] {2});
        verify(statement).executeBatch();
    }

    @Test
    void configuredBatchesRetainPartialAndCleanupEvidenceWithoutDriverDetails() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        Configured repository = new BatchCollisionRepository_Configured__Jdbc(
                JdbcClient.builder().dataSource(source).build(), Config.empty());
        when(statement.executeBatch()).thenReturn(new int[] {0, Statement.SUCCESS_NO_INFO});
        JdbcClient.BatchResult success = repository.scalar(List.of("first", "second"), null);
        assertThat(success.entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                   is(List.of(JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.SUCCESS_NO_INFO)));
        assertThat(success.entries().getFirst().updateCount().orElseThrow(), is(0L));
        assertThat(success.totalUpdateCount(), is(OptionalLong.empty()));
        assertThrows(UnsupportedOperationException.class, () -> success.entries().clear());
        doThrow(new BatchUpdateException("DRIVER_CANARY", "23000", 17, new int[] {1})).when(statement).executeBatch();
        JdbcBatchException partial = assertThrows(JdbcBatchException.class,
                () -> repository.scalar(List.of("first", "second"), null));
        assertThat(partial.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                   is(List.of(JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.NOT_REPORTED)));
        assertConfidential(partial);
        for (int[] counts : new int[][] {null, {}, {1}, {1, 1, 1}, {1, -8}, {1, Statement.EXECUTE_FAILED}}) {
            doReturn(counts).when(statement).executeBatch();
            JdbcBatchException malformed = assertThrows(JdbcBatchException.class,
                    () -> repository.scalar(List.of("first", "second"), null));
            assertThat(malformed.result().entries().size(), is(2));
            assertConfidential(malformed);
        }
        doReturn(new int[] {1, 1}).when(statement).executeBatch();
        doThrow(new SQLException("CLEANUP_CANARY")).when(statement).close();
        JdbcBatchException cleanup = assertThrows(JdbcBatchException.class,
                () -> repository.scalar(List.of("first", "second"), null));
        assertThat(cleanup.result().totalUpdateCount().orElseThrow(), is(2L));
        assertConfidential(cleanup);
        verify(statement, times(9)).executeBatch();
        verify(connection, times(9)).close();
    }
    /**
     * Empty and misaligned inputs acquire no connection under any selection form.
     * Successful and malformed JDBC outcomes share the same cleanup and completion ownership.
     */
    @Test
    void inferredAndExplicitBatchesUseTheSameTerminalAndOutcomeRules() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("INSERT INTO T VALUES (?, ?)")).thenReturn(statement);
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(
                JdbcClient.builder().dataSource(source).build());
        for (BiFunction<int[], byte[], JdbcClient.BatchResult> execute
                : List.<BiFunction<int[], byte[], JdbcClient.BatchResult>>of(
                        repository::array, repository::inferredArray, repository::explicitArray)) {
            assertThat(execute.apply(new int[0], new byte[0]).totalUpdateCount().orElseThrow(), is(0L));
            assertThrows(IllegalArgumentException.class, () -> execute.apply(new int[] {1}, new byte[0]));
            when(statement.executeBatch()).thenReturn(new int[] {0, Statement.SUCCESS_NO_INFO});
            JdbcClient.BatchResult result = execute.apply(new int[] {1, 2}, new byte[] {3, 4});
            assertThat(result.entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                       is(List.of(JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.SUCCESS_NO_INFO)));
            assertThat(result.totalUpdateCount(), is(OptionalLong.empty()));
            when(statement.executeBatch()).thenReturn(new int[] {1});
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> execute.apply(new int[] {1, 2}, new byte[] {3, 4}));
            assertThat(failure.result().entries().getLast().status(), is(JdbcClient.BatchResult.Status.NOT_REPORTED));
        }
        verify(source, times(6)).getConnection();
        verify(statement, times(12)).addBatch();
        verify(statement, times(6)).executeBatch();
        verify(statement, times(6)).close();
        verify(connection, times(6)).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    /**
     * The generated callback validates both references before reading an item or assigning a binding.
     */
    @Test
    @SuppressWarnings("unchecked")
    void generatedBinderValidatesBothArgumentsBeforeReadingOrBinding() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        JdbcClient.BatchBindings bindings = mock(JdbcClient.BatchBindings.class);
        JdbcClient.BatchResult result = mock(JdbcClient.BatchResult.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.executeBatch(any(), any())).thenAnswer(call -> {
            JdbcClient.BatchBinder<Object[]> binder = call.getArgument(1);
            NullPointerException bothMissing = assertThrows(NullPointerException.class,
                    () -> binder.bind(null, null));
            assertThat(bothMissing.getMessage(), is("The batch bindings must not be null."));
            NullPointerException bindingsMissing = assertThrows(NullPointerException.class,
                    () -> binder.bind(null, new Object[0]));
            assertThat(bindingsMissing.getMessage(), is("The batch bindings must not be null."));
            NullPointerException itemMissing = assertThrows(NullPointerException.class,
                    () -> binder.bind(bindings, null));
            assertThat(itemMissing.getMessage(), is("The batch item must not be null."));
            verifyZeroInteractions(bindings);
            return result;
        });
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        assertThat(repository.array(new int[0], new byte[0]), sameInstance(result));
    }

    /**
     * Generated variable names preserve application bindings.
     * Repeated markers do not repeat record accessor calls.
     */
    @Test
    @SuppressWarnings("unchecked")
    void executesCapturedNamesRepeatedAndNullableValues() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        JdbcClient.BatchBindings bindings = mock(JdbcClient.BatchBindings.class);
        JdbcClient.BatchResult result = mock(JdbcClient.BatchResult.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.executeBatch(any(), any())).thenAnswer(call -> {
            Iterable<Object[]> source = call.getArgument(0);
            JdbcClient.BatchBinder<Object[]> binder = call.getArgument(1);
            int count = 0;
            for (Object[] row : source) {
                binder.bind(bindings, row);
                count++;
            }
            assertThat(count, is(2));
            return result;
        });
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        Literal.Item.ACCESSES.set(0);
        assertThat(repository.insert(Arrays.asList("value", null), "constant",
                List.of(new Literal.Item("first", 1),
                        new Literal.Item("second", null)).iterator()), is(result));
        verify(client).create("INSERT INTO T VALUES (?, ?, ?, ?, ?, ?)");
        verify(bindings).bind(1, "value");
        verify(bindings).bind(3, "value");
        verify(bindings).bindNull(1, JDBCType.VARCHAR);
        verify(bindings).bindNull(3, JDBCType.VARCHAR);
        verify(bindings).bind(4, "first");
        verify(bindings).bind(6, "first");
        assertThat(Literal.Item.ACCESSES.get(), is(2));
        verify(bindings).bindNull(5, JDBCType.INTEGER);
    }

    @Test
    void checksSourceNullBeforeStaging() {
        JdbcClient client = mock(JdbcClient.class);
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        assertThrows(NullPointerException.class, () -> repository.array(null, (byte[]) null));
        assertThrows(NullPointerException.class, () -> repository.insert(List.of(), "constant", null));
        verifyZeroInteractions(client);
    }
    @Test
    void alignmentAndEmptyCapturePerformNoJdbcWork() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        assertThat(repository.array(new int[0], new byte[0]).entries().size(), is(0));
        assertThrows(IllegalArgumentException.class, () -> repository.array(new int[] {1}, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> repository.array(new int[] {1, 2}, (byte) 3));
        verifyZeroInteractions(source);
    }

    /**
     * Primitive array elements use scalar setters and release resources without completing a transaction.
     */
    @Test
    void executesAlignedPrimitiveArraysThroughRealCapture() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1, 1});
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        assertThat(repository.array(new int[] {1, 2}, (byte) 3, (byte) 4).entries().size(), is(2));
        verify(statement).setInt(1, 1);
        verify(statement).setInt(1, 2);
        verify(statement).setByte(2, (byte) 3);
        verify(statement).setByte(2, (byte) 4);
        verify(statement, never()).setNull(anyInt(), anyInt());
        verify(statement).executeBatch();
        verify(statement).close();
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void recordAccessorAndNullRecordFailuresPrecedeAcquisition() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        assertThrows(IllegalStateException.class, () -> repository.insert(List.of("value"), "constant",
                List.of(new Literal.Item("fail", 1)).iterator()));
        assertThrows(NullPointerException.class, () -> repository.insert(List.of("value"), "constant",
                Arrays.asList((Literal.Item) null).iterator()));
        verifyZeroInteractions(source);
    }

    /**
     * Iterator creation and traversal failures precede JDBC acquisition, and application failures retain their identity.
     */
    @Test
    void iteratorFailuresPrecedeAcquisition() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(client);
        IllegalStateException failure = new IllegalStateException("The application iterator failed.");
        Iterator<Literal.Item> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Literal.Item next() {
                throw failure;
            }
        };
        assertThat(assertThrows(IllegalStateException.class,
                () -> repository.insert(List.of("value"), "constant", iterator)), sameInstance(failure));
        Iterable<String> missingIterator = () -> null;
        NullPointerException literal = assertThrows(NullPointerException.class,
                () -> repository.originalAdapterNames(missingIterator, null));
        assertThat(literal.getMessage(), is("The batch iterator must not be null."));
        Configured configured = new BatchCollisionRepository_Configured__Jdbc(client, Config.empty());
        NullPointerException prepared = assertThrows(NullPointerException.class,
                () -> configured.scalar(missingIterator, null));
        assertThat(prepared.getMessage(), is("The batch iterator must not be null."));
        assertThrows(NullPointerException.class, () -> configured.aliases(null, new long[0]));
        verifyZeroInteractions(source);
    }

    @Test
    void executesInheritedAliasesWithCompleteCaptureAndTypedNulls() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        AtomicInteger captured = new AtomicInteger();
        when(source.getConnection()).thenAnswer(_ -> {
            assertThat(captured.get(), is(2));
            return connection;
        });
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("UPDATE T SET NAME = ?, LABEL = ?, EMAIL = ? WHERE ID = ?"))
                .thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1, 0});
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(
                JdbcClient.builder().dataSource(source).build());
        String input = "alias ' OR '1'='1";
        Iterator<String> values = Arrays.asList(input, null).stream().peek(_ -> captured.incrementAndGet()).iterator();

        JdbcClient.BatchResult result = repository.aliases(new long[] {11, 12}, values, null);

        assertThat(result.entries().stream().map(JdbcClient.BatchResult.Entry::itemIndex).toList(), is(List.of(0, 1)));
        assertThat(result.totalUpdateCount().orElseThrow(), is(1L));
        InOrder order = inOrder(statement, connection);
        order.verify(statement).setString(1, input);
        order.verify(statement).setString(2, input);
        order.verify(statement).setNull(3, Types.VARCHAR);
        order.verify(statement).setLong(4, 11L);
        order.verify(statement).addBatch();
        order.verify(statement).setNull(1, Types.VARCHAR);
        order.verify(statement).setNull(2, Types.VARCHAR);
        order.verify(statement).setNull(3, Types.VARCHAR);
        order.verify(statement).setLong(4, 12L);
        order.verify(statement).addBatch();
        order.verify(statement).executeBatch();
        order.verify(statement).close();
        order.verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void aliasedSourcesPreserveEmptyNullAndUnequalLengthBoundaries() {
        DataSource source = mock(DataSource.class);
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(
                JdbcClient.builder().dataSource(source).build());
        assertThat(repository.aliases(new long[0], List.<String>of().iterator(), null).totalUpdateCount().orElseThrow(), is(0L));
        assertThrows(NullPointerException.class, () -> repository.aliases(null, List.<String>of().iterator(), null));
        assertThrows(NullPointerException.class, () -> repository.aliases(new long[0], null, null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.aliases(new long[] {1, 2}, List.of("first").iterator(), "constant"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.aliases(new long[0], List.of("first").iterator(), "constant"));
        IllegalStateException failure = new IllegalStateException("The aliased source failed.");
        Iterator<String> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public String next() {
                throw failure;
            }
        };
        assertThat(assertThrows(IllegalStateException.class,
                () -> repository.aliases(new long[] {1}, iterator, "constant")), sameInstance(failure));
        verifyZeroInteractions(source);
    }

    @Test
    void combinesAliasedConstantsWithUnchangedRecordComponents() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("UPDATE T SET NAME = ?, EMAIL = ? WHERE ID = ? OR ID = ?"))
                .thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1});
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(
                JdbcClient.builder().dataSource(source).build());

        assertThat(repository.aliasedRecords(null, List.of(new Literal.ItemChange("record", 42)))
                           .totalUpdateCount().orElseThrow(), is(1L));
        verify(statement).setString(1, "record");
        verify(statement).setNull(2, Types.VARCHAR);
        verify(statement).setLong(3, 42L);
        verify(statement).setLong(4, 42L);
        verify(statement).executeBatch();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void preservesAliasedPartialFailureEvidenceAndReleasesResources() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenThrow(new BatchUpdateException(
                "The driver supplied private details.", "23000", 17, new int[] {1, Statement.EXECUTE_FAILED}));
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(
                JdbcClient.builder().dataSource(source).build());

        JdbcBatchException failure = assertThrows(JdbcBatchException.class, () -> repository.aliases(
                new long[] {1, 2, 3}, List.of("first", "second", "third").iterator(), "constant"));

        assertThat(failure.getMessage(), is("The JDBC batch failed."));
        assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                   is(List.of(JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.EXECUTE_FAILED,
                              JdbcClient.BatchResult.Status.NOT_REPORTED)));
        assertThat(failure.result().totalUpdateCount(), is(OptionalLong.empty()));
        verify(statement, times(3)).addBatch();
        verify(statement).executeBatch();
        verify(statement).close();
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void retainsAliasedSuccessfulEvidenceWhenCleanupFails() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1});
        doThrow(new SQLException("The driver supplied private cleanup details.")).when(statement).close();
        Literal repository = new BatchCollisionRepository_Literal__Jdbc(
                JdbcClient.builder().dataSource(source).build());

        JdbcBatchException failure = assertThrows(JdbcBatchException.class, () -> repository.aliases(
                new long[] {1}, List.of("first").iterator(), null));

        assertThat(failure.result().totalUpdateCount().orElseThrow(), is(1L));
        assertThat(failure.result().entries().getFirst().status(), is(JdbcClient.BatchResult.Status.EXACT_COUNT));
        verify(statement).executeBatch();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void sizedGeneratedAdaptersCaptureOnceBeforeExecutingLiteralAndConfiguredSubmissions() throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        AtomicInteger captured = new AtomicInteger();
        when(source.getConnection()).thenAnswer(_ -> {
            assertThat(captured.get(), is(5));
            return connection;
        });
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1, 1}, new int[] {1, 1}, new int[] {1});
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Literal literal = new BatchCollisionRepository_Literal__Jdbc(client);
        JdbcClient.BatchResult result = literal.sized(
                () -> List.of("one", "two", "three", "four", "five").stream()
                        .peek(_ -> captured.incrementAndGet()).iterator(),
                null, new long[] {1, 2, 3, 4, 5}, 5, 4, 3, 2, 1);
        assertThat(result.totalUpdateCount().orElseThrow(), is(5L));
        verify(statement, times(3)).executeBatch();
        verify(statement, times(2)).clearBatch();
        verify(statement, times(5)).setNull(2, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(statement).setString(1, "five");
        verify(statement).setLong(3, 5);
        verify(statement).setInt(4, 1);
        Config config = mock(Config.class);
        when(config.get(anyString())).thenAnswer(call -> Config.just(ConfigSources.create(Map.of(
                "app.sized", "INSERT INTO T VALUES (:email, :name, :name)"))).get((String) call.getArgument(0)));
        Configured configured = new BatchCollisionRepository_Configured__Jdbc(client, config);
        when(statement.executeBatch()).thenReturn(new int[] {1, 1}, new int[] {1, 1}, new int[] {1});
        captured.set(0);
        Iterable<String> names = () -> List.of("one", "two", "three", "four", "five").stream()
                .peek(_ -> captured.incrementAndGet()).iterator();
        assertThat(configured.sized(names, null).totalUpdateCount().orElseThrow(), is(5L));
        verify(config).get("app.sized");
        verify(statement, times(6)).executeBatch();
        verify(statement, times(4)).clearBatch();
        verify(statement).setString(3, "five");
        verify(source, times(2)).getConnection();
        verify(connection, times(2)).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void sizedGeneratedSourcesAndAccessorsFailBeforeAnySubmission() throws SQLException {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Configured repository = new BatchCollisionRepository_Configured__Jdbc(client, Config.empty());
        Configured.Change.ACCESSES.set(0);
        List<Configured.Change> brokenRecords = List.of(
                new Configured.Change("one", 1L), new Configured.Change("two", 2L),
                new Configured.Change("three", 3L), new Configured.Change("four", 4L),
                new Configured.Change("fail", 5L));
        assertThat(assertThrows(IllegalStateException.class,
                                () -> repository.sizedRecords(brokenRecords.iterator())).getMessage(),
                   is("The configured record accessor failed."));
        assertThat(Configured.Change.ACCESSES.get(), is(5));
        IllegalStateException application = new IllegalStateException("The fifth source item failed.");
        Iterable<String> brokenSource = () -> new Iterator<>() {
            private int position;

            @Override
            public boolean hasNext() {
                return position < 5;
            }

            @Override
            public String next() {
                if (++position == 5) {
                    throw application;
                }
                return "value";
            }
        };
        assertThat(assertThrows(IllegalStateException.class, () -> repository.sized(brokenSource, null)),
                   sameInstance(application));
        verifyZeroInteractions(source);
        Configured.Change.ACCESSES.set(0);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenAnswer(_ -> {
            assertThat(Configured.Change.ACCESSES.get(), is(5));
            return connection;
        });
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1, 1}, new int[] {1, 1}, new int[] {1});
        assertThat(repository.sizedRecords(List.of(new Configured.Change(null, null),
                new Configured.Change("two", 2L), new Configured.Change("three", 3L),
                new Configured.Change("four", 4L), new Configured.Change("five", 5L)).iterator())
                           .totalUpdateCount().orElseThrow(), is(5L));
        assertThat(Configured.Change.ACCESSES.get(), is(5));
        verify(statement).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(statement).setNull(2, JDBCType.BIGINT.getVendorTypeNumber());
        verify(statement, times(3)).executeBatch();
        verify(statement, times(2)).clearBatch();
    }

    private static void assertConfidential(Throwable failure) {
        assertThat(failure.toString(), not(containsString("CANARY")));
        if (failure.getCause() != null) {
            assertConfidential(failure.getCause());
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            assertConfidential(suppressed);
        }
    }

}
