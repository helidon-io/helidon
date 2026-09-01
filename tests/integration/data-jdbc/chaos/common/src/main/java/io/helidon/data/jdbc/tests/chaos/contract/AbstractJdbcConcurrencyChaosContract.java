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
import io.helidon.data.jdbc.tests.chaos.support.ChaosConcurrencyFixture;
import io.helidon.data.jdbc.tests.chaos.support.ChaosDatabaseFixture;
import io.helidon.data.jdbc.tests.chaos.support.ChaosFailureAssertions;
import io.helidon.data.jdbc.tests.chaos.support.ChaosSecrets;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestConfigFactory;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestDataSourceFactory;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bounded contract for real lock and one-connection-pool timeout recovery.
 */
@Tag("data-jdbc-chaos")
@Tag("data-jdbc-chaos-concurrency")
public abstract class AbstractJdbcConcurrencyChaosContract {
    private ServiceRegistryManager manager;
    private ChaosDatabaseFixture database;
    private ChaosContactOperations operations;
    private ChaosConcurrencyFixture fixture;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return chaos operation adapter type
     */
    protected abstract Class<? extends ChaosContactOperations> operationsType();

    /**
     * Publishes a bounded one-connection application pool and creates its concurrency controls.
     *
     * @return concurrency fixture
     * @throws Exception when the fixture cannot be created
     */
    protected abstract ChaosConcurrencyFixture beforeStartApplication() throws Exception;

    /**
     * Starts an isolated pooled application and establishes committed baseline rows.
     *
     * @throws Exception when the fixture cannot be created
     */
    @BeforeEach
    protected final void setUpApplication() throws Exception {
        fixture = beforeStartApplication();
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(ChaosDatabaseFixture.class);
        database.resetContacts();
        operations = manager.registry().get(operationsType());
    }

    /**
     * Proves a real database lock timeout rolls back, releases the only lease, and permits a later operation.
     *
     * @throws Exception when gate control fails
     */
    @Test
    @Timeout(10)
    protected final void lockTimeoutRollsBackAndAllowsRecovery() throws Exception {
        TxException failure;
        try (AutoCloseable _ = fixture.lockGate()) {
            failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                operations.updateGate(1L);
                return null;
            }));
        }

        ChaosFailureAssertions.assertSanitizedTransactionDataFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        fixture.assertPoolIdle();
        assertThat(database.committedGateValue(), is(0L));
        assertThat(operations.countContacts(), is(2L));
        fixture.assertPoolIdle();
    }

    /**
     * Proves bounded acquisition failure from an exhausted one-connection pool releases no hidden lease and recovers.
     *
     * @throws Exception when the fixture cannot hold the sole lease
     */
    @Test
    @Timeout(10)
    protected final void poolAcquisitionTimeoutAllowsRecoveryAfterLeaseReturn() throws Exception {
        RuntimeException failure;
        try (AutoCloseable _ = fixture.holdOnlyPoolLease()) {
            failure = assertThrows(RuntimeException.class, operations::countContacts);
        }

        ChaosFailureAssertions.assertSanitizedDataFailure(
                failure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        fixture.assertPoolIdle();
        assertThat(operations.countContacts(), is(2L));
        fixture.assertPoolIdle();
    }

    /**
     * Stops the isolated application and closes its bounded pool and control resources.
     *
     * @throws Exception when fixture cleanup fails
     */
    @AfterEach
    protected final void shutDownApplication() throws Exception {
        try {
            if (manager != null) {
                manager.shutdown();
                manager = null;
            }
        } finally {
            try {
                if (fixture != null) {
                    fixture.close();
                    fixture = null;
                }
            } finally {
                ChaosTestConfigFactory.reset();
                ChaosTestDataSourceFactory.reset();
            }
        }
    }
}
