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
import io.helidon.data.jdbc.tests.chaos.support.ChaosDatabaseFixture;
import io.helidon.data.jdbc.tests.chaos.support.ChaosFailureAssertions;
import io.helidon.data.jdbc.tests.chaos.support.ChaosSecrets;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestConfigFactory;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestDataSourceFactory;
import io.helidon.data.jdbc.tests.chaos.support.JdbcLifecycleFault;
import io.helidon.data.jdbc.tests.chaos.support.JdbcLifecycleFaultDataSource;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Live-driver contract for deterministic JDBC connection and transaction lifecycle failures.
 */
@Tag("data-jdbc-chaos")
@Tag("data-jdbc-chaos-lifecycle")
public abstract class AbstractJdbcLifecycleChaosContract {
    private ServiceRegistryManager manager;
    private ChaosDatabaseFixture database;
    private ChaosContactOperations operations;
    private JdbcLifecycleFaultDataSource dataSource;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return chaos operation adapter type
     */
    protected abstract Class<? extends ChaosContactOperations> operationsType();

    /**
     * Initializes the database and publishes a live-driver fault datasource for the next service registry.
     *
     * @return published fault datasource
     */
    protected abstract JdbcLifecycleFaultDataSource beforeStartApplication();

    /**
     * Starts an isolated application and establishes committed baseline rows before fault injection.
     */
    @BeforeEach
    protected final void setUpApplication() {
        dataSource = beforeStartApplication();
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(ChaosDatabaseFixture.class);
        database.resetContacts();
        operations = manager.registry().get(operationsType());
    }

    /**
     * Proves failure while inspecting auto-commit invalidates the rejected connection and permits a fresh operation.
     */
    @Test
    protected final void autoCommitInspectionFailureInvalidatesConnectionAndAllowsRecovery() {
        long connectionsBeforeFailure = dataSource.connectionsCreated();
        long inspectionsBeforeFailure = dataSource.calls(JdbcLifecycleFault.GET_AUTO_COMMIT);
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        long closesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        dataSource.arm(JdbcLifecycleFault.GET_AUTO_COMMIT);

        RuntimeException failure = assertThrows(RuntimeException.class, operations::countContacts);

        ChaosFailureAssertions.assertSanitizedDataFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(dataSource.calls(JdbcLifecycleFault.GET_AUTO_COMMIT) - inspectionsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - closesBeforeFailure, is(1L));
        assertThat(operations.countContacts(), is(2L));
        assertThat(dataSource.connectionsCreated(), greaterThan(connectionsBeforeFailure));
    }

    /**
     * Proves failure disabling auto-commit prevents transaction work, invalidates the connection, and permits recovery.
     */
    @Test
    protected final void autoCommitDisableFailurePreventsTransactionWorkAndAllowsRecovery() {
        long disablesBeforeFailure = dataSource.calls(JdbcLifecycleFault.DISABLE_AUTO_COMMIT);
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        long closesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        dataSource.arm(JdbcLifecycleFault.DISABLE_AUTO_COMMIT);

        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> Tx.transaction(Tx.Type.REQUIRED, operations::countContacts));

        ChaosFailureAssertions.assertSanitizedTransactionDataFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(dataSource.calls(JdbcLifecycleFault.DISABLE_AUTO_COMMIT) - disablesBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - closesBeforeFailure, is(1L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Proves commit failure reports an unknown outcome, avoids auto-commit restoration, invalidates, and recovers.
     */
    @Test
    protected final void commitFailureHasUnknownOutcomeAndAllowsRecovery() {
        long connectionsBeforeFailure = dataSource.connectionsCreated();
        long commitsBeforeFailure = dataSource.calls(JdbcLifecycleFault.COMMIT);
        long rollbacksBeforeFailure = dataSource.calls(JdbcLifecycleFault.ROLLBACK);
        long resetsBeforeFailure = dataSource.calls(JdbcLifecycleFault.RESET_AUTO_COMMIT);
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        long closesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        dataSource.arm(JdbcLifecycleFault.COMMIT);

        TxException failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            operations.insertContact(31L, "commit-failure");
            return null;
        }));

        ChaosFailureAssertions.assertSanitizedFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(failure.getMessage(), containsString("outcome is unknown"));
        assertThat(dataSource.calls(JdbcLifecycleFault.COMMIT) - commitsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ROLLBACK) - rollbacksBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.RESET_AUTO_COMMIT) - resetsBeforeFailure, is(0L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - closesBeforeFailure, is(1L));
        assertThat(operations.countContacts(), greaterThanOrEqualTo(2L));
        assertThat(dataSource.connectionsCreated(), greaterThan(connectionsBeforeFailure));
    }

    /**
     * Proves rollback failure remains attached to the task failure, invalidates the connection, and permits recovery.
     */
    @Test
    protected final void rollbackFailureInvalidatesConnectionAndAllowsRecovery() {
        long rollbacksBeforeFailure = dataSource.calls(JdbcLifecycleFault.ROLLBACK);
        long resetsBeforeFailure = dataSource.calls(JdbcLifecycleFault.RESET_AUTO_COMMIT);
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        long closesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        dataSource.arm(JdbcLifecycleFault.ROLLBACK);

        TxException failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            operations.insertContact(32L, "rollback-failure");
            throw new IllegalStateException("Force application rollback.");
        }));

        assertThat(failure.getMessage(), containsString("transaction task failed"));
        assertThat(failure.getSuppressed().length, greaterThanOrEqualTo(1));
        assertThat(dataSource.calls(JdbcLifecycleFault.ROLLBACK) - rollbacksBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.RESET_AUTO_COMMIT) - resetsBeforeFailure, is(0L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - closesBeforeFailure, is(1L));
        assertThat(database.committedContactCountByName("rollback-failure"), is(0L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Proves auto-commit restoration failure preserves a confirmed commit, invalidates, and permits recovery.
     */
    @Test
    protected final void autoCommitRestorationFailurePreservesCommitAndAllowsRecovery() {
        long commitsBeforeFailure = dataSource.calls(JdbcLifecycleFault.COMMIT);
        long resetsBeforeFailure = dataSource.calls(JdbcLifecycleFault.RESET_AUTO_COMMIT);
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        dataSource.arm(JdbcLifecycleFault.RESET_AUTO_COMMIT);

        TxException failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            operations.insertContact(33L, "committed-before-reset-failure");
            return null;
        }));

        ChaosFailureAssertions.assertSanitizedFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(failure.getMessage(), containsString("was committed"));
        assertThat(dataSource.calls(JdbcLifecycleFault.COMMIT) - commitsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.RESET_AUTO_COMMIT) - resetsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(database.committedContactCountByName("committed-before-reset-failure"), is(1L));
        assertThat(operations.countContacts(), is(3L));
    }

    /**
     * Proves abort and close failures remain subordinate to commit failure and do not poison later application work.
     */
    @Test
    protected final void abortAndCloseFailuresRemainSubordinateAndAllowRecovery() {
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        long closesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        dataSource.arm(JdbcLifecycleFault.COMMIT, JdbcLifecycleFault.ABORT, JdbcLifecycleFault.CLOSE);

        TxException failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            operations.insertContact(34L, "invalidation-failure");
            return null;
        }));

        ChaosFailureAssertions.assertSanitizedFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(failure.getMessage(), containsString("outcome is unknown"));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - closesBeforeFailure, is(1L));
        assertThat(operations.countContacts(), greaterThanOrEqualTo(2L));
    }

    /**
     * Proves owned-connection close failure is reported, followed by invalidation, without preventing a later query.
     */
    @Test
    protected final void ownedConnectionCloseFailureIsReportedAndAllowsRecovery() {
        long closesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        long abortsBeforeFailure = dataSource.calls(JdbcLifecycleFault.ABORT);
        dataSource.arm(JdbcLifecycleFault.CLOSE);

        RuntimeException failure = assertThrows(RuntimeException.class, operations::countContacts);

        ChaosFailureAssertions.assertSanitizedDataFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - closesBeforeFailure, is(2L));
        assertThat(dataSource.calls(JdbcLifecycleFault.ABORT) - abortsBeforeFailure, is(1L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Proves statement-close failure is reported after successful materialization and does not prevent later work.
     */
    @Test
    protected final void statementCloseFailureIsReportedAndAllowsRecovery() {
        long statementClosesBeforeFailure = dataSource.calls(JdbcLifecycleFault.STATEMENT_CLOSE);
        long connectionClosesBeforeFailure = dataSource.calls(JdbcLifecycleFault.CLOSE);
        dataSource.arm(JdbcLifecycleFault.STATEMENT_CLOSE);

        RuntimeException failure = assertThrows(RuntimeException.class, operations::countContacts);

        ChaosFailureAssertions.assertSanitizedDataFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        assertThat(dataSource.calls(JdbcLifecycleFault.STATEMENT_CLOSE) - statementClosesBeforeFailure, is(1L));
        assertThat(dataSource.calls(JdbcLifecycleFault.CLOSE) - connectionClosesBeforeFailure, is(1L));
        assertThat(operations.countContacts(), is(2L));
    }

    /**
     * Stops the isolated application and force-closes resources retained by deliberate cleanup failures.
     */
    @AfterEach
    protected final void shutDownApplication() {
        try {
            if (manager != null) {
                manager.shutdown();
                manager = null;
            }
        } finally {
            try {
                if (dataSource != null) {
                    dataSource.close();
                    dataSource = null;
                }
            } finally {
                ChaosTestConfigFactory.reset();
                ChaosTestDataSourceFactory.reset();
            }
        }
    }
}
