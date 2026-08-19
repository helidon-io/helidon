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
package io.helidon.data.jdbc.tests.chaos.h2;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.jdbc.tests.chaos.application.ChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.application.ChaosSql;
import io.helidon.data.jdbc.tests.chaos.declarative.DeclarativeChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.imperative.ImperativeChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.support.ChaosDatabaseFixture;
import io.helidon.data.jdbc.tests.chaos.support.ChaosFailureAssertions;
import io.helidon.data.jdbc.tests.chaos.support.ChaosH2Database;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestConfigFactory;
import io.helidon.data.jdbc.tests.chaos.support.ChaosTestDataSourceFactory;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Injects deterministic JDBC faults into H2 to verify that provider cleanup paths release resources after driver-level
 * failures that are difficult to provoke portably with real database behavior alone.
 */
@Tag("data-jdbc-chaos")
@Tag("data-jdbc-reference-exhaustive")
class H2DeterministicFaultInjectionTest {
    private FaultInjectionPlan faultPlan;
    private HikariDataSource dataSource;
    private ServiceRegistryManager manager;
    private ChaosDatabaseFixture database;

    @BeforeEach
    void setUp() {
        faultPlan = new FaultInjectionPlan();
        dataSource = ChaosH2Database.dataSource();
        ChaosTestDataSourceFactory.dataSource(ChaosH2Database.HIKARI_SOURCE_NAME,
                                              new FaultInjectingDataSource(dataSource, faultPlan));
        ChaosTestConfigFactory.config(ChaosH2Database.hikariConfig());
        manager = ServiceRegistryManager.start();
        database = registry().get(ChaosDatabaseFixture.class);
        database.resetContacts();
    }

    /**
     * Verifies that injected query execution failures do not leak the only pool connection and both adapter styles can
     * recover with a later query.
     */
    @Test
    void executeQueryFaultReleasesPoolLeaseAndAllowsRecoveryQuery() {
        assertDataFaultReleasesLease(ImperativeChaosContactOperations.class,
                                     FaultBoundary.EXECUTE_STATEMENT,
                                     ChaosContactOperations::countContacts);
        assertDataFaultReleasesLease(DeclarativeChaosContactOperations.class,
                                     FaultBoundary.EXECUTE_STATEMENT,
                                     ChaosContactOperations::countContacts);
    }

    /**
     * Verifies that injected bind failures return the pool lease before any statement execution reaches the database.
     */
    @Test
    void bindFaultReleasesPoolLeaseAndAllowsRecoveryQuery() {
        assertDataFaultReleasesLease(ImperativeChaosContactOperations.class,
                                     FaultBoundary.BIND_PARAMETER,
                                     operations -> operations.insertContact(3L, "bind-fault"));
        assertDataFaultReleasesLease(DeclarativeChaosContactOperations.class,
                                     FaultBoundary.BIND_PARAMETER,
                                     operations -> operations.insertContact(3L, "bind-fault"));
    }

    /**
     * Verifies that scalar mapping failures caused by result-set reads return the pool lease and preserve recovery.
     */
    @Test
    void resultSetReadFaultReleasesPoolLeaseAndAllowsRecoveryQuery() {
        assertDataFaultReleasesLease(ImperativeChaosContactOperations.class,
                                     FaultBoundary.RESULT_SET_READ,
                                     ChaosContactOperations::countContacts);
        assertDataFaultReleasesLease(DeclarativeChaosContactOperations.class,
                                     FaultBoundary.RESULT_SET_READ,
                                     ChaosContactOperations::countContacts);
    }

    /**
     * Verifies that generated-key retrieval failures do not leave the insert statement or connection checked out.
     */
    @Test
    void generatedKeysFaultReleasesPoolLeaseAndAllowsRecoveryQuery() {
        assertDataFaultReleasesLease(ImperativeChaosContactOperations.class,
                                     FaultBoundary.GENERATED_KEYS,
                                     operations -> operations.insertGeneratedContact("generated-key-fault"));
        assertDataFaultReleasesLease(DeclarativeChaosContactOperations.class,
                                     FaultBoundary.GENERATED_KEYS,
                                     operations -> operations.insertGeneratedContact("generated-key-fault"));
    }

    /**
     * Verifies that a rollback failure reported by the JDBC driver still returns the pool lease and leaves transactional
     * writes uncommitted.
     */
    @Test
    void rollbackFaultReleasesPoolLeaseAndPreservesRollback() {
        assertRollbackFaultReleasesLease(ImperativeChaosContactOperations.class);
        assertRollbackFaultReleasesLease(DeclarativeChaosContactOperations.class);
    }

    @AfterEach
    void tearDown() {
        try {
            if (manager != null) {
                manager.shutdown();
                manager = null;
            }
        } finally {
            ChaosTestConfigFactory.reset();
            ChaosTestDataSourceFactory.reset();
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            faultPlan = null;
        }
    }

    private void assertDataFaultReleasesLease(Class<? extends ChaosContactOperations> operationsType,
                                             FaultBoundary boundary,
                                             ChaosOperation operation) {
        ChaosContactOperations operations = registry().get(operationsType);
        faultPlan.arm(boundary);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> operation.execute(operations));

        ChaosFailureAssertions.assertSanitizedDataFailure(failure);
        assertPoolIdle();
        database.resetContacts();
        assertThat(operations.countContacts(), is(2L));
        assertPoolIdle();
    }

    private void assertRollbackFaultReleasesLease(Class<? extends ChaosContactOperations> operationsType) {
        ChaosContactOperations operations = registry().get(operationsType);
        faultPlan.arm(FaultBoundary.ROLLBACK);

        TxException failure = assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
            operations.insertContact(3L, ChaosSql.TRANSACTION_ROLLBACK_CANARY);
            throw new IllegalStateException("Trigger rollback after JDBC write.");
        }));

        assertThat(failure, notNullValue());
        assertPoolIdle();
        assertThat(database.committedContactCountByName(ChaosSql.TRANSACTION_ROLLBACK_CANARY), is(0L));
        assertThat(operations.countContacts(), is(2L));
        assertPoolIdle();
    }

    private void assertPoolIdle() {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    private ServiceRegistry registry() {
        return manager.registry();
    }

    private enum FaultBoundary {
        BIND_PARAMETER,
        COMMIT,
        CONNECTION_CLOSE,
        EXECUTE_STATEMENT,
        GENERATED_KEYS,
        PREPARE_STATEMENT,
        RESULT_SET_CLOSE,
        RESULT_SET_NEXT,
        RESULT_SET_READ,
        ROLLBACK,
        STATEMENT_CLOSE
    }

    private enum FaultTiming {
        BEFORE_DELEGATE,
        AFTER_DELEGATE
    }

    @FunctionalInterface
    private interface ChaosOperation {
        void execute(ChaosContactOperations operations);
    }

    private static final class FaultInjectionPlan {
        private static final String MESSAGE = "Injected JDBC chaos fault.";

        private FaultBoundary boundary;
        private boolean thrown;

        void arm(FaultBoundary boundary) {
            this.boundary = boundary;
            thrown = false;
        }

        void throwIf(FaultBoundary candidate) throws SQLException {
            if (boundary == candidate && !thrown) {
                thrown = true;
                throw new SQLException(MESSAGE);
            }
        }
    }

    private static final class FaultInjectingDataSource implements DataSource {
        private final DataSource delegate;
        private final FaultInjectionPlan plan;

        FaultInjectingDataSource(DataSource delegate, FaultInjectionPlan plan) {
            this.delegate = delegate;
            this.plan = plan;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(delegate.getConnection(), Connection.class, plan);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrap(delegate.getConnection(username, password), Connection.class, plan);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        private static <T> T wrap(T target, Class<T> type, FaultInjectionPlan plan) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                                                   new Class<?>[] {type},
                                                   new FaultInjectingInvocationHandler(target, plan)));
        }
    }

    private static final class FaultInjectingInvocationHandler implements java.lang.reflect.InvocationHandler {
        private final Object target;
        private final FaultInjectionPlan plan;

        FaultInjectingInvocationHandler(Object target, FaultInjectionPlan plan) {
            this.target = target;
            this.plan = plan;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            FaultTiming timing = timing(method);
            FaultBoundary boundary = boundary(method);
            if (timing == FaultTiming.BEFORE_DELEGATE && boundary != null) {
                plan.throwIf(boundary);
            }
            try {
                Object result = method.invoke(target, args);
                Object wrapped = wrapResult(method, result);
                if (timing == FaultTiming.AFTER_DELEGATE && boundary != null) {
                    plan.throwIf(boundary);
                }
                return wrapped;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        private FaultBoundary boundary(Method method) {
            Class<?> declaringType = method.getDeclaringClass();
            String methodName = method.getName();
            if (declaringType == Connection.class) {
                return connectionBoundary(methodName);
            }
            if (declaringType == PreparedStatement.class || declaringType == Statement.class) {
                return statementBoundary(methodName);
            }
            if (declaringType == ResultSet.class) {
                return resultSetBoundary(methodName);
            }
            return null;
        }

        private FaultBoundary connectionBoundary(String methodName) {
            return switch (methodName) {
            case "prepareStatement" -> FaultBoundary.PREPARE_STATEMENT;
            case "commit" -> FaultBoundary.COMMIT;
            case "rollback" -> FaultBoundary.ROLLBACK;
            case "close" -> FaultBoundary.CONNECTION_CLOSE;
            default -> null;
            };
        }

        private FaultBoundary resultSetBoundary(String methodName) {
            return switch (methodName) {
            case "close" -> FaultBoundary.RESULT_SET_CLOSE;
            case "next" -> FaultBoundary.RESULT_SET_NEXT;
            default -> methodName.startsWith("get") ? FaultBoundary.RESULT_SET_READ : null;
            };
        }

        private FaultBoundary statementBoundary(String methodName) {
            if (methodName.equals("close")) {
                return FaultBoundary.STATEMENT_CLOSE;
            }
            if (methodName.equals("getGeneratedKeys")) {
                return FaultBoundary.GENERATED_KEYS;
            }
            if (methodName.startsWith("execute")) {
                return FaultBoundary.EXECUTE_STATEMENT;
            }
            if (methodName.startsWith("set")) {
                return FaultBoundary.BIND_PARAMETER;
            }
            return null;
        }

        private FaultTiming timing(Method method) {
            String methodName = method.getName();
            if (methodName.equals("close")) {
                return FaultTiming.AFTER_DELEGATE;
            }
            return FaultTiming.BEFORE_DELEGATE;
        }

        private Object wrapResult(Method method, Object result) {
            if (result == null) {
                return null;
            }
            if (method.getReturnType() == PreparedStatement.class) {
                return FaultInjectingDataSource.wrap((PreparedStatement) result, PreparedStatement.class, plan);
            }
            if (method.getReturnType() == ResultSet.class) {
                return FaultInjectingDataSource.wrap((ResultSet) result, ResultSet.class, plan);
            }
            return result;
        }
    }
}
