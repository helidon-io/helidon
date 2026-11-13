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
package io.helidon.tests.integration.data.jdbc.contract;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.declarative.repository.BatchRepository.ScalarChange;
import io.helidon.tests.integration.data.jdbc.support.DatabaseFixture;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies execution and independent committed state through real database fixtures.
 */
public abstract class AbstractJdbcBatchContract {
    private ServiceRegistryManager manager;
    private JdbcClient client;
    private DatabaseFixture fixture;
    private TxSupport transactions;

    protected static <T> Iterable<T> oneShot(List<T> items) {
        AtomicBoolean traversed = new AtomicBoolean();
        return () -> {
            assertThat("The source must supply only one iterator.", traversed.compareAndSet(false, true), is(true));
            return items.iterator();
        };
    }

    protected abstract void beforeStartApplication();

    protected final <T> T service(Class<T> type) {
        return manager.registry().get(type);
    }

    protected final JdbcClient client() {
        return client;
    }

    protected final TxSupport transactions() {
        return transactions;
    }

    protected final void restartBatch(Config config) {
        manager.shutdown();
        TestConfigFactory.config(config);
        startBatch();
    }

    protected JdbcClient.BatchResult insert(List<String> rows, String email) {
        return client.create("INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)").executeBatch(
                IntStream.range(0, rows.size()).boxed().toList(), (b, index) -> {
                    String name = rows.get(index);
                    if (name == null) {
                        b.bindNull(1, JDBCType.VARCHAR);
                    } else {
                        b.bind(1, name);
                    }
                    if (email == null) {
                        b.bindNull(2, JDBCType.VARCHAR);
                    } else {
                        b.bind(2, email);
                    }
                });
    }

    protected JdbcClient.BatchResult insertSource(Iterable<String> rows) {
        return client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                .executeBatch(rows, (bindings, name) -> {
                    Objects.requireNonNull(bindings, "The batch bindings must not be null.");
                    Objects.requireNonNull(name, "The batch item must not be null.");
                    bindings.bind(1, name);
                });
    }

    protected JdbcClient.BatchResult insertOrderedSet(Set<String> rows) {
        return insertSource(rows);
    }

    protected JdbcClient.BatchResult storeScalars(Iterable<ScalarChange> rows) {
        return client.create("""
                UPDATE SCALAR_VALUE
                SET BYTES_VALUE = ?, DECIMAL_VALUE = ?, LOCAL_DATE_VALUE = ?, LOCAL_TIME_VALUE = ?,
                    LOCAL_DATE_TIME_VALUE = ?, DATE_VALUE = ?, TIME_VALUE = ?, TIMESTAMP_VALUE = ?
                WHERE ID = ?
                """).executeBatch(rows, (bindings, row) -> {
                    Objects.requireNonNull(bindings, "The batch bindings must not be null.");
                    Objects.requireNonNull(row, "The batch item must not be null.");
                    Object[] values = {row.bytes(), row.decimal(), row.date(), row.time(), row.dateTime(),
                                       row.sqlDate(), row.sqlTime(), row.sqlTimestamp()};
                    JDBCType[] types = {JDBCType.VARBINARY, JDBCType.DECIMAL, JDBCType.DATE, JDBCType.TIME,
                                        JDBCType.TIMESTAMP, JDBCType.DATE, JDBCType.TIME, JDBCType.TIMESTAMP};
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] == null) {
                            bindings.bindNull(i + 1, types[i]);
                        } else {
                            bindings.bind(i + 1, values[i]);
                        }
                    }
                    bindings.bind(9, row.id());
                });
    }

    @Test
    protected final void preservesOneShotAndOrderedSetEncounterOrder() {
        JdbcClient.BatchResult iterable = insertSource(oneShot(List.of("source-z", "source-a")));
        assertThat(iterable.entries().stream().map(JdbcClient.BatchResult.Entry::itemIndex).toList(), is(List.of(0, 1)));
        assertThat(client.create("SELECT NAME FROM CONTACT WHERE NAME LIKE 'source-%' ORDER BY ID")
                           .map(String.class).list(), is(List.of("source-z", "source-a")));
        JdbcClient.BatchResult ordered = insertOrderedSet(new LinkedHashSet<>(List.of("set-z", "set-a", "set-z")));
        assertThat(ordered.entries().size(), is(2));
        assertThat(client.create("SELECT NAME FROM CONTACT WHERE NAME LIKE 'set-%' ORDER BY ID")
                           .map(String.class).list(), is(List.of("set-z", "set-a")));
        assertThat(insertSource(oneShot(List.of())).totalUpdateCount().orElseThrow(), is(0L));
    }

    @Test
    protected final void sourceFailureLeavesNoCommittedPrefixAndAllowsLaterReuse() {
        IllegalStateException failure = new IllegalStateException("The application source failed.");
        Iterable<String> rows = () -> new Iterator<>() {
            private int position;

            @Override
            public boolean hasNext() {
                return position < 2;
            }

            @Override
            public String next() {
                if (position++ == 0) {
                    return "source-prefix";
                }
                throw failure;
            }
        };
        assertThat(assertThrows(IllegalStateException.class, () -> insertSource(rows)), sameInstance(failure));
        assertThat(fixture.committedByName("source-prefix"), is(Optional.empty()));
        assertThat(insertSource(oneShot(List.of("source-reused"))).entries().size(), is(1));
        assertThat(fixture.committedByName("source-reused").isPresent(), is(true));
    }

    @Test
    protected final void storesBinaryDecimalAndTemporalRecordsWithTypedNulls() {
        client.create("DELETE FROM SCALAR_VALUE WHERE ID = 2").execute();
        client.create("INSERT INTO SCALAR_VALUE (ID) VALUES (2)").execute();
        ScalarChange values = new ScalarChange(1, new byte[] {9, 10}, new BigDecimal("8.50"),
                LocalDate.of(2026, 7, 27), LocalTime.of(10, 11, 12), LocalDateTime.of(2026, 7, 27, 10, 11, 12),
                Date.valueOf("2026-07-27"), Time.valueOf("10:11:12"), Timestamp.valueOf("2026-07-27 10:11:12"));
        ScalarChange nulls = new ScalarChange(2, null, null, null, null, null, null, null, null);
        String sql = """
                SELECT BYTES_VALUE, DECIMAL_VALUE, LOCAL_DATE_VALUE, LOCAL_TIME_VALUE,
                       LOCAL_DATE_TIME_VALUE, DATE_VALUE, TIME_VALUE, TIMESTAMP_VALUE
                FROM SCALAR_VALUE WHERE ID = ?
                """;
        try {
            JdbcClient.BatchResult result = transactions.transaction(Tx.Type.REQUIRED,
                    () -> storeScalars(oneShot(List.of(values, nulls))));
            assertThat(result.entries().size(), is(2));
            client.create(sql).bind(1, 1).map(row -> {
                Objects.requireNonNull(row, "The row must not be null.");
                assertThat(row.get(1, byte[].class), is(values.bytes()));
                assertThat(row.get(2, BigDecimal.class), comparesEqualTo(values.decimal()));
                assertThat(row.get(3, LocalDate.class), is(values.date()));
                assertThat(row.get(4, LocalTime.class), is(values.time()));
                assertThat(row.get(5, LocalDateTime.class), is(values.dateTime()));
                assertThat(row.get(6, Date.class), is(values.sqlDate()));
                assertThat(row.get(7, Time.class), is(values.sqlTime()));
                assertThat(row.get(8, Timestamp.class), is(values.sqlTimestamp()));
                return true;
            }).one();
            client.create(sql).bind(1, 2).map(row -> {
                Objects.requireNonNull(row, "The row must not be null.");
                Class<?>[] types = {byte[].class, BigDecimal.class, LocalDate.class, LocalTime.class,
                                    LocalDateTime.class, Date.class, Time.class, Timestamp.class};
                for (int i = 0; i < types.length; i++) {
                    assertThat(row.optional(i + 1, types[i]).isEmpty(), is(true));
                }
                return true;
            }).one();
        } finally {
            client.create("DELETE FROM SCALAR_VALUE WHERE ID = 2").execute();
        }
    }

    @Test
    protected final void rejectsUnsupportedBindingsAndDefinitionsWithoutChangingStoredData() {
        for (Object unsupported : List.of(new Object(), List.of(1), Optional.of(1), new int[] {1}, 'x',
                                           OffsetDateTime.parse("2026-07-27T10:11:12Z"))) {
            assertThrows(IllegalArgumentException.class, () -> client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                    .executeBatch(List.of("unsupported"), (bindings, _) -> {
                        Objects.requireNonNull(bindings, "The batch bindings must not be null.");
                        bindings.bind(1, unsupported);
                    }));
        }
        for (JDBCType unsupported : List.of(JDBCType.NULL, JDBCType.REF_CURSOR, JDBCType.ARRAY, JDBCType.DISTINCT,
                                            JDBCType.JAVA_OBJECT, JDBCType.REF, JDBCType.STRUCT)) {
            assertThrows(IllegalArgumentException.class, () -> client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                    .executeBatch(List.of("unsupported"), (bindings, _) -> {
                        Objects.requireNonNull(bindings, "The batch bindings must not be null.");
                        bindings.bindNull(1, unsupported);
                    }));
        }
        assertThrows(NullPointerException.class, () -> insertSource(null));
        assertThrows(NullPointerException.class, () -> client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                .executeBatch(Arrays.asList((String) null), (_, _) -> {
                    throw new AssertionError("A null item must be rejected before the callback.");
                }));
        Iterable<String> forbidden = () -> {
            throw new AssertionError("Rejected definitions must not traverse batch inputs.");
        };
        for (JdbcClient.Statement stage : List.of(client.prepare("INSERT INTO CONTACT (NAME) VALUES (?)").get(),
                client.prepare("INSERT INTO CONTACT (NAME) VALUES (:name)", List.of("name")).get())) {
            stage.bind(1, "prebound");
            assertThrows(NullPointerException.class, () -> stage.executeBatch(forbidden, null));
            assertThrows(IllegalArgumentException.class,
                    () -> stage.executeBatch(forbidden, (_, _) -> {
                        throw new AssertionError("Rejected definitions must not invoke the binder.");
                    }));
            assertThrows(IllegalArgumentException.class, () -> stage.executeBatch(List.of(), (_, _) -> { }));
        }
        assertThat(client.create("SELECT NAME FROM CONTACT ORDER BY ID").map(String.class).list(),
                   is(List.of("alpha", "beta")));
        insertSource(oneShot(List.of("after-rejection")));
        assertThat(fixture.committedByName("after-rejection").isPresent(), is(true));
    }

    /**
     * YAML replacements retain named identities while changing physical marker order and repetition.
     */
    @Test
    protected final void executesPreparedBatchesFromApplicationYamlWithFreshStages() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            Supplier<JdbcClient.Statement> definition = preparedDefinition(variant);
            assertThat(insertPrepared(definition, List.of(), null).totalUpdateCount().orElseThrow(), is(0L));
            JdbcClient.BatchResult result = insertPrepared(definition, List.of("prepared-one", "prepared-two"), null);
            assertThat(result.entries().size(), is(2));
            assertThat(fixture.committedByName("prepared-one").orElseThrow().email(), is(Optional.empty()));
            assertThat(fixture.committedByName("prepared-two").orElseThrow().email(), is(Optional.empty()));
            assertThat(insertPrepared(definition, List.of("prepared-reused"), "email@example.test").entries().size(), is(1));
            assertThat(fixture.committedByName("prepared-reused").orElseThrow().email(),
                       is(Optional.of("email@example.test")));
            fixture.reset();
        }
    }

    /**
     * Independent connections establish transaction effects rather than treating batch counts as commit evidence.
     */
    @Test
    protected final void preparedBatchesPreserveTransactionOwnershipFailureEvidenceAndReuse() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            Supplier<JdbcClient.Statement> definition = preparedDefinition(variant);
            transactions.transaction(Tx.Type.REQUIRED,
                    () -> insertPrepared(definition, List.of("prepared-commit"), null));
            assertThat(fixture.committedByName("prepared-commit").isPresent(), is(true));
            assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                insertPrepared(definition, List.of("prepared-rollback"), null);
                throw new IllegalStateException("The application rejected the prepared batch.");
            }));
            assertThat(fixture.committedByName("prepared-rollback"), is(Optional.empty()));
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> insertPrepared(definition, Arrays.asList("batch-before", null, "batch-after"), null));
            assertThat(failure.result().entries().size(), is(3));
            assertThat(List.of("batch-before", "batch-after").stream()
                    .filter(name -> fixture.committedByName(name).isPresent()).toList(), is(committedAfterPartialFailure()));
            transactions.transaction(Tx.Type.REQUIRED, () -> {
                insertPrepared(definition, List.of("prepared-before-caught"), null);
                assertThrows(JdbcBatchException.class, () -> insertPrepared(definition, Arrays.asList((String) null), null));
                return null;
            });
            assertThat(fixture.committedByName("prepared-before-caught").isPresent(),
                       is(!constraintFailureAbortsTransaction()));
            insertPrepared(definition, List.of("prepared-after-failure"), null);
            assertThat(fixture.committedByName("prepared-after-failure").isPresent(), is(true));
            fixture.reset();
        }
    }

    @BeforeEach
    protected final void setupBatch() {
        beforeStartApplication();
        startBatch();
    }

    /**
     * Successful and failed batches leave observable committed state and allow later operations to reuse the client.
     */
    @Test
    protected final void executesSuccessEmptyAndConstraintFailureWithLaterReuse() {
        assertThat(insert(List.of(), null).entries().size(), is(0));
        JdbcClient.BatchResult result = insert(List.of("batch-one", "batch-two"), null);
        assertThat(result.entries().size(), is(2));
        assertThat(fixture.committedByName("batch-one").orElseThrow().email(), is(Optional.empty()));
        JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                () -> insert(Arrays.asList("batch-before", null, "batch-after"), null));
        assertThat(failure.result().entries().size(), is(3));
        List<String> committed = List.of("batch-before", "batch-after").stream()
                .filter(name -> fixture.committedByName(name).isPresent()).toList();
        assertThat(committed, is(committedAfterPartialFailure()));
        // A new operation verifies that failure did not strand the connection lease.
        insert(List.of("batch-reused"), null);
        assertThat(fixture.committedByName("batch-reused").isPresent(), is(true));
    }

    @Test
    protected final void verifiesCommitRollbackAndEscapingJoinedFailureOutsideTransaction() {
        transactions.transaction(Tx.Type.REQUIRED, () -> insert(List.of("batch-commit"), null));
        assertThat(fixture.committedByName("batch-commit").isPresent(), is(true));
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
            insert(List.of("batch-rollback"), null);
            throw new IllegalStateException("Roll back the batch.");
        }));
        assertThat(fixture.committedByName("batch-rollback"), is(Optional.empty()));
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
            insert(List.of("batch-joined"), null);
            try {
                transactions.transaction(Tx.Type.REQUIRED,
                        () -> insert(Arrays.asList("batch-partial", null), null));
            } catch (TxException _) {
                // The joined boundary has already observed the failure.
            }
            return null;
        }));
        assertThat(fixture.committedByName("batch-joined"), is(Optional.empty()));
        assertThat(fixture.committedByName("batch-partial"), is(Optional.empty()));
    }

    @Test
    protected final void captureFailureCaughtInsideTransactionDoesNotPreventCommit() {
        transactions.transaction(Tx.Type.REQUIRED, () -> {
            assertThrows(IllegalStateException.class, () -> client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                    .executeBatch(List.of("capture"), (_, _) -> { throw new IllegalStateException("Capture failed."); }));
            return insert(List.of("batch-after-capture"), null);
        });
        assertThat(fixture.committedByName("batch-after-capture").isPresent(), is(true));
    }

    @Test
    protected final void caughtBindingValidationFailuresPreserveTransactionCompletionAndStatementReuse() {
        transactions.transaction(Tx.Type.REQUIRED, () -> {
            insert(List.of("batch-before-validation"), null);
            JdbcClient.Statement incomplete = client.create("INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)");
            assertThrows(IllegalStateException.class, () -> incomplete.executeBatch(List.of("batch-incomplete"),
                    (bindings, name) -> bindings.bind(1, name)));
            assertThrows(IllegalStateException.class, incomplete::execute);
            JdbcClient.Statement prebound = client.create("INSERT INTO CONTACT (NAME) VALUES (?)")
                    .bind(1, "batch-after-validation");
            assertThrows(IllegalArgumentException.class, () -> prebound.executeBatch(List.of("batch-prebound"),
                    (bindings, name) -> bindings.bind(1, name)));
            assertThrows(IllegalArgumentException.class, () -> prebound.executeBatch(List.of(), (_, _) -> { }));
            assertThat(prebound.execute(), is(1L));
            return null;
        });
        assertThat(fixture.committedByName("batch-before-validation").isPresent(), is(true));
        assertThat(fixture.committedByName("batch-after-validation").isPresent(), is(true));
        assertThat(fixture.committedByName("batch-incomplete"), is(Optional.empty()));
        assertThat(fixture.committedByName("batch-prebound"), is(Optional.empty()));
    }

    /**
     * Independent observations distinguish joined work from NEW and UNSUPPORTED work after outer rollback.
     */
    @Test
    protected final void verifiesEveryPropagationUsingCommittedStateAfterOuterRollback() {
        for (Tx.Type type : List.of(Tx.Type.REQUIRED, Tx.Type.SUPPORTED, Tx.Type.MANDATORY, Tx.Type.NEW,
                                    Tx.Type.UNSUPPORTED)) {
            String name = "batch-propagation-" + type;
            assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                insert(List.of(name + "-outer"), null);
                transactions.transaction(type, () -> insert(List.of(name), null));
                throw new IllegalStateException("Roll back the outer scope.");
            }));
            assertThat(fixture.committedByName(name + "-outer"), is(Optional.empty()));
            boolean independent = type == Tx.Type.NEW || type == Tx.Type.UNSUPPORTED;
            assertThat(type.toString(), fixture.committedByName(name).isPresent(), is(independent));
        }
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.MANDATORY,
                () -> insert(List.of("batch-mandatory"), null)));
        assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED,
                () -> transactions.transaction(Tx.Type.NEVER, () -> insert(List.of("batch-never"), null))));
        assertThat(fixture.committedByName("batch-never"), is(Optional.empty()));
        transactions.transaction(Tx.Type.NEVER, () -> insert(List.of("batch-never-independent"), null));
        assertThat(fixture.committedByName("batch-never-independent").isPresent(), is(true));
    }

    /**
     * Describes whether this database aborts the transaction after a statement failure.
     *
     * @return True means that a caught constraint failure prevents earlier work from committing.
     */
    protected boolean constraintFailureAbortsTransaction() {
        return false;
    }

    /**
     * Independent state distinguishes driver transaction abort behavior from provider rollback policy.
     */
    @Test
    protected final void observesDriverTransactionEffectsAfterCaughtDirectFailure() {
        transactions.transaction(Tx.Type.REQUIRED, () -> {
            insert(List.of("batch-before-caught"), null);
            assertThrows(JdbcBatchException.class, () -> insert(Arrays.asList((String) null), null));
            return null;
        });
        assertThat(fixture.committedByName("batch-before-caught").isPresent(),
                   is(!constraintFailureAbortsTransaction()));
    }

    /**
     * Defines committed state for the fixture driver after a failed batch outside a transaction.
     *
     * @return The names identify rows that remain visible from a later independent operation.
     */
    protected List<String> committedAfterPartialFailure() {
        return List.of("batch-before");
    }

    @AfterEach
    protected final void stopBatch() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    private Supplier<JdbcClient.Statement> preparedDefinition(String variant) {
        Config config = Config.just(ConfigSources.classpath("configured-batches/" + variant + "application.yaml"));
        return client.prepare(config.get("application.batches.insert").asString().get(), List.of("name", "email"));
    }

    private void startBatch() {
        manager = ServiceRegistryManager.start();
        client = manager.registry().get(JdbcClient.class, Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                Qualifier.builder().typeName(Data.ProviderType.TYPE).value("jdbc").build());
        transactions = manager.registry().all(TxSupport.class).stream()
                .filter(support -> support.type().equals("jdbc")).findFirst().orElseThrow();
        fixture = service(DatabaseFixture.class);
        fixture.reset();
    }

    private JdbcClient.BatchResult insertPrepared(Supplier<JdbcClient.Statement> definition, List<String> names, String email) {
        return definition.get().executeBatch(oneShot(IntStream.range(0, names.size()).boxed().toList()), (bindings, index) -> {
            Objects.requireNonNull(bindings, "The batch bindings must not be null.");
            Objects.requireNonNull(index, "The batch index must not be null.");
            String name = names.get(index);
            if (name == null) {
                bindings.bindNull("name", JDBCType.VARCHAR);
            } else {
                bindings.bind("name", name);
            }
            if (email == null) {
                bindings.bindNull("email", JDBCType.VARCHAR);
            } else {
                bindings.bind("email", email);
            }
        });
    }
}
