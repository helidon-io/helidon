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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.data.jdbc.tests.chaos.application.ChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.support.ChaosDatabaseFixture;
import io.helidon.data.jdbc.tests.chaos.support.ChaosDisruptionController;
import io.helidon.data.jdbc.tests.chaos.support.ChaosDisruptionFixture;
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Portable observable contract for loss of a physical JDBC session while a transaction is executing.
 */
@Tag("data-jdbc-chaos")
@Tag("data-jdbc-chaos-disruption")
public abstract class AbstractJdbcConnectionLossChaosContract {
    private ServiceRegistryManager manager;
    private ChaosDatabaseFixture database;
    private ChaosContactOperations operations;
    private ChaosDisruptionFixture fixture;

    /**
     * Returns the adapter type for one application programming style.
     *
     * @return chaos operation adapter type
     */
    protected abstract Class<? extends ChaosContactOperations> operationsType();

    /**
     * Publishes a one-connection application pool and creates an independent database disruption controller.
     *
     * @return disruption fixture
     */
    protected abstract ChaosDisruptionFixture beforeStartApplication() throws Exception;

    /**
     * Starts an isolated pooled application and establishes committed baseline rows before disruption.
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
     * Proves a database-terminated in-flight session fails safely, rolls back, releases its lease, and is replaced.
     * This assertion also guards the unresolved {@code DATA-JDBC-CHAOS-001} H2 recovery finding.
     *
     * @throws Exception when deterministic scenario coordination or fixture cleanup fails
     */
    @Test
    @Timeout(30)
    protected final void terminatedSessionDuringExecutionIsInvalidatedAndAllowsRecovery() throws Exception {
        long terminatedSession;
        Throwable applicationFailure;
        ChaosDisruptionController controller = fixture.controller();
        try (AutoCloseable _ = controller.lockGate();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<Long> sessionId = new CompletableFuture<>();
            Future<Void> operation = executor.submit(() -> {
                try {
                    return Tx.transaction(Tx.Type.REQUIRED, () -> {
                        long id = operations.currentSessionId();
                        sessionId.complete(id);
                        operations.updateGate(1L);
                        return null;
                    });
                } catch (RuntimeException | Error failure) {
                    sessionId.completeExceptionally(failure);
                    throw failure;
                }
            });

            terminatedSession = sessionId.get(5, TimeUnit.SECONDS);
            controller.awaitGateWait(terminatedSession, Duration.ofSeconds(5));
            controller.terminateSession(terminatedSession);
            ExecutionException executionFailure = assertThrows(
                    ExecutionException.class, () -> operation.get(10, TimeUnit.SECONDS));
            applicationFailure = executionFailure.getCause();
        }

        assertThat(applicationFailure, instanceOf(TxException.class));
        ChaosFailureAssertions.assertSanitizedFailure(
                applicationFailure, ChaosSecrets.DATABASE_NAME_CANARY, ChaosSecrets.URL_CANARY);
        fixture.assertPoolIdle();
        assertThat(database.committedGateValue(), is(0L));
        assertThat(operations.countContacts(), is(2L));
        assertThat(operations.currentSessionId(), not(is(terminatedSession)));
        fixture.assertPoolIdle();
    }

    /**
     * Restores controller state before stopping the application and its one-connection pool.
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
