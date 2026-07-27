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
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.common.Weighted;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcPersistenceUnitFactoryTest {

    @Test
    void createsDistinctNamedAndProviderQualifiedClients() throws Exception {
        JdbcDataSource contacts = dataSource("contacts", "CONTACTS");
        JdbcDataSource audit = dataSource("audit", "AUDIT");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "contacts",
                "data.persistence-units.jdbc.0.data-source", "contacts-source",
                "data.persistence-units.jdbc.1.name", "audit",
                "data.persistence-units.jdbc.1.data-source", "audit-source")));
        List<ServiceInstance<DataSource>> sources = List.of(instance("contacts-source", contacts),
                                                            instance("audit-source", audit));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(() -> sources,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        List<Service.QualifiedInstance<JdbcClient>> clients = factory.services();

        assertThat(clients.size(), is(2));
        JdbcClient contactsClient = namedProviderClient(clients, "contacts");
        JdbcClient auditClient = namedProviderClient(clients, "audit");
        assertThat(contactsClient.create("SELECT NAME FROM UNIT_NAME").map(String.class).one(), is("CONTACTS"));
        assertThat(auditClient.create("SELECT NAME FROM UNIT_NAME").map(String.class).one(), is("AUDIT"));
    }

    @Test
    void rejectsMissingAndDuplicatePersistenceUnitConfiguration() {
        Config missing = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "missing",
                "data.persistence-units.jdbc.0.data-source", "does-not-exist")));
        JdbcPersistenceUnitFactory missingFactory = new JdbcPersistenceUnitFactory(List::of,
                                                                                   () -> missing,
                                                                                   new JdbcTransactionConnectionManager());
        assertThrows(DataException.class, missingFactory::services);

        Config duplicate = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "same",
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.name", "same",
                "data.persistence-units.jdbc.1.data-source", "source")));
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:duplicate;DB_CLOSE_DELAY=-1");
        JdbcPersistenceUnitFactory duplicateFactory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("source", source)),
                () -> duplicate,
                new JdbcTransactionConnectionManager());
        assertThrows(DataException.class, duplicateFactory::services);
    }

    @Test
    void validatesEveryUnitBeforeActivatingTheFirstDatasource() {
        Config duplicate = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "same",
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.name", "same",
                "data.persistence-units.jdbc.1.data-source", "source")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("An earlier unit activated its datasource before validation completed");
                },
                () -> duplicate,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("Duplicate JDBC persistence-unit name: same"));
    }

    @Test
    void validatesALaterConnectionSourceBeforeActivatingTheFirstDatasource() {
        Config invalidLaterUnit = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "first",
                "data.persistence-units.jdbc.0.data-source", "source",
                "data.persistence-units.jdbc.1.name", "invalid")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("An earlier unit activated its datasource before validation completed");
                },
                () -> invalidLaterUnit,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("invalid"));
        assertThat(failure.getMessage(), containsString("exactly one connection source"));
    }

    @Test
    void createsAClientFromExistingDirectConnectionConfiguration() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url",
                "jdbc:h2:mem:direct_unit;DB_CLOSE_DELAY=-1",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name",
                "org.h2.Driver",
                "data.persistence-units.jdbc.0.init-script",
                "jdbc-bootstrap-init.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), Service.Named.DEFAULT_NAME);

        assertThat(client.create("SELECT DATA_VALUE FROM SCRIPT_VALUE ORDER BY ID").map(String.class).list(),
                   is(List.of("first;value", "second 'quoted;value'")));
    }

    @Test
    void rejectsMissingConnectionSourceBeforeResolvingDependenciesOrScripts() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "missing-source",
                "data.persistence-units.jdbc.0.init-script", "does-not-exist.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Connection-source validation activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("missing-source"));
        assertThat(failure.getMessage(), containsString("exactly one connection source"));
        assertThat(failure.getMessage(), containsString("data-source or connection"));
    }

    @Test
    void rejectsAmbiguousConnectionSourceBeforeResolvingEitherSource() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "ambiguous-source",
                "data.persistence-units.jdbc.0.data-source", "must-not-resolve",
                "data.persistence-units.jdbc.0.connection.url", "jdbc:must-not-connect",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "missing.Driver",
                "data.persistence-units.jdbc.0.init-script", "does-not-exist.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Connection-source validation activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("ambiguous-source"));
        assertThat(failure.getMessage(), containsString("exactly one connection source"));
        assertThat(failure.getMessage(), containsString("data-source or connection"));
    }

    @Test
    void rejectsBlankDatasourceNameBeforeActivatingDatasourceServices() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "blank-source",
                "data.persistence-units.jdbc.0.data-source", "   ")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Blank datasource name activated datasource services");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("blank-source"));
        assertThat(failure.getMessage(), containsString("data-source must not be blank"));
    }

    @Test
    void executesDropBeforeInitForNamedDataSource() throws Exception {
        JdbcDataSource source = dataSource("bootstrap_order", "obsolete");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SCRIPT_VALUE (ID INTEGER PRIMARY KEY, DATA_VALUE VARCHAR(80))");
            statement.execute("INSERT INTO SCRIPT_VALUE VALUES (99, 'obsolete')");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "bootstrap",
                "data.persistence-units.jdbc.0.data-source", "bootstrap-source",
                "data.persistence-units.jdbc.0.drop-script", "jdbc-bootstrap-drop.sql",
                "data.persistence-units.jdbc.0.init-script", "jdbc-bootstrap-init.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("bootstrap-source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), "bootstrap");

        assertThat(client.create("SELECT DATA_VALUE FROM SCRIPT_VALUE ORDER BY ID").map(String.class).list(),
                   is(List.of("first;value", "second 'quoted;value'")));
    }

    @Test
    void executesDropScriptWithoutAnInitScript() throws Exception {
        JdbcDataSource source = dataSource("bootstrap_drop_only", "obsolete");
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SCRIPT_VALUE (ID INTEGER PRIMARY KEY)");
        }
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "drop-only",
                "data.persistence-units.jdbc.0.data-source", "drop-only-source",
                "data.persistence-units.jdbc.0.drop-script", "jdbc-bootstrap-drop.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("drop-only-source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        JdbcClient client = namedProviderClient(factory.services(), "drop-only");

        assertThrows(DataException.class,
                     () -> client.create("SELECT COUNT(*) FROM SCRIPT_VALUE").map(Long.class).one());
    }

    @Test
    void rejectsMissingScriptBeforeOpeningAConnection() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:missing_script;DB_CLOSE_DELAY=-1");
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "missing-script",
                "data.persistence-units.jdbc.0.data-source", "missing-script-source",
                "data.persistence-units.jdbc.0.init-script", "does-not-exist.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> List.of(instance("missing-script-source", source)),
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("missing-script"));
        assertThat(failure.getMessage(), containsString("does-not-exist.sql"));
    }

    @Test
    void returnsScriptConnectionAfterSqlFailure() {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:h2:mem:invalid_script;DB_CLOSE_DELAY=-1");
        poolConfig.setMaximumPoolSize(1);
        poolConfig.setConnectionTimeout(1_000);
        try (HikariDataSource source = new HikariDataSource(poolConfig)) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "invalid-script",
                    "data.persistence-units.jdbc.0.data-source", "invalid-script-source",
                    "data.persistence-units.jdbc.0.init-script", "jdbc-bootstrap-invalid.sql")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("invalid-script-source", source)),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            DataException failure = assertThrows(DataException.class, factory::services);

            assertThat(failure.getMessage(), containsString("jdbc-bootstrap-invalid.sql"));
            assertThat(failure.getMessage(), containsString("statement 2"));
            assertThat(failure.getCause(), instanceOf(SQLException.class));
            assertThat(source.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    @Test
    void commitsBootstrapWorkWhenDatasourceDisablesAutoCommit() {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:h2:mem:manual_bootstrap;DB_CLOSE_DELAY=-1");
        poolConfig.setAutoCommit(false);
        poolConfig.setMaximumPoolSize(1);
        try (HikariDataSource source = new HikariDataSource(poolConfig)) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "manual-bootstrap",
                    "data.persistence-units.jdbc.0.data-source", "manual-bootstrap-source",
                    "data.persistence-units.jdbc.0.init-script", "jdbc-bootstrap-init.sql")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("manual-bootstrap-source", source)),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            JdbcClient client = namedProviderClient(factory.services(), "manual-bootstrap");

            assertThat(client.create("SELECT COUNT(*) FROM SCRIPT_VALUE").map(Long.class).one(), is(2L));
        }
    }

    @Test
    void rollsBackBootstrapWorkWhenDatasourceDisablesAutoCommit() throws Exception {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:h2:mem:manual_bootstrap_failure;DB_CLOSE_DELAY=-1");
        poolConfig.setAutoCommit(false);
        poolConfig.setMaximumPoolSize(1);
        try (HikariDataSource source = new HikariDataSource(poolConfig)) {
            try (var connection = source.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE BOOTSTRAP_TX (ID INTEGER PRIMARY KEY)");
                connection.commit();
            }
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "manual-bootstrap-failure",
                    "data.persistence-units.jdbc.0.data-source", "manual-bootstrap-failure-source",
                    "data.persistence-units.jdbc.0.init-script", "jdbc-bootstrap-transaction-invalid.sql")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("manual-bootstrap-failure-source", source)),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            assertThrows(DataException.class, factory::services);

            try (var connection = source.getConnection();
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM BOOTSTRAP_TX")) {
                resultSet.next();
                assertThat(resultSet.getLong(1), is(0L));
                connection.rollback();
            }
        }
    }

    @Test
    void rejectsEveryUnterminatedBootstrapLexicalRegionBeforeConnecting() {
        for (String resource : List.of("jdbc-bootstrap-unterminated-quote.sql",
                                       "jdbc-bootstrap-unterminated-comment.sql",
                                       "jdbc-bootstrap-unterminated-dollar.sql")) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.name", "malformed",
                    "data.persistence-units.jdbc.0.data-source", "malformed-source",
                    "data.persistence-units.jdbc.0.init-script", resource)));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                    () -> List.of(instance("malformed-source", new FailingDataSource())),
                    () -> config,
                    new JdbcTransactionConnectionManager());

            DataException failure = assertThrows(DataException.class, factory::services);

            assertThat(failure.getMessage(), containsString("unterminated"));
            assertThat(failure.getMessage(), containsString(resource));
        }
    }

    @Test
    void directDatasourceUsesIndependentPropertiesAndReportsUnsupportedTimeouts() throws Exception {
        Driver driver = mock(Driver.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        Connection credentialConnection = mock(Connection.class);
        List<Properties> received = new ArrayList<>();
        when(driver.connect(anyString(), any(Properties.class))).thenAnswer(invocation -> {
            Properties properties = invocation.getArgument(1);
            Properties snapshot = new Properties();
            snapshot.putAll(properties);
            received.add(snapshot);
            properties.setProperty("driver-mutation", "must-not-escape");
            return switch (received.size()) {
            case 1 -> firstConnection;
            case 2 -> secondConnection;
            default -> credentialConnection;
            };
        });
        ConnectionConfig config = ConnectionConfig.builder()
                .url("jdbc:test:properties")
                .username("configured-user")
                .password("configured-password".toCharArray())
                .build();
        DataSource dataSource = directDataSource(config, driver);

        assertThat(dataSource.getConnection(), sameInstance(firstConnection));
        assertThat(dataSource.getConnection(), sameInstance(secondConnection));
        assertThat(dataSource.getConnection(null, null), sameInstance(credentialConnection));

        assertThat(received.get(0).getProperty("user"), is("configured-user"));
        assertThat(received.get(0).getProperty("password"), is("configured-password"));
        assertThat(received.get(1).getProperty("user"), is("configured-user"));
        assertThat(received.get(1).containsKey("driver-mutation"), is(false));
        assertThat(received.get(2).isEmpty(), is(true));
        dataSource.setLoginTimeout(0);
        assertThat(dataSource.getLoginTimeout(), is(0));
        assertThrows(IllegalArgumentException.class, () -> dataSource.setLoginTimeout(-1));
        assertThrows(SQLFeatureNotSupportedException.class, () -> dataSource.setLoginTimeout(1));
    }

    /**
     * Locates the client carrying both the persistence-unit name and the JDBC
     * provider qualifier.
     *
     * @param clients qualified clients
     * @param name persistence-unit name
     * @return matching client
     */
    private static JdbcClient namedProviderClient(List<Service.QualifiedInstance<JdbcClient>> clients, String name) {
        Qualifier named = Qualifier.createNamed(name);
        return clients.stream()
                .filter(client -> client.qualifiers().contains(named))
                .filter(client -> client.qualifiers().size() == 2)
                .findFirst()
                .orElseThrow()
                .get();
    }

    /**
     * Creates a datasource with one identifying row.
     *
     * @param database in-memory database name
     * @param value identifying value
     * @return initialized datasource
     * @throws Exception when setup fails
     */
    private static JdbcDataSource dataSource(String database, String value) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE UNIT_NAME (NAME VARCHAR(20))");
            statement.execute("INSERT INTO UNIT_NAME VALUES ('" + value + "')");
        }
        return dataSource;
    }

    /**
     * Wraps a datasource as a named registry service instance.
     *
     * @param name datasource registry name
     * @param dataSource datasource value
     * @return named service instance
     */
    private static ServiceInstance<DataSource> instance(String name, DataSource dataSource) {
        return new TestServiceInstance(dataSource, Set.of(Qualifier.createNamed(name)));
    }

    /**
     * Instantiates the private direct datasource adapter for focused contract
     * testing without widening its production visibility.
     *
     * @param config direct connection configuration
     * @param driver recording driver
     * @return direct datasource
     * @throws Exception if the private implementation cannot be constructed
     */
    private static DataSource directDataSource(ConnectionConfig config, Driver driver) throws Exception {
        Class<?> type = Class.forName(JdbcPersistenceUnitFactory.class.getName() + "$DirectDataSource");
        Constructor<?> constructor = type.getDeclaredConstructor(ConnectionConfig.class, Driver.class);
        constructor.setAccessible(true);
        return (DataSource) constructor.newInstance(config, driver);
    }

    /** Datasource that proves malformed scripts fail before connection acquisition. */
    private static final class FailingDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new AssertionError("Malformed script acquired a connection");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new AssertionError("Malformed script acquired a connection");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private record TestServiceInstance(DataSource value,
                                       Set<Qualifier> qualifiers) implements ServiceInstance<DataSource> {
        @Override
        public DataSource get() {
            return value;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(TypeName.create(DataSource.class)));
        }

        @Override
        public TypeName scope() {
            return TypeName.create(Service.Singleton.class);
        }

        @Override
        public double weight() {
            return Weighted.DEFAULT_WEIGHT;
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create(JdbcPersistenceUnitFactoryTest.class);
        }
    }
}
