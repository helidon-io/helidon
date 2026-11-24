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

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcBatchTest {
    private DataSource source;
    private Connection connection;
    private PreparedStatement prepared;
    private JdbcClient client;

    @BeforeEach
    void setup() throws Exception {
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        prepared = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        client = JdbcTestClients.create(source);
    }

    @Test
    void unchangedBinaryBindingsShareOnlyWithinOneItem() throws Exception {
        byte[] value = {0};
        List<byte[]> captured = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        doAnswer(invocation -> {
            captured.add(invocation.getArgument(1));
            return null;
        }).when(prepared).setBytes(anyInt(), any(byte[].class));
        when(source.getConnection()).thenAnswer(_ -> {
            assertThat(callbacks.get(), is(3));
            assertThat(value[0], is((byte) 9));
            return connection;
        });
        when(prepared.executeBatch()).thenReturn(new int[] {1, 1, 1});

        client.create("INSERT INTO T VALUES (?, ?, ?, ?)").executeBatch(List.of(1, 2, 2), (bindings, item) -> {
            value[0] = item.byteValue();
            bindings.bind(1, value).bind(2, item).bind(3, value).bind(4, value);
            callbacks.incrementAndGet();
            value[0] = 9;
        });

        assertThat(captured.size(), is(9));
        for (int row = 0; row < 3; row++) {
            byte[] snapshot = captured.get(row * 3);
            assertThat(snapshot, is(new byte[] {(byte) (row == 0 ? 1 : 2)}));
            assertThat(snapshot, not(sameInstance(value)));
            assertThat(captured.get(row * 3 + 1), sameInstance(snapshot));
            assertThat(captured.get(row * 3 + 2), sameInstance(snapshot));
            if (row > 0) {
                assertThat(snapshot, not(sameInstance(captured.get((row - 1) * 3))));
            }
        }
        verify(source).getConnection();
        verify(prepared).close();
        verify(connection).close();
    }

    /**
     * Array identity alone cannot justify sharing because an earlier bind must retain its original contents.
     */
    @Test
    void binarySnapshotReuseObservesMutationsAndDistinctSources() throws Exception {
        byte[] first = {1};
        byte[] second = {1};
        List<byte[]> captured = new ArrayList<>();
        doAnswer(invocation -> {
            captured.add(invocation.getArgument(1));
            return null;
        }).when(prepared).setBytes(anyInt(), any(byte[].class));
        when(prepared.executeBatch()).thenReturn(new int[] {1});

        client.create("INSERT INTO T VALUES (?, ?, ?, ?, ?)").executeBatch(List.of(1), (bindings, _) -> {
            bindings.bind(1, first).bind(2, second);
            first[0] = 9;
            bindings.bind(3, first).bind(4, second).bind(5, first);
        });

        assertThat(captured.get(0), is(new byte[] {1}));
        assertThat(captured.get(1), is(new byte[] {1}));
        assertThat(captured.get(0), not(sameInstance(captured.get(1))));
        assertThat(captured.get(2), is(new byte[] {9}));
        assertThat(captured.get(2), not(sameInstance(captured.get(0))));
        assertThat(captured.get(3), sameInstance(captured.get(1)));
        assertThat(captured.get(4), sameInstance(captured.get(2)));
    }

    @Test
    void restoredBinaryValuesAndEmptyArraysRetainTypedNullsAcrossSubmissions() throws Exception {
        for (boolean reusable : List.of(false, true)) {
            setup();
            byte[] value = {0};
            byte[] empty = new byte[0];
            List<byte[]> captured = new ArrayList<>();
            doAnswer(invocation -> {
                captured.add(invocation.getArgument(1));
                return null;
            }).when(prepared).setBytes(anyInt(), any(byte[].class));
            when(prepared.executeBatch()).thenReturn(new int[] {1});
            stage(reusable, "INSERT INTO T VALUES (?, ?, ?, ?, ?, ?)")
                    .executeBatch(List.of(1, 2), 1, (bindings, item) -> {
                        value[0] = item.byteValue();
                        bindings.bind(1, value);
                        value[0] = 9;
                        bindings.bind(2, value);
                        value[0] = item.byteValue();
                        bindings.bind(3, value).bindNull(4, JDBCType.VARBINARY)
                                .bind(5, empty).bind(6, empty);
                    });
            assertThat(captured.size(), is(10));
            for (int row = 0; row < 2; row++) {
                int offset = row * 5;
                assertThat(captured.get(offset), is(new byte[] {(byte) (row + 1)}));
                assertThat(captured.get(offset + 1), is(new byte[] {9}));
                assertThat(captured.get(offset + 2), is(new byte[] {(byte) (row + 1)}));
                assertThat(captured.get(offset + 3), is(new byte[0]));
                assertThat(captured.get(offset + 3), not(sameInstance(empty)));
                assertThat(captured.get(offset + 4), sameInstance(captured.get(offset + 3)));
            }
            assertThat(captured.get(0), not(sameInstance(captured.get(5))));
            assertThat(captured.get(3), not(sameInstance(captured.get(8))));
            verify(prepared, times(2)).setNull(4, Types.VARBINARY);
            verify(prepared, times(2)).executeBatch();
            verify(prepared).clearBatch();
            verify(prepared).close();
            verify(connection).close();
        }
    }

    @Test
    void failedBinaryCaptureExpiresItsViewBeforeAcquisition() throws Exception {
        for (boolean reusable : List.of(false, true)) {
            setup();
            JdbcClient.BatchBindings[] escaped = new JdbcClient.BatchBindings[1];
            IllegalStateException expected = new IllegalStateException("The application rejected binary capture.");
            JdbcClient.Statement statement = stage(reusable, "INSERT INTO T VALUES (?, ?)");
            assertThat(assertThrows(IllegalStateException.class, () -> statement.executeBatch(List.of(new byte[] {1}),
                    1, (bindings, value) -> {
                        escaped[0] = bindings;
                        bindings.bind(1, value).bind(2, value);
                        throw expected;
                    })), sameInstance(expected));
            assertThrows(IllegalStateException.class, () -> escaped[0].bind(1, new byte[] {2}));
            assertThrows(IllegalStateException.class, () -> statement.executeBatch(List.of(), 1, (_, _) -> { }));
            verifyZeroInteractions(source, connection, prepared);
        }
    }

    @Test
    void namedAndIndexedBinaryBindingsShareTheSameItemSnapshot() throws Exception {
        byte[] value = {1, 2};
        List<byte[]> captured = new ArrayList<>();
        doAnswer(invocation -> {
            captured.add(invocation.getArgument(1));
            return null;
        }).when(prepared).setBytes(anyInt(), any(byte[].class));
        when(prepared.executeBatch()).thenReturn(new int[] {1});

        client.prepare("INSERT INTO T VALUES (:payload, :scalar, :payload, :other)").get()
                .executeBatch(List.of(1), (bindings, item) -> {
                    bindings.bind("payload", value).bind(2, item).bind(4, value);
                    value[0] = 9;
                });

        assertThat(captured.size(), is(3));
        assertThat(captured.get(0), is(new byte[] {1, 2}));
        assertThat(captured.get(0), not(sameInstance(value)));
        assertThat(captured.get(1), sameInstance(captured.get(0)));
        assertThat(captured.get(2), sameInstance(captured.get(0)));
    }

    @Test
    void emptyInputConsumesTerminalWithoutAcquisition() {
        JdbcClient.Statement stage = client.create("INSERT INTO T VALUES (?)");
        JdbcClient.BatchResult result = stage.executeBatch(List.of(), (_, _) -> { });
        assertThat(result.entries().size(), is(0));
        assertThat(result.totalUpdateCount(), is(OptionalLong.of(0)));
        assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(1), (b, v) -> b.bind(1, v)));
        assertThrows(NullPointerException.class, () -> stage.executeBatch(null, null));
        verifyZeroInteractions(source);
    }

    @Test
    void reusableDefinitionsConsumeEmptyBatchWithoutAcquisition() {
        JdbcClient.BatchBinder<Integer> binder = (_, _) -> {
            throw new AssertionError("Empty batches must not invoke the binder.");
        };
        List<JdbcClient.Statement> stages = List.of(
                client.prepare("INSERT INTO T VALUES (?)").get(),
                client.prepare("INSERT INTO T VALUES (:value)").get(),
                client.prepare("INSERT INTO T VALUES (?)", List.of("value")).get(),
                client.prepare("INSERT INTO T VALUES (:value)", List.of("value")).get(),
                client.prepare("DELETE FROM T").get());
        for (JdbcClient.Statement stage : stages) {
            assertThrows(NullPointerException.class, () -> stage.executeBatch(null, null));
            assertThrows(NullPointerException.class, () -> stage.executeBatch(List.of(), null));
            assertThat(stage.executeBatch(List.of(), binder).totalUpdateCount(), is(OptionalLong.of(0)));
            assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(), binder));
            assertThrows(IllegalStateException.class, () -> stage.map(String.class));
        }
        verifyZeroInteractions(source, connection, prepared);
    }

    @Test
    void preparedBatchesUseNamedAndPhysicalPositionsWithFreshStages() throws Exception {
        when(prepared.executeBatch()).thenReturn(new int[] {1});
        List<Supplier<JdbcClient.Statement>> definitions = List.of(
                client.prepare("INSERT INTO T VALUES (?, ?)", List.of("name", "id")),
                client.prepare("INSERT INTO T VALUES (:name, :id)"));
        for (Supplier<JdbcClient.Statement> definition : definitions) {
            definition.get().executeBatch(List.of(7), (b, v) -> b.bind("name", "value").bind(2, v));
            definition.get().executeBatch(List.of(7), (b, v) -> b.bindNull("name", JDBCType.VARCHAR).bind("id", v));
        }
        verify(prepared, times(2)).setString(1, "value");
        verify(prepared, times(2)).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(prepared, times(4)).setInt(2, 7);
        verify(prepared, times(4)).executeBatch();
        verify(connection, times(4)).close();
    }

    @Test
    void namedCaptureValidatesAllDestinationsBeforeMutation() throws Exception {
        when(prepared.executeBatch()).thenReturn(new int[] {1});
        client.prepare("INSERT INTO T VALUES (:value, :value, :other)").get()
                .executeBatch(List.of(1), (b, v) -> {
                    b.bind(2, v);
                    assertThrows(IllegalArgumentException.class, () -> b.bind("value", 9));
                    assertThrows(IllegalArgumentException.class, () -> b.bindNull("value", JDBCType.INTEGER));
                    b.bind(1, 2);
                    assertThrows(IllegalArgumentException.class, () -> b.bind("missing", v));
                    assertThrows(IllegalArgumentException.class, () -> b.bind("other", new Object()));
                    assertThrows(IllegalArgumentException.class, () -> b.bindNull("other", JDBCType.STRUCT));
                    b.bindNull("other", JDBCType.INTEGER);
                    assertThrows(IllegalArgumentException.class, () -> b.bind(3, v));
                    verifyZeroInteractions(source);
                });
        verify(prepared).setInt(1, 2);
        verify(prepared).setInt(2, 1);
        verify(prepared).setNull(3, JDBCType.INTEGER.getVendorTypeNumber());
    }

    @Test
    void namedCaptureSnapshotsRepeatedMutableValuesAndTypedNulls() throws Exception {
        byte[] bytes = {1, 2};
        var timestamp = Timestamp.valueOf("2026-11-13 12:00:00.123456789");
        var expected = Timestamp.valueOf("2026-11-13 12:00:00.123456789");
        when(prepared.executeBatch()).thenReturn(new int[] {1});
        client.prepare("INSERT INTO T VALUES (:bytes, :time, :bytes, :time, :empty, :empty)").get()
                .executeBatch(List.of(1), (b, _) -> {
                    b.bind("bytes", bytes).bind("time", timestamp).bindNull("empty", JDBCType.VARCHAR);
                    bytes[0] = 9;
                    timestamp.setNanos(0);
                    verifyZeroInteractions(source);
                });
        verify(prepared).setBytes(1, new byte[] {1, 2});
        verify(prepared).setBytes(3, new byte[] {1, 2});
        verify(prepared).setTimestamp(2, expected);
        verify(prepared).setTimestamp(4, expected);
        verify(prepared).setNull(5, JDBCType.VARCHAR.getVendorTypeNumber());
        verify(prepared).setNull(6, JDBCType.VARCHAR.getVendorTypeNumber());
    }

    @Test
    void namedBindingsRequireDefinitionAndRespectLifetimeAndNullPrecedence() {
        AtomicReference<JdbcClient.BatchBindings> retained = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("Application capture failed.");
        JdbcClient.Statement stage = client.prepare("INSERT INTO T VALUES (:value)").get();
        assertThat(assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(1), (b, _) -> {
            retained.set(b);
            assertThrows(NullPointerException.class, () -> b.bind((String) null, null));
            assertThrows(NullPointerException.class, () -> b.bindNull((String) null, null));
            b.bind("value", 1);
            throw failure;
        })), sameInstance(failure));
        assertThrows(IllegalStateException.class, () -> retained.get().bind("value", 2));
        assertThrows(IllegalStateException.class, () -> retained.get().bindNull("value", JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> retained.get().bind("value", null));
        assertThrows(NullPointerException.class, () -> retained.get().bindNull("value", null));
        assertThrows(IllegalStateException.class, stage::execute);
        assertThrows(IllegalStateException.class, () -> client.create("INSERT INTO T VALUES (?)")
                .executeBatch(List.of(1), (b, v) -> b.bind("value", v)));
        assertThrows(IllegalStateException.class, () -> client.create("INSERT INTO T VALUES (?)")
                .executeBatch(List.of(1), (b, _) -> b.bindNull("value", JDBCType.INTEGER)));
        assertThrows(IllegalArgumentException.class, () -> client.prepare("INSERT INTO T VALUES (?)").get()
                .executeBatch(List.of(1), (b, v) -> b.bind("value", v)));
        verifyZeroInteractions(source);
    }

    /**
     * Concurrent callbacks overlap before acquisition while each stage retains its own captured values.
     */
    @Test
    void preparedDefinitionSharesOnlyImmutableMetadataAcrossConcurrentCaptures() throws Exception {
        List<String> names = new ArrayList<>(List.of("value"));
        Supplier<JdbcClient.Statement> definition = client.prepare("INSERT INTO T VALUES (?)", names);
        names.clear();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ConcurrentLinkedQueue<String> values = new ConcurrentLinkedQueue<>();
        List<Connection> connections = new ArrayList<>();
        List<PreparedStatement> statements = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Connection borrowed = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(borrowed.getAutoCommit()).thenReturn(true);
            when(borrowed.prepareStatement("INSERT INTO T VALUES (?)")).thenReturn(statement);
            when(statement.executeBatch()).thenReturn(new int[] {1});
            doAnswer(call -> {
                values.add(call.getArgument(1));
                return null;
            })
                    .when(statement).setString(eq(1), anyString());
            connections.add(borrowed);
            statements.add(statement);
        }
        when(source.getConnection()).thenReturn(connections.getFirst(), connections.getLast());
        List<Thread> workers = new ArrayList<>();
        try {
            for (String value : List.of("first", "second")) {
                workers.add(Thread.ofVirtual().start(() -> {
                    try {
                        assertThat(definition.get().executeBatch(List.of(value), (bindings, item) -> {
                            bindings.bind("value", item);
                            entered.countDown();
                            try {
                                assertThat("The test must release both callbacks.", release.await(5, TimeUnit.SECONDS), is(true));
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(interrupted);
                            }
                        }).totalUpdateCount(), is(OptionalLong.of(1)));
                    } catch (Throwable observed) {
                        failure.compareAndSet(null, observed);
                    }
                }));
            }
            assertThat("Both callbacks must enter capture.", entered.await(5, TimeUnit.SECONDS), is(true));
            verifyZeroInteractions(source);
            release.countDown();
            for (Thread worker : workers) {
                assertThat("Each batch must finish.", worker.join(Duration.ofSeconds(5)), is(true));
            }
            if (failure.get() != null) {
                throw new AssertionError("Concurrent batch execution failed.", failure.get());
            }
            assertThat(values.stream().sorted().toList(), is(List.of("first", "second")));
            for (PreparedStatement statement : statements) {
                verify(statement).addBatch();
                verify(statement).executeBatch();
                verify(statement).close();
            }
            for (Connection borrowed : connections) {
                verify(borrowed).close();
            }
        } finally {
            release.countDown();
            for (Thread worker : workers) {
                worker.interrupt();
                assertThat("Each worker must terminate.", worker.join(Duration.ofSeconds(5)), is(true));
            }
        }
    }

    @Test
    void preparedMarkerFreeBatchRetainsImperativeSemantics() throws Exception {
        when(prepared.executeBatch()).thenReturn(new int[] {1, 1});
        JdbcClient.BatchResult result = client.prepare("DELETE FROM T").get().executeBatch(List.of(1, 2), (_, _) -> { });
        assertThat(result.totalUpdateCount(), is(OptionalLong.of(2)));
        verify(prepared, times(2)).addBatch();
        verify(prepared).executeBatch();
        verify(prepared).close();
    }

    @Test
    void captureFailurePreservesIdentityAndExpiresView() {
        AtomicReference<JdbcClient.BatchBindings> retained = new AtomicReference<>();
        IllegalStateException application = new IllegalStateException("application");
        JdbcClient.Statement stage = client.create("INSERT INTO T VALUES (?)");
        assertThat(assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(1), (b, _) -> {
            retained.set(b);
            throw application;
        })), sameInstance(application));
        assertThrows(IllegalStateException.class, () -> retained.get().bind(1, 1));
        assertThrows(NullPointerException.class, () -> retained.get().bind(0, null));
        assertThrows(IllegalStateException.class, stage::execute);
        verifyZeroInteractions(source);
    }

    @Test
    void rejectsIncompleteDuplicateAndPreboundBindings() {
        assertThrows(IllegalStateException.class, () -> client.create("INSERT INTO T VALUES (?)")
                .executeBatch(List.of(1), (_, _) -> { }));
        assertThrows(IllegalArgumentException.class, () -> client.create("INSERT INTO T VALUES (?)")
                .executeBatch(List.of(1), (b, v) -> {
                    b.bind(1, v);
                    b.bind(1, v);
                }));
        assertThrows(IllegalArgumentException.class, () -> client.create("INSERT INTO T VALUES (?)").bind(1, 2)
                .executeBatch(List.of(1), (b, v) -> b.bind(1, v)));
        assertThrows(NullPointerException.class, () -> client.create("INSERT INTO T VALUES (?)")
                .executeBatch(Arrays.asList((Integer) null), (b, v) -> b.bind(1, v)));
        verifyZeroInteractions(source);
    }

    @Test
    void missingCoverageConsumesTerminalAndExpiresBindingsBeforeAcquisition() {
        JdbcClient.Statement stage = client.create("INSERT INTO T VALUES (?, ?)");
        AtomicReference<JdbcClient.BatchBindings> retained = new AtomicReference<>();
        assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(1), (bindings, value) -> {
            retained.set(bindings);
            bindings.bind(1, value);
        }));
        assertThrows(IllegalStateException.class, () -> retained.get().bind(2, 2));
        assertThrows(IllegalStateException.class, stage::execute);
        verifyZeroInteractions(source, connection, prepared);
    }

    @Test
    void preboundRejectionPrecedesTraversalAndPreservesTerminal() {
        JdbcClient.Statement stage = client.create("INSERT INTO T VALUES (?, ?)").bind(1, 1);
        Iterable<Integer> items = () -> {
            throw new AssertionError("Prebound rejection must precede input traversal.");
        };
        JdbcClient.BatchBinder<Integer> binder = (_, _) -> {
            throw new AssertionError("Prebound rejection must precede the binding callback.");
        };
        assertThat(assertThrows(NullPointerException.class, () -> stage.executeBatch(null, binder)).getMessage(),
                   is("The batch source must not be null."));
        assertThat(assertThrows(NullPointerException.class, () -> stage.executeBatch(items, null)).getMessage(),
                   is("The batch binder must not be null."));
        assertThrows(IllegalArgumentException.class, () -> stage.executeBatch(items, binder));
        assertThrows(IllegalArgumentException.class, () -> stage.executeBatch(List.of(), binder));
        verifyZeroInteractions(source, connection, prepared);
        stage.bind(2, 2);
    }

    @Test
    void iteratorFailureConsumesTerminalBeforeAcquisition() {
        IllegalStateException failure = new IllegalStateException("source");
        Iterable<Integer> items = () -> {
            throw failure;
        };
        JdbcClient.Statement stage = client.create("INSERT INTO T VALUES (?)");
        assertThat(assertThrows(IllegalStateException.class,
                () -> stage.executeBatch(items, (b, v) -> b.bind(1, v))), sameInstance(failure));
        assertThrows(IllegalStateException.class, stage::execute);
        verifyZeroInteractions(source);
    }

    /**
     * Mutable input is copied during capture before acquisition and cannot change stored batch bindings.
     */
    @Test
    void capturesBinaryValuesBeforeAcquisitionAndUsesOneExecution() throws Exception {
        byte[] bytes = {1, 2};
        when(source.getConnection()).thenAnswer(_ -> {
            assertThat(bytes[0], is((byte) 9));
            return connection;
        });
        doAnswer(call -> {
            assertThat((byte[]) call.getArgument(1), is(new byte[] {1, 2}));
            return null;
        }).when(prepared).setBytes(eq(1), any(byte[].class));
        when(prepared.executeBatch()).thenReturn(new int[] {0, Statement.SUCCESS_NO_INFO});
        JdbcClient.BatchResult result = client.create("INSERT INTO T VALUES (?)").executeBatch(List.of(1, 2), (b, _) -> {
            bytes[0] = 1;
            b.bind(1, bytes);
            bytes[0] = 9;
            verifyZeroInteractions(source);
        });
        assertThat(result.entries().getFirst().updateCount(), is(OptionalLong.of(0)));
        assertThat(result.entries().get(1).status(), is(JdbcClient.BatchResult.Status.SUCCESS_NO_INFO));
        assertThat(result.totalUpdateCount(), is(OptionalLong.empty()));
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
        verify(prepared, times(2)).addBatch();
        verify(prepared).executeBatch();
        verify(prepared, never()).executeLargeBatch();
        verify(prepared).close();
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void bindsTypedNullAndRejectsUnsupportedTypeBeforeAcquisition() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> client.create("INSERT INTO T VALUES (?)")
                .executeBatch(List.of(1), (b, _) -> b.bindNull(1, JDBCType.STRUCT)));
        verifyZeroInteractions(source);
        when(prepared.executeBatch()).thenReturn(new int[] {1});
        client.create("INSERT INTO T VALUES (?)").executeBatch(List.of(1), (b, _) -> b.bindNull(1, JDBCType.VARCHAR));
        verify(prepared).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());
    }

    /**
     * Partial execution evidence survives cleanup while all surfaced infrastructure diagnostics remain sanitized.
     */
    @Test
    void retainsPartialEvidenceAndSanitizesDriverAndCleanupFailures() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            when(prepared.executeBatch()).thenThrow(new BatchUpdateException("SECRET", "23000", 42,
                    new int[] {1, Statement.EXECUTE_FAILED}));
            doThrow(new SQLException("CLEANUP_SECRET")).when(prepared).close();
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)")
                            .executeBatch(List.of(1, 2, 3), (b, v) -> b.bind(1, v)));
            assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                    is(List.of(JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.EXECUTE_FAILED,
                               JdbcClient.BatchResult.Status.NOT_REPORTED)));
            assertSanitized(failure);
            assertThat(failure.getCause().toString(), containsString("23000"));
            verify(prepared).executeBatch();
            verify(connection).close();
        }
    }

    @Test
    void malformedNormalResultsRetainOnlyAttributableEvidence() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            for (int[] counts : new int[][] {null, {}, {1}, {1, 2, 3}, {1, -9}, {1, Statement.EXECUTE_FAILED}}) {
                when(prepared.executeBatch()).thenReturn(counts);
                JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                        () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v)));
                assertThat(failure.result().entries().size(), is(2));
                if (counts != null && counts.length > 0) {
                    assertThat(failure.result().entries().getFirst().updateCount(), is(OptionalLong.of(1)));
                }
            }
            verify(prepared, times(6)).executeBatch();
        }
    }

    @Test
    void successfulEvidenceSurvivesCleanupFailure() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            when(prepared.executeBatch()).thenReturn(new int[] {1, 2});
            doThrow(new SQLException("CLEANUP_SECRET")).when(prepared).close();
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v)));
            assertThat(failure.result().totalUpdateCount(), is(OptionalLong.of(3)));
            assertSanitized(failure);
            verify(connection).close();
        }
    }

    @Test
    void setupFailureReportsEveryCapturedItemWithoutRetry() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            when(source.getConnection()).thenThrow(new SQLException("SECRET"));
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v)));
            assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                    is(List.of(JdbcClient.BatchResult.Status.NOT_REPORTED, JdbcClient.BatchResult.Status.NOT_REPORTED)));
            verify(source).getConnection();
        }
    }
    /**
     * The transaction owner controls commit and connection release after a caught batch failure.
     */
    @Test
    void caughtDirectFailureLeavesTransactionCompletionWithItsOwner() throws Exception {
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        when(connection.getAutoCommit()).thenAnswer(_ -> autoCommit.get());
        doAnswer(call -> {
            autoCommit.set(call.getArgument(0));
            return null;
        })
                .when(connection).setAutoCommit(anyBoolean());
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        JdbcTxSupport tx = new JdbcTxSupport(manager, List.of(manager));
        JdbcRunner runner = new JdbcRunner(source, manager::acquire);
        when(prepared.executeBatch()).thenThrow(new BatchUpdateException("SECRET", new int[] {1}));
        tx.transaction(Tx.Type.REQUIRED, () -> {
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> new JdbcStatement(runner, "INSERT INTO T VALUES (?)", 1)
                            .executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v)));
            assertThat(failure.result().entries().size(), is(2));
            verify(connection, never()).close();
            verify(connection, never()).commit();
            return null;
        });
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection).close();
    }

    /**
     * A failure escaping a joined transaction marks the outer transaction for rollback.
     * Transaction wrapping retains the batch outcomes.
     */
    @Test
    void escapingJoinedFailureRollsBackAndRetainsBatchEvidence() throws Exception {
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        when(connection.getAutoCommit()).thenAnswer(_ -> autoCommit.get());
        doAnswer(call -> {
            autoCommit.set(call.getArgument(0));
            return null;
        })
                .when(connection).setAutoCommit(anyBoolean());
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        JdbcTxSupport tx = new JdbcTxSupport(manager, List.of(manager));
        JdbcRunner runner = new JdbcRunner(source, manager::acquire);
        when(prepared.executeBatch()).thenThrow(new BatchUpdateException("SECRET", new int[] {1}));
        assertThrows(TxException.class, () -> tx.transaction(Tx.Type.REQUIRED, () -> {
            TxException failure = assertThrows(TxException.class, () -> tx.transaction(Tx.Type.REQUIRED,
                    () -> new JdbcStatement(runner, "INSERT INTO T VALUES (?)", 1)
                            .executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v))));
            Throwable cause = failure;
            while (!(cause instanceof JdbcBatchException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(((JdbcBatchException) cause).result().entries().size(), is(2));
            return null;
        }));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
    }

    @Test
    void capturesApplicationPartitionWithoutHiddenChunking() throws Exception {
        int size = 2048;
        int[] counts = new int[size];
        Arrays.fill(counts, 1);
        when(prepared.executeBatch()).thenReturn(counts);
        JdbcClient.BatchResult result = client.create("INSERT INTO T VALUES (?)")
                .executeBatch(IntStream.range(0, size).boxed().toList(), (b, v) -> b.bind(1, v));
        assertThat(result.entries().size(), is(size));
        assertThat(result.totalUpdateCount(), is(OptionalLong.of(size)));
        verify(prepared, times(size)).addBatch();
        verify(prepared).executeBatch();
    }

    /**
     * The callback view rejects another thread without changing the binding state on the invocation thread.
     */
    @Test
    void bindingsRejectAnotherThreadBeforeMutation() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            when(prepared.executeBatch()).thenReturn(new int[] {1});
            AtomicReference<Throwable> observed = new AtomicReference<>();
            JdbcClient.Statement stage = reusable ? client.prepare("INSERT INTO T VALUES (:value)").get()
                    : client.create("INSERT INTO T VALUES (?)");
            stage.executeBatch(List.of(1), (b, v) -> {
                Thread worker = Thread.ofVirtual().start(() -> {
                    try {
                        assertThrows(IllegalStateException.class, () -> b.bind(1, v));
                        assertThrows(IllegalStateException.class, () -> b.bind("value", v));
                        assertThrows(IllegalStateException.class, () -> b.bindNull("value", JDBCType.INTEGER));
                        assertThrows(NullPointerException.class, () -> b.bind("value", null));
                        assertThrows(NullPointerException.class, () -> b.bindNull("value", null));
                    } catch (Throwable failure) {
                        observed.set(failure);
                    }
                });
                try {
                    assertThat(worker.join(Duration.ofSeconds(5)), is(true));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                } finally {
                    worker.interrupt();
                }
                if (observed.get() != null) {
                    throw new AssertionError("The binding checks on another thread failed.", observed.get());
                }
                assertThrows(NullPointerException.class, () -> b.bindNull(0, null));
                b.bind(1, v);
            });
            verify(prepared).setInt(1, 1);
        }
    }

    @Test
    void setterAndAddFailuresExposeNoExecutionEvidence() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            doThrow(new SQLException("SETTER_SECRET")).when(prepared).setInt(1, 1);
            JdbcBatchException setter = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(List.of(1), (b, v) -> b.bind(1, v)));
            assertThat(setter.result().entries().getFirst().status(), is(JdbcClient.BatchResult.Status.NOT_REPORTED));
            verify(prepared, never()).executeBatch();
            assertSanitized(setter);
            setup();
            doThrow(new SQLException("ADD_SECRET")).when(prepared).addBatch();
            JdbcBatchException add = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(List.of(1), (b, v) -> b.bind(1, v)));
            assertThat(add.result().entries().getFirst().status(), is(JdbcClient.BatchResult.Status.NOT_REPORTED));
            verify(prepared, never()).executeBatch();
            assertSanitized(add);
        }
    }

    /**
     * A fatal cleanup error remains primary while later cleanup still releases the connection lease.
     */
    @Test
    void fatalCleanupTakesPrecedenceAndStillReleasesTheLease() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            AssertionError fatal = new AssertionError("Fatal cleanup failure.");
            when(prepared.executeBatch()).thenThrow(new BatchUpdateException("SECRET", new int[] {1}));
            doThrow(fatal).when(prepared).close();
            assertThat(assertThrows(AssertionError.class, () -> stage(reusable, "INSERT INTO T VALUES (?)")
                    .executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v))), sameInstance(fatal));
            assertThat(fatal.getSuppressed().length, is(1));
            assertSanitized(fatal.getSuppressed()[0]);
            verify(connection).close();
        }
    }

    @Test
    void widensIntegerCountsBeforeComputingTheTotal() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            when(prepared.executeBatch()).thenReturn(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE});
            JdbcClient.BatchResult result = stage(reusable, "INSERT INTO T VALUES (?)")
                    .executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v));
            assertThat(result.totalUpdateCount(), is(OptionalLong.of(2L * Integer.MAX_VALUE)));
        }
    }

    @Test
    void ordinaryExecutionFailureReportsNoCountsAndDoesNotRetry() throws Exception {
        for (boolean reusable : new boolean[] {false, true}) {
            setup();
            when(prepared.executeBatch()).thenThrow(new SQLException("EXECUTE_SECRET"));
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(List.of(1, 2), (b, v) -> b.bind(1, v)));
            assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(),
                    is(List.of(JdbcClient.BatchResult.Status.NOT_REPORTED, JdbcClient.BatchResult.Status.NOT_REPORTED)));
            assertSanitized(failure);
            verify(prepared).executeBatch();
            verify(connection).close();
        }
    }

    @Test
    void sizedBoundariesReuseOneStatementAndClearOnlyBetweenSubmissions() throws Exception {
        for (boolean reusable : List.of(false, true)) {
            for (int batchSize : new int[] {1, 3, Integer.MAX_VALUE}) {
                for (int count : new int[] {0, 1, 2, 3, 4, 6, 7}) {
                    setup();
                    AtomicInteger queued = new AtomicInteger();
                    doAnswer(_ -> {
                        queued.incrementAndGet();
                        return null;
                    }).when(prepared).addBatch();
                    when(prepared.executeBatch()).thenAnswer(_ -> {
                        int[] counts = new int[queued.getAndSet(0)];
                        Arrays.fill(counts, 1);
                        return counts;
                    });
                    JdbcClient.Statement stage = stage(reusable, "INSERT INTO T VALUES (?)");
                    AtomicInteger captured = new AtomicInteger();
                    JdbcClient.BatchResult result = stage.executeBatch(IntStream.range(0, count).boxed().toList(), batchSize,
                            (bindings, value) -> {
                                assertThat(captured.getAndIncrement(), is(value));
                                bindings.bind(1, value);
                                verifyZeroInteractions(source);
                            });
                    assertThat(result.entries().size(), is(count));
                    assertThat(result.totalUpdateCount(), is(OptionalLong.of(count)));
                    assertThat(result.entries().stream().map(JdbcClient.BatchResult.Entry::itemIndex).toList(),
                               is(IntStream.range(0, count).boxed().toList()));
                    int submissions = count == 0 ? 0 : 1 + (count - 1) / batchSize;
                    verify(source, times(count == 0 ? 0 : 1)).getConnection();
                    verify(connection, times(count == 0 ? 0 : 1)).prepareStatement(anyString());
                    verify(prepared, times(count)).addBatch();
                    verify(prepared, times(submissions)).executeBatch();
                    verify(prepared, times(Math.max(0, submissions - 1))).clearBatch();
                    verify(prepared, never()).clearParameters();
                    verify(prepared, never()).executeLargeBatch();
                    assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(), 1, (_, _) -> { }));
                }
            }
        }
    }

    @Test
    void sizedValidationPreservesNullPrecedenceAndAnUnconsumedStage() throws Exception {
        Iterable<Integer> forbidden = () -> {
            throw new AssertionError("Validation must precede traversal.");
        };
        JdbcClient.BatchBinder<Integer> binder = (b, v) -> b.bind(1, v);
        for (boolean reusable : List.of(false, true)) {
            JdbcClient.Statement stage = stage(reusable, "INSERT INTO T VALUES (?)");
            for (int size : new int[] {0, -1, Integer.MIN_VALUE}) {
                assertThrows(NullPointerException.class, () -> stage.executeBatch(null, size, null));
                assertThrows(NullPointerException.class, () -> stage.executeBatch(null, size, binder));
                assertThrows(NullPointerException.class, () -> stage.executeBatch(forbidden, size, null));
                assertThat(assertThrows(IllegalArgumentException.class,
                        () -> stage.executeBatch(forbidden, size, binder)).getMessage(),
                        is("The batch size must be greater than zero."));
            }
            stage.bind(1, 1);
            assertThrows(IllegalArgumentException.class, () -> stage.executeBatch(forbidden, 1, binder));
            when(prepared.getLargeUpdateCount()).thenReturn(1L, -1L);
            assertThat(stage.execute(), is(1L));
            assertThrows(NullPointerException.class, () -> stage.executeBatch(null, 0, binder));
            assertThrows(IllegalArgumentException.class, () -> stage.executeBatch(forbidden, 0, binder));
            assertThrows(IllegalStateException.class, () -> stage.executeBatch(forbidden, 1, binder));
        }
    }

    @Test
    void sizedCaptureFailuresBeyondBoundariesPerformNoJdbcWork() {
        for (boolean reusable : List.of(false, true)) {
            IllegalStateException application = new IllegalStateException("The application rejected a later item.");
            AtomicReference<JdbcClient.BatchBindings> retained = new AtomicReference<>();
            JdbcClient.Statement stage = stage(reusable, "INSERT INTO T VALUES (?)");
            assertThat(assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(1, 2, 3, 4, 5), 2,
                    (bindings, value) -> {
                        retained.set(bindings);
                        bindings.bind(1, value);
                        if (value == 5) {
                            throw application;
                        }
                    })), sameInstance(application));
            assertThrows(IllegalStateException.class, () -> retained.get().bind(1, 1));
            assertThrows(IllegalStateException.class, () -> stage.executeBatch(List.of(), 2, (_, _) -> { }));
            assertThrows(IllegalStateException.class, () -> stage(reusable, "INSERT INTO T VALUES (?)")
                    .executeBatch(List.of(1, 2, 3, 4, 5), 2, (b, v) -> {
                        if (v < 5) {
                            b.bind(1, v);
                        }
                    }));
        }
        verifyZeroInteractions(source);
    }

    @Test
    void sizedFailureKeepsGlobalPrefixAndDoesNotSubmitTheSuffix() throws Exception {
        for (boolean reusable : List.of(false, true)) {
            setup();
            when(prepared.executeBatch()).thenReturn(new int[] {1, 1, 1})
                    .thenThrow(new BatchUpdateException("SECRET", "23000", 42, new int[] {1, Statement.EXECUTE_FAILED}));
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> stage(reusable, "INSERT INTO T VALUES (?)").executeBatch(IntStream.range(0, 8).boxed().toList(),
                            3, (b, v) -> b.bind(1, v)));
            assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(), is(List.of(
                    JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.EXACT_COUNT,
                    JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.EXACT_COUNT,
                    JdbcClient.BatchResult.Status.EXECUTE_FAILED, JdbcClient.BatchResult.Status.NOT_REPORTED,
                    JdbcClient.BatchResult.Status.NOT_REPORTED, JdbcClient.BatchResult.Status.NOT_REPORTED)));
            assertThat(failure.result().totalUpdateCount(), is(OptionalLong.empty()));
            assertSanitized(failure);
            assertThat(failure.getCause().toString(), containsString("23000"));
            assertThat(failure.getCause().toString(), containsString("42"));
            verify(prepared, times(6)).addBatch();
            verify(prepared, times(2)).executeBatch();
            verify(prepared).clearBatch();
            verify(prepared).close();
            verify(connection).close();
        }
    }

    @Test
    void sizedMalformedEvidenceCannotDescribeTheNextSubmission() throws Exception {
        for (boolean exceptional : List.of(false, true)) {
            for (int[] counts : new int[][] {null, {}, {2}, {2, 3, 4}, {-9, 1}, {Statement.EXECUTE_FAILED, 1}}) {
                setup();
                if (exceptional) {
                    when(prepared.executeBatch()).thenReturn(new int[] {1, 1})
                            .thenThrow(new BatchUpdateException("SECRET", "23000", 42, counts));
                } else {
                    when(prepared.executeBatch()).thenReturn(new int[] {1, 1}).thenReturn(counts);
                }
                JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                        () -> client.create("INSERT INTO T VALUES (?)")
                                .executeBatch(List.of(1, 2, 3, 4, 5), 2, (b, v) -> b.bind(1, v)));
                assertThat(failure.result().entries().get(0).updateCount(), is(OptionalLong.of(1)));
                assertThat(failure.result().entries().get(1).updateCount(), is(OptionalLong.of(1)));
                assertThat(failure.result().entries().get(4).status(), is(JdbcClient.BatchResult.Status.NOT_REPORTED));
                assertThat(failure.result().entries().get(4).itemIndex(), is(4));
                assertSanitized(failure);
                verify(prepared, times(2)).executeBatch();
                verify(prepared).clearBatch();
            }
        }
    }

    @Test
    void sizedClearAndLaterSetupFailuresRetainOnlyExecutedEvidence() throws Exception {
        for (String operation : List.of("clear", "bind", "add", "execute")) {
            setup();
            when(prepared.executeBatch()).thenReturn(new int[] {1, 1});
            if (operation.equals("clear")) {
                doThrow(new SQLException("SECRET")).when(prepared).clearBatch();
            } else if (operation.equals("bind")) {
                doThrow(new SQLException("SECRET")).when(prepared).setInt(1, 3);
            } else if (operation.equals("add")) {
                AtomicInteger additions = new AtomicInteger();
                doAnswer(_ -> {
                    if (additions.incrementAndGet() == 3) {
                        throw new SQLException("SECRET");
                    }
                    return null;
                }).when(prepared).addBatch();
            } else {
                when(prepared.executeBatch()).thenReturn(new int[] {1, 1}).thenThrow(new SQLException("SECRET"));
            }
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> client.create("INSERT INTO T VALUES (?)")
                            .executeBatch(List.of(1, 2, 3, 4, 5), 2, (b, v) -> b.bind(1, v)));
            assertThat(failure.result().entries().stream().map(JdbcClient.BatchResult.Entry::status).toList(), is(List.of(
                    JdbcClient.BatchResult.Status.EXACT_COUNT, JdbcClient.BatchResult.Status.EXACT_COUNT,
                    JdbcClient.BatchResult.Status.NOT_REPORTED, JdbcClient.BatchResult.Status.NOT_REPORTED,
                    JdbcClient.BatchResult.Status.NOT_REPORTED)));
            assertSanitized(failure);
            verify(prepared).close();
            verify(connection).close();
        }
    }

    @Test
    void sizedSuccessCountsAndCleanupFailureRetainAllGlobalEvidence() throws Exception {
        for (boolean cleanupFailure : List.of(false, true)) {
            setup();
            when(prepared.executeBatch()).thenReturn(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE})
                    .thenReturn(new int[] {0});
            if (cleanupFailure) {
                doThrow(new SQLException("SECRET")).when(prepared).close();
            }
            Supplier<JdbcClient.BatchResult> execution = () -> client.create("INSERT INTO T VALUES (?)")
                    .executeBatch(List.of(1, 2, 3), 2, (b, v) -> b.bind(1, v));
            JdbcClient.BatchResult result = cleanupFailure
                    ? assertThrows(JdbcBatchException.class, execution::get).result() : execution.get();
            assertThat(result.totalUpdateCount(), is(OptionalLong.of(2L * Integer.MAX_VALUE)));
            assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
            var order = inOrder(prepared);
            order.verify(prepared).executeBatch();
            order.verify(prepared).clearBatch();
            order.verify(prepared).executeBatch();
            order.verify(prepared).close();
        }
        setup();
        when(prepared.executeBatch()).thenReturn(new int[] {0, Statement.SUCCESS_NO_INFO}).thenReturn(new int[] {1});
        JdbcClient.BatchResult result = client.create("INSERT INTO T VALUES (?)")
                .executeBatch(List.of(1, 2, 3), 2, (b, v) -> b.bind(1, v));
        assertThat(result.entries().get(0).updateCount(), is(OptionalLong.of(0)));
        assertThat(result.entries().get(1).status(), is(JdbcClient.BatchResult.Status.SUCCESS_NO_INFO));
        assertThat(result.totalUpdateCount(), is(OptionalLong.empty()));
    }

    @Test
    void sizedCaptureSnapshotsRepeatedMutableValuesAndExpiresEveryCallback() throws Exception {
        byte[] bytes = {1};
        Timestamp timestamp = Timestamp.valueOf("2026-11-16 12:00:00.123456789");
        List<Timestamp> expected = new ArrayList<>();
        AtomicReference<JdbcClient.BatchBindings> retained = new AtomicReference<>();
        when(prepared.executeBatch()).thenReturn(new int[] {1, 1}, new int[] {1});
        client.prepare("INSERT INTO T VALUES (:bytes, :bytes, :time, :empty)").get()
                .executeBatch(List.of(1, 2, 3), 2, (bindings, _) -> {
                    if (retained.get() != null) {
                        assertThrows(IllegalStateException.class, () -> retained.get().bind("bytes", bytes));
                    }
                    retained.set(bindings);
                    Timestamp snapshot = new Timestamp(timestamp.getTime());
                    snapshot.setNanos(timestamp.getNanos());
                    expected.add(snapshot);
                    bindings.bind("bytes", bytes).bind("time", timestamp).bindNull("empty", JDBCType.VARCHAR);
                    bytes[0]++;
                    timestamp.setNanos(timestamp.getNanos() + 1);
                    verifyZeroInteractions(source);
                });
        for (byte value : new byte[] {1, 2, 3}) {
            verify(prepared).setBytes(1, new byte[] {value});
            verify(prepared).setBytes(2, new byte[] {value});
        }
        for (Timestamp value : expected) {
            verify(prepared).setTimestamp(3, value);
        }
        verify(prepared, times(3)).setNull(4, JDBCType.VARCHAR.getVendorTypeNumber());
        assertThrows(IllegalStateException.class, () -> retained.get().bindNull("empty", JDBCType.VARCHAR));
    }

    @Test
    void sizedFatalCleanupPreservesPrecedenceAndAttemptsEveryRelease() throws Exception {
        for (String location : List.of("clear", "statement", "lease")) {
            setup();
            OutOfMemoryError fatal = new OutOfMemoryError("The controlled cleanup failure is fatal.");
            when(prepared.executeBatch()).thenReturn(new int[] {1, 1})
                    .thenThrow(new BatchUpdateException("SECRET", new int[] {1}));
            if (location.equals("clear")) {
                doThrow(fatal).when(prepared).clearBatch();
            } else if (location.equals("statement")) {
                doThrow(fatal).when(prepared).close();
            } else {
                doThrow(fatal).when(connection).close();
            }
            assertThat(assertThrows(OutOfMemoryError.class,
                    () -> client.create("INSERT INTO T VALUES (?)")
                            .executeBatch(List.of(1, 2, 3, 4, 5), 2, (b, v) -> b.bind(1, v))), sameInstance(fatal));
            verify(prepared, times(location.equals("clear") ? 1 : 2)).executeBatch();
            verify(prepared).close();
            verify(connection, times(location.equals("lease") ? 2 : 1)).close();
            if (location.equals("lease")) {
                verify(connection).abort(any());
            }
        }
    }

    @Test
    void fatalCleanupOverridesBatchRuntimeFailure() throws Exception {
        for (boolean reusable : List.of(false, true)) {
            for (boolean sized : List.of(false, true)) {
                for (String location : List.of("statement", "lease")) {
                    setup();
                    OutOfMemoryError fatal = new OutOfMemoryError("The controlled cleanup failure is fatal.");
                    when(prepared.executeBatch()).thenThrow(new IllegalStateException("The driver failed with SECRET details."));
                    if (location.equals("statement")) {
                        doThrow(fatal).when(prepared).close();
                    } else {
                        doThrow(fatal).doNothing().when(connection).close();
                    }
                    JdbcClient.Statement statement = stage(reusable, "INSERT INTO T VALUES (?)");

                    assertThat(assertThrows(OutOfMemoryError.class, () -> {
                        if (sized) {
                            statement.executeBatch(List.of(1, 2), 1, (bindings, value) -> bindings.bind(1, value));
                        } else {
                            statement.executeBatch(List.of(1, 2), (bindings, value) -> bindings.bind(1, value));
                        }
                    }), sameInstance(fatal));
                    assertThat(fatal.getSuppressed().length, is(1));
                    assertSanitized(fatal);
                    verify(prepared).executeBatch();
                    verify(prepared).close();
                    verify(connection, times(location.equals("lease") ? 2 : 1)).close();
                    if (location.equals("lease")) {
                        verify(connection).abort(any());
                    }
                }
            }
        }
    }

    @Test
    void sharedPreparedDefinitionKeepsConcurrentSubmissionSizesIndependent() throws Exception {
        Supplier<JdbcClient.Statement> definition = client.prepare("INSERT INTO T VALUES (:value)");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<PreparedStatement> statements = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        List<AtomicInteger> submissions = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            Connection borrowed = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(borrowed.getAutoCommit()).thenReturn(true);
            when(borrowed.prepareStatement(anyString())).thenReturn(statement);
            AtomicInteger queued = new AtomicInteger();
            AtomicInteger executions = new AtomicInteger();
            submissions.add(executions);
            doAnswer(_ -> {
                queued.incrementAndGet();
                return null;
            }).when(statement).addBatch();
            when(statement.executeBatch()).thenAnswer(_ -> {
                executions.incrementAndGet();
                int[] counts = new int[queued.getAndSet(0)];
                Arrays.fill(counts, 1);
                return counts;
            });
            statements.add(statement);
            connections.add(borrowed);
        }
        when(source.getConnection()).thenReturn(connections.getFirst(), connections.getLast());
        List<Thread> workers = new ArrayList<>();
        try {
            for (int size : new int[] {1, 2}) {
                workers.add(Thread.ofVirtual().start(() -> {
                    try {
                        JdbcClient.BatchResult result = definition.get().executeBatch(List.of(1, 2, 3), size,
                                (bindings, value) -> {
                                    if (value == 1) {
                                        entered.countDown();
                                        try {
                                            assertThat("Both captures must be released.",
                                                       release.await(10, TimeUnit.SECONDS), is(true));
                                        } catch (InterruptedException interrupted) {
                                            Thread.currentThread().interrupt();
                                            throw new AssertionError(interrupted);
                                        }
                                    }
                                    bindings.bind("value", value);
                                });
                        assertThat(result.totalUpdateCount(), is(OptionalLong.of(3)));
                    } catch (Throwable problem) {
                        failure.compareAndSet(null, problem);
                        entered.countDown();
                        release.countDown();
                    }
                }));
            }
            assertThat("Both workers must enter capture or report failure.", entered.await(10, TimeUnit.SECONDS), is(true));
            if (failure.get() != null) {
                throw new AssertionError(failure.get());
            }
            verifyZeroInteractions(source);
            release.countDown();
            for (Thread worker : workers) {
                worker.join(Duration.ofSeconds(10));
                assertThat("The worker must finish.", worker.isAlive(), is(false));
            }
            assertThat(failure.get(), is((Throwable) null));
            verify(statements.getFirst(), times(3)).addBatch();
            verify(statements.getLast(), times(3)).addBatch();
            assertThat(submissions.stream().map(AtomicInteger::get).sorted().toList(), is(List.of(2, 3)));
            for (int index = 0; index < statements.size(); index++) {
                verify(statements.get(index), times(submissions.get(index).get() - 1)).clearBatch();
                verify(statements.get(index)).close();
                verify(connections.get(index)).close();
            }
        } finally {
            release.countDown();
            for (Thread worker : workers) {
                worker.interrupt();
                worker.join(Duration.ofSeconds(10));
                assertThat("The worker must terminate during cleanup.", worker.isAlive(), is(false));
            }
        }
    }

    @Test
    void sizedAcquisitionPreparationAndLeaseFailuresKeepCaptureAndOutcomes() throws Exception {
        for (String operation : List.of("acquire", "prepare", "lease")) {
            setup();
            AtomicInteger captured = new AtomicInteger();
            when(source.getConnection()).thenAnswer(_ -> {
                assertThat(captured.get(), is(3));
                if (operation.equals("acquire")) {
                    throw new SQLException("SECRET");
                }
                return connection;
            });
            if (operation.equals("prepare")) {
                when(connection.prepareStatement(anyString())).thenThrow(new SQLException("SECRET"));
            }
            if (operation.equals("lease")) {
                when(prepared.executeBatch()).thenReturn(new int[] {1, 1}, new int[] {1});
                doThrow(new SQLException("SECRET")).when(connection).close();
            }
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> client.create("INSERT INTO T VALUES (?)").executeBatch(List.of(1, 2, 3), 2,
                            (bindings, value) -> {
                                captured.incrementAndGet();
                                bindings.bind(1, value);
                            }));
            assertThat(failure.result().entries().size(), is(3));
            for (JdbcClient.BatchResult.Entry entry : failure.result().entries()) {
                assertThat(entry.status(), is(operation.equals("lease")
                        ? JdbcClient.BatchResult.Status.EXACT_COUNT : JdbcClient.BatchResult.Status.NOT_REPORTED));
            }
            assertSanitized(failure);
            verify(source).getConnection();
            if (operation.equals("lease")) {
                assertThat(failure.result().totalUpdateCount(), is(OptionalLong.of(3)));
                verify(connection).abort(any());
                verify(connection, times(2)).close();
            } else {
                verify(prepared, never()).executeBatch();
            }
        }
    }

    private static void assertSanitized(Throwable failure) {
        assertThat(failure.toString(), not(containsString("SECRET")));
        if (failure.getCause() != null) {
            assertSanitized(failure.getCause());
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            assertSanitized(suppressed);
        }
    }

    private JdbcClient.Statement stage(boolean reusable, String sql) {
        return reusable ? client.prepare(sql).get() : client.create(sql);
    }

}
