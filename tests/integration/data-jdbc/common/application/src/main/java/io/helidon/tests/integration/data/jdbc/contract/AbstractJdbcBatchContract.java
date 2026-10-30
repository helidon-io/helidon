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

import java.sql.JDBCType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.support.DatabaseFixture;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies execution and independent committed state through real database fixtures.
 */
public abstract class AbstractJdbcBatchContract {
    private ServiceRegistryManager manager;
    private JdbcClient client;
    private DatabaseFixture fixture;
    private TxSupport transactions;

    protected abstract void beforeStartApplication();

    protected final <T> T service(Class<T> type) {
        return manager.registry().get(type);
    }

    protected final JdbcClient client() {
        return client;
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

    @BeforeEach
    protected final void setupBatch() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        client = manager.registry().get(JdbcClient.class, Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                Qualifier.builder().typeName(Data.ProviderType.TYPE).value("jdbc").build());
        transactions = manager.registry().all(TxSupport.class).stream()
                .filter(support -> support.type().equals("jdbc")).findFirst().orElseThrow();
        fixture = service(DatabaseFixture.class);
        fixture.reset();
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
}
