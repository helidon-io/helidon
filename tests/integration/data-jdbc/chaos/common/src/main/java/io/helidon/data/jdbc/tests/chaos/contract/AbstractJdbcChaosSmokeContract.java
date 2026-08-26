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
package io.helidon.data.jdbc.tests.chaos.contract;

import io.helidon.data.jdbc.tests.chaos.application.ChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.application.ChaosSql;
import io.helidon.data.jdbc.tests.chaos.support.ChaosDatabaseFixture;
import io.helidon.data.jdbc.tests.chaos.support.ChaosFailureAssertions;
import io.helidon.data.jdbc.tests.chaos.support.ChaosSecrets;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestDataSourceFactory;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestConfigFactory;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Portable smoke contract for JDBC chaos scenarios that must behave the same through every application style.
 */
@Tag("data-jdbc-chaos")
@Tag("data-jdbc-chaos-smoke")
public abstract class AbstractJdbcChaosSmokeContract {
    private ServiceRegistryManager manager;
    private ChaosDatabaseFixture database;
    private ChaosContactOperations operations;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return chaos operation adapter type
     */
    protected abstract Class<? extends ChaosContactOperations> operationsType();

    /**
     * Asserts that a database leaf has returned every pool lease after a pool-backed chaos scenario.
     */
    protected void assertPoolIdle() {
        throw new UnsupportedOperationException("Pool recovery assertions are not configured for this database leaf.");
    }

    /**
     * Allows a database leaf to publish datasource-backed configuration before the service registry starts.
     *
     * @return close action for any datasource resources created by the leaf
     */
    protected AutoCloseable beforeStartPooledApplication() {
        throw new UnsupportedOperationException("Pooled configuration is not configured for this database leaf.");
    }

    @BeforeEach
    protected final void setUpApplication() {
        startApplication(true);
    }

    /**
     * Allows a database leaf to publish its dynamic JDBC configuration before the service registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Verifies that malformed SQL is reported as a sanitized data failure, does not expose the SQL canary, leaves the
     * baseline committed rows unchanged, and allows a later valid query through the same application adapter to succeed.
     */
    @Test
    protected final void malformedSqlFailureIsSanitizedAndAllowsRecoveryQuery() {
        RuntimeException failure = assertThrows(RuntimeException.class, operations::executeMalformedSql);

        ChaosFailureAssertions.assertSanitizedDataFailure(failure, ChaosSecrets.malformedSqlCanaries());
        assertThat(database.committedContactCount(), is(2L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Verifies that a constraint violation caused by a secret bind value is sanitized, leaves baseline state unchanged,
     * and does not prevent a later valid query through the same application adapter.
     */
    @Test
    protected final void constraintFailureIsSanitizedAndAllowsRecoveryQuery() {
        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> operations.insertContact(1L, ChaosSql.BIND_VALUE_CANARY));

        ChaosFailureAssertions.assertSanitizedDataFailure(failure, ChaosSecrets.bindValueCanaries());
        assertThat(database.committedContactCount(), is(2L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Verifies that a scalar conversion failure is sanitized, closes the failed result processing path, leaves committed
     * rows unchanged, and allows a later valid query through the same application adapter.
     */
    @Test
    protected final void conversionFailureIsSanitizedAndAllowsRecoveryQuery() {
        RuntimeException failure = assertThrows(RuntimeException.class, operations::executeConversionFailureQuery);

        ChaosFailureAssertions.assertSanitizedDataFailure(failure, ChaosSecrets.conversionCanaries());
        assertThat(database.committedContactCount(), is(2L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Verifies that generated-key execution returns a database-assigned identifier, commits the inserted row, and leaves
     * the adapter able to execute a later recovery query.
     */
    @Test
    protected final void generatedKeyInsertCommitsRowAndAllowsRecoveryQuery() {
        long id = operations.insertGeneratedContact("generated-chaos");

        assertThat(id > 0L, is(true));
        assertThat(database.committedGeneratedCountByName("generated-chaos"), is(1L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Verifies that a malformed SQL failure inside a local transaction rolls back prior JDBC work from that transaction,
     * reports a sanitized data failure through the transaction boundary, and allows later non-transactional recovery.
     */
    @Test
    protected final void transactionFailureRollsBackPriorJdbcWorkAndAllowsRecoveryQuery() {
        TxException failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            operations.insertContact(3L, ChaosSql.TRANSACTION_ROLLBACK_CANARY);
            operations.executeMalformedSql();
            return null;
        }));

        ChaosFailureAssertions.assertSanitizedTransactionDataFailure(
                failure,
                combine(ChaosSql.TRANSACTION_ROLLBACK_CANARY, ChaosSecrets.malformedSqlCanaries()));
        assertThat(database.committedContactCountByName(ChaosSql.TRANSACTION_ROLLBACK_CANARY), is(0L));
        assertThat(database.committedContactCount(), is(2L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Verifies that a malformed SQL failure from a one-connection pool returns the sole pool lease, preserves committed
     * state, and allows the same application adapter to recover through a later valid query.
     *
     * @throws Exception if the database leaf cannot close a test-owned datasource
     */
    @Test
    protected final void malformedSqlFailureReturnsOneConnectionPoolLease() throws Exception {
        shutDownApplication();
        try (AutoCloseable _ = beforeStartPooledApplication()) {
            startApplication(false);

            RuntimeException failure = assertThrows(RuntimeException.class, operations::executeMalformedSql);

            ChaosFailureAssertions.assertSanitizedDataFailure(failure, ChaosSecrets.malformedSqlCanaries());
            assertPoolIdle();
            assertThat(database.committedContactCount(), is(2L));
            assertThat(operations.countContacts(), is(2L));
            assertPoolIdle();
        } finally {
            shutDownApplication();
        }
    }

    @AfterEach
    protected final void shutDownApplication() {
        try {
            if (manager != null) {
                manager.shutdown();
                manager = null;
            }
        } finally {
            ChaosTestConfigFactory.reset();
            ChaosTestDataSourceFactory.reset();
        }
    }

    private static String[] combine(String canary, String[] canaries) {
        String[] result = new String[canaries.length + 1];
        result[0] = canary;
        System.arraycopy(canaries, 0, result, 1, canaries.length);
        return result;
    }

    private void startApplication(boolean defaultConfiguration) {
        if (defaultConfiguration) {
            beforeStartApplication();
        }
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(ChaosDatabaseFixture.class);
        database.resetContacts();
        operations = manager.registry().get(operationsType());
    }
}
