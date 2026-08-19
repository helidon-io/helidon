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
package io.helidon.data.jdbc.tests.contract;

import java.util.List;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.application.transaction.FocusedTransactionOperations;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused transaction behavior that must hold for both imperative and generated repository use.
 */
public abstract class AbstractJdbcFocusedTransactionContract {
    private ServiceRegistryManager manager;
    private DatabaseFixture database;
    private FocusedTransactionOperations operations;

    /**
     * Returns the operations adapter for one application style.
     *
     * @return focused transaction operations adapter type
     */
    protected abstract Class<? extends FocusedTransactionOperations> operationsType();

    @BeforeEach
    protected final void setUpApplication() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(DatabaseFixture.class);
        operations = manager.registry().get(operationsType());
        database.resetTransactionMatrix();
    }

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Verifies a caught failure in a joined REQUIRED operation still marks the
     * outer local transaction rollback-only. The final assertion uses committed
     * state to prove the insert before the caught failure did not commit.
     */
    @Test
    protected void caughtJoinedFailureMarksOuterTransactionRollbackOnly() {
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            assertThat(operations.insertRequired("before-caught-failure"), is(1L));
            TxException failure = assertThrows(TxException.class, operations::failRequired);
            assertThat("Expected the failed JDBC operation in the transaction failure chain",
                       hasCause(failure, DataException.class),
                       is(true));
            return null;
        }));

        assertThat(database.committedTransactionValues(), is(List.of("baseline")));
    }

    /**
     * Verifies a NEW operation uses an independent local transaction. The inner
     * insert must remain committed even when the caller's outer REQUIRED
     * transaction rolls back.
     */
    @Test
    protected void newTransactionCommitSurvivesOuterRollback() {
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            assertThat(operations.insertRequired("outer-new-rollback"), is(1L));
            assertThat(operations.insertNew("inner-new-committed"), is(1L));
            throw new DeliberateRollbackException();
        }));

        assertThat(database.committedTransactionValues(), is(List.of("baseline", "inner-new-committed")));
    }

    /**
     * Verifies an UNSUPPORTED operation runs outside a suspended caller
     * transaction. The unsupported insert must remain committed even when the
     * caller's outer REQUIRED transaction rolls back.
     */
    @Test
    protected void unsupportedCommitSurvivesOuterRollback() {
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            assertThat(operations.insertRequired("outer-unsupported-rollback"), is(1L));
            assertThat(operations.insertUnsupported("unsupported-committed"), is(1L));
            throw new DeliberateRollbackException();
        }));

        assertThat(database.committedTransactionValues(), is(List.of("baseline", "unsupported-committed")));
    }

    @AfterEach
    protected final void shutDownApplication() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        for (Throwable current = throwable;
                current != null && current != current.getCause();
                current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static final class DeliberateRollbackException extends RuntimeException {
    }
}
