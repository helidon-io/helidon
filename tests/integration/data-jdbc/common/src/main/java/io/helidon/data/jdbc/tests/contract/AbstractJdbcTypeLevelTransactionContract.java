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

import io.helidon.data.jdbc.tests.declarative.repository.TypeLevelRequiredTransactionRepository;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies direct type-level transaction annotation retention on generated repositories.
 */
public abstract class AbstractJdbcTypeLevelTransactionContract {
    private ServiceRegistryManager manager;
    private DatabaseFixture database;
    private TypeLevelRequiredTransactionRepository repository;

    @BeforeEach
    protected final void setUpApplication() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        database = manager.registry().get(DatabaseFixture.class);
        repository = manager.registry().get(TypeLevelRequiredTransactionRepository.class);
        database.resetTransactionMatrix();
    }

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Verifies an unannotated generated query method inherits the repository
     * type-level REQUIRED policy and starts a local transaction when called
     * outside an existing transaction.
     */
    @Test
    protected void typeLevelRequiredQueryStartsTransactionOutsideCallerTransaction() {
        assertThat(repository.query(), is(1L));
        assertThat(database.committedTransactionValues(), is(List.of("baseline")));
    }

    /**
     * Verifies an unannotated generated update method inherits the repository
     * type-level REQUIRED policy and commits when no caller transaction exists.
     */
    @Test
    protected void typeLevelRequiredUpdateCommitsOutsideCallerTransaction() {
        assertThat(repository.update(), is(1L));
        assertThat(database.committedTransactionValues(), is(List.of()));
    }

    /**
     * Verifies an unannotated generated-key method inherits the repository
     * type-level REQUIRED policy and commits both the row and key-return path
     * when no caller transaction exists.
     */
    @Test
    protected void typeLevelRequiredGeneratedKeyCommitsOutsideCallerTransaction() {
        assertThat(repository.generatedKey(), greaterThan(0L));
        assertThat(database.committedTransactionValues(), is(List.of("baseline", "generated")));
    }

    /**
     * Verifies an unannotated generated-key method joins the caller REQUIRED
     * transaction inherited from the repository type and rolls back with the
     * caller when that transaction fails.
     */
    @Test
    protected void typeLevelRequiredGeneratedKeyRollsBackWithCallerTransaction() {
        assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            assertThat(repository.generatedKey(), greaterThan(0L));
            throw new DeliberateRollbackException();
        }));

        assertThat(database.committedTransactionValues(), is(List.of("baseline")));
    }

    @AfterEach
    protected final void shutDownApplication() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    private static final class DeliberateRollbackException extends RuntimeException {
    }
}
