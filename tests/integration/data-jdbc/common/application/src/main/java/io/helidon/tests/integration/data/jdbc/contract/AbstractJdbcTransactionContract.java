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

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.application.transaction.TransactionContext;
import io.helidon.tests.integration.data.jdbc.application.transaction.TransactionMatrixOperations;
import io.helidon.tests.integration.data.jdbc.application.transaction.TransactionOperation;
import io.helidon.tests.integration.data.jdbc.application.transaction.TransactionPolicy;
import io.helidon.tests.integration.data.jdbc.support.DatabaseFixture;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Transaction policy, caller context, and JDBC operation matrix shared by both application styles.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractJdbcTransactionContract {
    private ServiceRegistryManager manager;
    private TransactionMatrixOperations operations;
    private DatabaseFixture database;

    /**
     * Returns the operations adapter for one application style.
     *
     * @return operations adapter type
     */
    protected abstract Class<? extends TransactionMatrixOperations> operationsType();

    @BeforeAll
    protected final void startApplication() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        operations = manager.registry().get(operationsType());
        database = manager.registry().get(DatabaseFixture.class);
    }

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Executes every policy, caller context, and operation combination.
     *
     * @return dynamic transaction tests
     */
    @TestFactory
    protected final Stream<DynamicTest> transactionMatrix() {
        return Stream.of(TransactionPolicy.values())
                .flatMap(policy -> Stream.of(TransactionContext.values())
                        .flatMap(context -> Stream.of(TransactionOperation.values())
                                .map(operation -> DynamicTest.dynamicTest(
                                        policy + " / " + context + " / " + operation,
                                        new TransactionScenario(policy, context, operation)))));
    }

    @AfterAll
    protected final void stopApplication() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    /**
     * Allows a database-specific leaf test to pace resource-intensive drivers between dynamic scenarios.
     */
    protected void afterTransactionScenario() {
    }

    private static final class DeliberateRollbackException extends RuntimeException {
    }

    private final class TransactionScenario implements Executable {
        private final TransactionPolicy policy;
        private final TransactionContext context;
        private final TransactionOperation operation;

        private TransactionScenario(TransactionPolicy policy, TransactionContext context, TransactionOperation operation) {
            this.policy = policy;
            this.context = context;
            this.operation = operation;
        }

        @Override
        public void execute() {
            try {
                database.resetTransactionMatrix();
                AtomicLong result = new AtomicLong(Long.MIN_VALUE);
                Throwable failure = null;
                try {
                    Callable<Long> invocation = switch (context) {
                    case OUTSIDE -> () -> operations.execute(policy, operation);
                    case REQUIRED_COMMIT -> () -> Tx.transaction(Tx.Type.REQUIRED,
                                                                 () -> operations.execute(policy, operation));
                    case REQUIRED_ROLLBACK -> () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                        result.set(operations.execute(policy, operation));
                        throw new DeliberateRollbackException();
                    });
                    };
                    result.set(invocation.call());
                } catch (Throwable throwable) {
                    failure = throwable;
                }

                boolean rejectedPolicy = context == TransactionContext.OUTSIDE && policy == TransactionPolicy.MANDATORY
                        || context != TransactionContext.OUTSIDE && policy == TransactionPolicy.NEVER;
                boolean expectedFailure = rejectedPolicy || context == TransactionContext.REQUIRED_ROLLBACK;
                if (expectedFailure) {
                    assertThat(failure, notNullValue());
                    if (rejectedPolicy) {
                        assertThat(failure, instanceOf(TxException.class));
                    } else {
                        boolean transactionFailure = false;
                        for (Throwable current = failure;
                                current != null && current != current.getCause();
                                current = current.getCause()) {
                            if (current instanceof TxException) {
                                transactionFailure = true;
                                break;
                            }
                        }
                        assertThat("Expected a transaction failure in the causal chain", transactionFailure, is(true));
                    }
                } else {
                    assertThat(failure, nullValue());
                }

                if (rejectedPolicy) {
                    assertThat(result.get(), is(Long.MIN_VALUE));
                } else if (operation == TransactionOperation.GENERATED_KEY) {
                    assertThat(result.get(), greaterThan(0L));
                } else {
                    assertThat(result.get(), is(1L));
                }

                long expectedRows;
                if (operation == TransactionOperation.QUERY || rejectedPolicy) {
                    expectedRows = 1L;
                } else if (context == TransactionContext.REQUIRED_ROLLBACK
                        && policy != TransactionPolicy.NEW
                        && policy != TransactionPolicy.UNSUPPORTED) {
                    expectedRows = 1L;
                } else if (operation == TransactionOperation.UPDATE) {
                    expectedRows = 0L;
                } else {
                    expectedRows = 2L;
                }
                assertThat(database.committedTransactionRowCount(), is(expectedRows));
            } finally {
                afterTransactionScenario();
            }
        }
    }
}
