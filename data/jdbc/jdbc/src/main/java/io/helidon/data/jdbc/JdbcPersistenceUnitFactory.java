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
package io.helidon.data.jdbc;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.common.SqlDriver;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

/**
 * Creates qualified JDBC clients from configured persistence units.
 */
@Service.Singleton
final class JdbcPersistenceUnitFactory implements Service.ServicesFactory<JdbcClient> {

    // All JDBC persistence units are read from this configuration branch.
    static final String CONFIG_KEY = "data.persistence-units.jdbc";

    private static final String NAME_CONFIG_KEY = "name";
    private static final String DATA_SOURCE_CONFIG_KEY = "data-source";
    private static final String CONNECTION_CONFIG_KEY = "connection";
    private static final String USER_PROPERTY = "user";
    private static final String PASSWORD_PROPERTY = "password";
    private static final Qualifier PROVIDER_QUALIFIER = Qualifier.builder()
            .typeName(Data.ProviderType.TYPE)
            .value(Jdbc.PROVIDER)
            .build();

    private final Supplier<List<ServiceInstance<DataSource>>> dataSources;
    private final Supplier<Config> config;
    private final JdbcTransactionConnectionManager connectionManager;

    /**
     * Creates the persistence-unit service factory.
     *
     * @param dataSources available datasource services
     * @param config application configuration
     * @param connectionManager transaction connection manager
     */
    @Service.Inject
    JdbcPersistenceUnitFactory(Supplier<List<ServiceInstance<DataSource>>> dataSources,
                               Supplier<Config> config,
                               JdbcTransactionConnectionManager connectionManager) {
        this.dataSources = Objects.requireNonNull(dataSources, "Datasource supplier must not be null");
        this.config = Objects.requireNonNull(config, "Config supplier must not be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "Connection manager must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public List<Service.QualifiedInstance<JdbcClient>> services() {
        List<Config> units = config.get().get(CONFIG_KEY).asNodeList().orElse(List.of());
        List<JdbcPersistenceUnitConfig> validatedUnits = new ArrayList<>(units.size());
        Set<String> names = new HashSet<>();
        // Validate every unit before driver loading or script execution can cause side effects.
        for (Config unitConfig : units) {
            validateConnectionSource(unitConfig);
            JdbcPersistenceUnitConfig unit = JdbcPersistenceUnitConfig.create(unitConfig);
            if (unit.name().isBlank()) {
                throw new DataException("JDBC persistence-unit name must not be blank");
            }
            if (!names.add(unit.name())) {
                throw new DataException("Duplicate JDBC persistence-unit name: " + unit.name());
            }
            validatedUnits.add(unit);
        }

        List<Service.QualifiedInstance<JdbcClient>> result = new ArrayList<>(validatedUnits.size());
        for (JdbcPersistenceUnitConfig unit : validatedUnits) {
            JdbcClient client = createClient(unit);
            Qualifier named = Qualifier.createNamed(unit.name());
            // A single view with both qualifiers avoids duplicate candidates for named lookups.
            result.add(Service.QualifiedInstance.create(client, named, PROVIDER_QUALIFIER));
        }
        return List.copyOf(result);
    }

    /**
     * Validates the raw unit before the shared SQL configuration decorator can
     * report a source-cardinality error without the persistence-unit context.
     *
     * @param unitConfig persistence-unit configuration node
     */
    private static void validateConnectionSource(Config unitConfig) {
        String unitName = unitConfig.get(NAME_CONFIG_KEY).asString().orElse(Service.Named.DEFAULT_NAME);
        Config dataSource = unitConfig.get(DATA_SOURCE_CONFIG_KEY);
        boolean hasDataSource = dataSource.exists();
        boolean hasConnection = unitConfig.get(CONNECTION_CONFIG_KEY).exists();
        if (hasDataSource == hasConnection) {
            throw new DataException("JDBC persistence unit '" + unitName
                                            + "' must configure exactly one connection source: "
                                            + "data-source or connection");
        }
        if (hasDataSource && dataSource.asString().orElse("").isBlank()) {
            throw new DataException("JDBC persistence unit '" + unitName
                                            + "' data-source must not be blank");
        }
    }

    /**
     * Resolves one unit's datasource and creates its client.
     *
     * @param unit persistence-unit configuration
     * @return configured client
     */
    private JdbcClient createClient(JdbcPersistenceUnitConfig unit) {
        DataSource dataSource = connectionSource(unit);
        List<Path> scripts = new ArrayList<>(2);
        // Drop runs before initialization when both scripts are configured.
        unit.dropScript().ifPresent(scripts::add);
        unit.initScript().ifPresent(scripts::add);
        JdbcScriptRunner.execute(unit.name(), dataSource, scripts);
        return new JdbcClientImpl(dataSource, connectionManager);
    }

    /**
     * Validates and resolves exactly one connection source.
     * <p>
     * Cardinality is validated before resolving either source so invalid
     * configuration cannot activate a datasource service or load a JDBC
     * driver as a side effect.
     *
     * @param unit persistence-unit configuration
     * @return configured datasource
     */
    private DataSource connectionSource(JdbcPersistenceUnitConfig unit) {
        boolean hasDataSource = unit.dataSource().isPresent();
        boolean hasConnection = unit.connection().isPresent();
        if (hasDataSource == hasConnection) {
            throw new DataException("JDBC persistence unit '" + unit.name()
                                            + "' must configure exactly one connection source: "
                                            + "data-source or connection");
        }
        if (hasDataSource) {
            String name = unit.dataSource().orElseThrow();
            if (name.isBlank()) {
                throw new DataException("JDBC persistence unit '" + unit.name()
                                                + "' data-source must not be blank");
            }
            return namedDataSource(name);
        }
        return directDataSource(unit.connection().orElseThrow());
    }

    /**
     * Resolves exactly one registry datasource by name.
     *
     * @param name datasource service name
     * @return matching datasource
     */
    private DataSource namedDataSource(String name) {
        Qualifier named = Qualifier.createNamed(name);
        List<ServiceInstance<DataSource>> matches = dataSources.get()
                .stream()
                .filter(instance -> instance.qualifiers().contains(named))
                .toList();
        if (matches.isEmpty()) {
            throw new DataException("No SQL datasource service is named '" + name + "'");
        }
        if (matches.size() > 1) {
            throw new DataException("Multiple SQL datasource services are named '" + name + "'");
        }
        return matches.getFirst().get();
    }

    /**
     * Adapts the existing direct connection configuration to a datasource.
     *
     * @param config direct connection configuration
     * @return datasource adapter
     */
    private static DataSource directDataSource(ConnectionConfig config) {
        return new DirectDataSource(config, SqlDriver.create(config).driver());
    }

    /**
     * Minimal DataSource adaptation for the existing direct SQL connection configuration.
     */
    private static final class DirectDataSource implements DataSource, JdbcTransactionConnectionManager.IdentitySource {

        private final String url;
        private final Driver driver;

        // Copied for each connection so a driver cannot mutate the shared defaults.
        private final Properties defaults;

        // Equivalent direct configurations must share one transaction identity.
        private final DirectIdentity transactionIdentity;

        private volatile PrintWriter logWriter;

        /**
         * Creates a datasource over one driver and connection configuration.
         *
         * @param config connection configuration
         * @param driver selected driver
         */
        private DirectDataSource(ConnectionConfig config, Driver driver) {
            this.url = config.url();
            this.driver = driver;
            this.defaults = new Properties();
            String username = config.username().orElse(null);
            char[] passwordChars = config.password().map(value -> value.clone()).orElse(null);
            // JDBC driver properties require string credentials. Keep the cloned
            // character array for the immutable datasource identity.
            String password = passwordChars == null ? null : new String(passwordChars);
            if (username != null) {
                defaults.setProperty(USER_PROPERTY, username);
            }
            if (password != null) {
                defaults.setProperty(PASSWORD_PROPERTY, password);
            }
            this.transactionIdentity = new DirectIdentity(url,
                                                          driver.getClass().getName(),
                                                          username,
                                                          passwordChars);
        }

        /** {@inheritDoc} */
        @Override
        public Connection getConnection() throws SQLException {
            return connect(copy(defaults));
        }

        /** {@inheritDoc} */
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties properties = copy(defaults);
            // Per-call credentials replace configured defaults. A null argument
            // removes the corresponding default.
            if (username == null) {
                properties.remove(USER_PROPERTY);
            } else {
                properties.setProperty(USER_PROPERTY, username);
            }
            if (password == null) {
                properties.remove(PASSWORD_PROPERTY);
            } else {
                properties.setProperty(PASSWORD_PROPERTY, password);
            }
            return connect(properties);
        }

        /** {@inheritDoc} */
        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        /** {@inheritDoc} */
        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        /** {@inheritDoc} */
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            if (seconds < 0) {
                throw new IllegalArgumentException("Login timeout must not be negative");
            }
            if (seconds != 0) {
                throw new SQLFeatureNotSupportedException("The direct JDBC datasource does not support login timeouts");
            }
        }

        /** {@inheritDoc} */
        @Override
        public int getLoginTimeout() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return driver.getParentLogger();
        }

        /** {@inheritDoc} */
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            Objects.requireNonNull(iface, "Unwrap type must not be null");
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            if (iface.isInstance(driver)) {
                return iface.cast(driver);
            }
            throw new SQLException("Direct JDBC datasource cannot unwrap to " + iface.getName());
        }

        /** {@inheritDoc} */
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            Objects.requireNonNull(iface, "Wrapper type must not be null");
            return iface.isInstance(this) || iface.isInstance(driver);
        }

        /** {@inheritDoc} */
        @Override
        public DirectIdentity transactionIdentity() {
            return transactionIdentity;
        }

        /**
         * Opens one physical connection and rejects a non-accepting driver.
         *
         * @param properties connection properties
         * @return opened connection
         * @throws SQLException when the driver cannot connect
         */
        private Connection connect(Properties properties) throws SQLException {
            Connection connection = driver.connect(url, properties);
            if (connection == null) {
                throw new SQLException("Configured JDBC driver does not accept URL: " + url);
            }
            return connection;
        }

        /**
         * Copies connection properties so a driver cannot mutate or retain the
         * adapter's shared defaults.
         *
         * @param source source properties
         * @return independent properties
         */
        private static Properties copy(Properties source) {
            Properties copy = new Properties();
            copy.putAll(source);
            return copy;
        }
    }

    /**
     * Immutable identity for equivalent direct datasource adapters.
     */
    private static final class DirectIdentity implements JdbcTransactionConnectionManager.StableIdentity {

        private final String url;
        private final String driverClass;
        private final String username;

        // The array is copied at construction so configuration changes cannot alter the identity.
        private final char[] password;

        /**
         * Creates a direct datasource identity.
         *
         * @param url JDBC URL
         * @param driverClass driver implementation name
         * @param username configured username
         * @param password configured password
         */
        private DirectIdentity(String url, String driverClass, String username, char[] password) {
            this.url = url;
            this.driverClass = driverClass;
            this.username = username;
            this.password = password == null ? null : password.clone();
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof DirectIdentity that
                    && url.equals(that.url)
                    && driverClass.equals(that.driverClass)
                    && Objects.equals(username, that.username)
                    && Arrays.equals(password, that.password);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = Objects.hash(url, driverClass, username);
            return 31 * result + Arrays.hashCode(password);
        }
    }
}
