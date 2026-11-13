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
package io.helidon.tests.integration.data.jdbc.support;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.declarative.repository.CallableRoutineRepository;
import io.helidon.transaction.spi.TxSupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Injects callable faults around real driver objects without replacing database execution or pool ownership.
 */
public final class CallableFailureFixture implements AutoCloseable {
    private final HikariDataSource pool;
    private final ServiceRegistryManager manager;
    private final JdbcClient client;
    private final TxSupport transactions;
    private final boolean direct;
    private final AtomicReference<RuntimeException> metadataFailure = new AtomicReference<>();
    private final AtomicReference<Error> cleanupFailure = new AtomicReference<>();
    private boolean physicalName;

    /**
     * Creates a registry using one pooled connection to the existing routine schema.
     *
     * @param config The existing connection configuration supplies the schema and driver settings.
     * @throws NullPointerException The configuration is null.
     * @throws SQLException JDBC instrumentation cannot be configured.
     */
    public CallableFailureFixture(Config config) throws SQLException {
        Objects.requireNonNull(config, "The connection configuration must not be null.");
        String url = config.get("url").asString().get();
        direct = url.startsWith("jdbc:mysql:");
        HikariConfig settings = new HikariConfig();
        settings.setJdbcUrl(url);
        settings.setUsername(config.get("username").asString().get());
        settings.setPassword(config.get("password").asString().get());
        settings.setMaximumPoolSize(1);
        settings.setMinimumIdle(0);
        settings.setConnectionTimeout(1000);
        pool = new HikariDataSource(settings);
        ServiceRegistryManager started = null;
        try {
            DataSource source = mock(DataSource.class, delegatesTo(pool));
            doAnswer(_ -> {
                Connection connection = pool.getConnection();
                Connection wrappedConnection = mock(Connection.class, delegatesTo(connection));
                doAnswer(invocation -> {
                    CallableStatement statement = connection.prepareCall(invocation.getArgument(0));
                    CallableStatement wrappedStatement = mock(CallableStatement.class, delegatesTo(statement));
                    doAnswer(_ -> rows(statement.getResultSet())).when(wrappedStatement).getResultSet();
                    doAnswer(getter -> {
                        int index = getter.getArgument(0);
                        Object value = statement.getObject(index);
                        return value instanceof ResultSet resultSet ? rows(resultSet) : value;
                    }).when(wrappedStatement).getObject(anyInt());
                    doAnswer(_ -> {
                        statement.close();
                        Error failure = cleanupFailure.getAndSet(null);
                        if (failure != null) {
                            throw failure;
                        }
                        return null;
                    }).when(wrappedStatement).close();
                    return wrappedStatement;
                }).when(wrappedConnection).prepareCall(anyString());
                return wrappedConnection;
            }).when(source).getConnection();
            TestDataSourceFactory.dataSource("callable-failure-pool", source);
            TestConfigFactory.config(Config.just(ConfigSources.create(Map.of(
                    "data.clients.jdbc.0.name", "@default", "data.clients.jdbc.0.data-source", "callable-failure-pool"))));
            started = ServiceRegistryManager.start();
            client = started.registry().get(JdbcClient.class);
            transactions = started.registry().get(TxSupport.class);
            manager = started;
        } catch (SQLException | RuntimeException | Error failure) {
            if (started != null) {
                try {
                    started.shutdown();
                } catch (RuntimeException | Error closing) {
                    if (closing != failure) {
                        failure.addSuppressed(closing);
                    }
                }
            }
            try {
                pool.close();
            } catch (RuntimeException | Error closing) {
                if (closing != failure) {
                    failure.addSuppressed(closing);
                }
            }
            TestDataSourceFactory.reset();
            throw failure;
        }
    }

    /**
     * Returns the client participating in this fixture registry.
     *
     * @return The client shares the single connection pool.
     */
    public JdbcClient client() {
        return client;
    }

    /**
     * Returns local transaction coordination for this fixture registry.
     *
     * @return The transaction support uses this fixture client.
     */
    public TxSupport transactions() {
        return transactions;
    }

    /**
     * Returns the number of active pooled connections.
     *
     * @return The active connection count exposes lease recovery.
     */
    public int activeConnections() {
        return pool.getHikariPoolMXBean().getActiveConnections();
    }

    /**
     * Arms the next callable label or fallback name lookup with an unchecked JDBC failure.
     *
     * @param name The next lookup uses the physical name when true.
     */
    public void failMetadata(boolean name) {
        physicalName = name;
        metadataFailure.set(new IllegalArgumentException("Secret callable metadata failure."));
    }

    /**
     * Arms the next callable close after the real statement has released its resources.
     *
     * @param failure The injected error must retain its identity.
     * @throws NullPointerException The error is null.
     */
    public void failCleanup(Error failure) {
        Objects.requireNonNull(failure, "The cleanup error must not be null.");
        cleanupFailure.set(failure);
    }

    /**
     * Invokes a real direct result or cursor routine using the selected application style.
     *
     * @param generated The invocation uses the generated repository when true.
     * @param action The consumer processes the callable result within its scope.
     * @throws NullPointerException The consumer is null.
     */
    public void consume(boolean generated, Consumer<JdbcClient.CallResult> action) {
        Objects.requireNonNull(action, "The callable consumer must not be null.");
        if (generated) {
            CallableRoutineRepository repository = manager.registry().get(CallableRoutineRepository.class);
            if (direct) {
                repository.configuredDirect(action);
            } else {
                repository.configuredCursorCallback(1, action);
            }
        } else if (direct) {
            client.prepare("{call sp_direct()}").get().call().consume(action);
        } else {
            client.prepare("{? = call sp_cursor(:input)}").get().bind("input", 1).call()
                    .returns(JDBCType.REF_CURSOR).consume(action);
        }
    }

    @Override
    public void close() {
        try {
            manager.shutdown();
        } finally {
            try {
                pool.close();
            } finally {
                TestDataSourceFactory.reset();
            }
        }
    }

    private ResultSet rows(ResultSet real) throws SQLException {
        if (real == null) {
            return null;
        }
        ResultSet wrapped = mock(ResultSet.class, delegatesTo(real));
        ResultSetMetaData metadata = real.getMetaData();
        ResultSetMetaData wrappedMetadata = mock(ResultSetMetaData.class, delegatesTo(metadata));
        doAnswer(invocation -> {
            if (metadataFailure.get() != null) {
                if (physicalName) {
                    return "";
                }
                throw metadataFailure.getAndSet(null);
            }
            return metadata.getColumnLabel(invocation.getArgument(0));
        }).when(wrappedMetadata).getColumnLabel(anyInt());
        doAnswer(invocation -> {
            RuntimeException failure = metadataFailure.getAndSet(null);
            if (failure != null) {
                throw failure;
            }
            return metadata.getColumnName(invocation.getArgument(0));
        }).when(wrappedMetadata).getColumnName(anyInt());
        doAnswer(_ -> wrappedMetadata).when(wrapped).getMetaData();
        return wrapped;
    }
}
