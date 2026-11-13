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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
            doAnswer(call -> { values.add(call.getArgument(1)); return null; })
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
                .executeBatch(List.of(1), (b, v) -> { b.bind(1, v); b.bind(1, v); }));
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
        Iterable<Integer> items = () -> { throw failure; };
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
        doAnswer(call -> { autoCommit.set(call.getArgument(0)); return null; })
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
        doAnswer(call -> { autoCommit.set(call.getArgument(0)); return null; })
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
