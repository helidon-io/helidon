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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceInstance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcPersistenceUnitConfigTest {

    /**
     * Verifies a later invalid cache policy is reported before an earlier
     * persistence unit can construct its configured resource.
     */
    @Test
    void validatesEveryCachePolicyBeforeConstructingResources() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "first",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.resource-path", "missing-private-script.sql",
                "data.persistence-units.jdbc.1.name", "second",
                "data.persistence-units.jdbc.1.data-source", "unused",
                "data.persistence-units.jdbc.1.properties.jdbc.parameter-count-cache.capacity", "4097",
                "data.persistence-units.jdbc.1.properties.jdbc.parameter-count-cache.max-sql-length", "1")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("JDBC persistence unit 'second'"));
        assertThat(failure.getMessage(), containsString("cache capacity must be between zero and 4096"));
        assertThat(failure.getMessage(), not(containsString("missing-private-script.sql")));
        assertThat(datasourceAccess.get(), is(false));
    }

    /**
     * Verifies a later invalid script policy is reported before an earlier
     * persistence unit can construct its configured resource.
     */
    @Test
    void validatesEveryScriptPolicyBeforeConstructingResources() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "first",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.resource-path", "missing-private-script.sql",
                "data.persistence-units.jdbc.1.name", "second",
                "data.persistence-units.jdbc.1.data-source", "unused",
                "data.persistence-units.jdbc.1.properties.jdbc.scripts.max-resource-size", "65 MiB",
                "data.persistence-units.jdbc.1.properties.jdbc.scripts.max-total-size", "65 MiB")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("JDBC persistence unit 'second'"));
        assertThat(failure.getMessage(), containsString("maximum resource size must not exceed 67108864 bytes"));
        assertThat(failure.getMessage(), not(containsString("missing-private-script.sql")));
        assertThat(datasourceAccess.get(), is(false));
    }

    /**
     * Verifies exact inline plain text and Base64 limits are accepted for
     * independent persistence units before datasource resolution.
     */
    @Test
    void acceptsExactInlinePreflightLimits() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.ofEntries(
                Map.entry("data.persistence-units.jdbc.0.name", "plain"),
                Map.entry("data.persistence-units.jdbc.0.data-source", "missing"),
                Map.entry("data.persistence-units.jdbc.0.init-script.content-plain", "€"),
                Map.entry("data.persistence-units.jdbc.0.properties.jdbc.scripts.max-resource-size", "3"),
                Map.entry("data.persistence-units.jdbc.0.properties.jdbc.scripts.max-total-size", "3"),
                Map.entry("data.persistence-units.jdbc.1.name", "binary"),
                Map.entry("data.persistence-units.jdbc.1.data-source", "missing"),
                Map.entry("data.persistence-units.jdbc.1.init-script.content", "4oKs"),
                Map.entry("data.persistence-units.jdbc.1.properties.jdbc.scripts.max-resource-size", "3"),
                Map.entry("data.persistence-units.jdbc.1.properties.jdbc.scripts.max-total-size", "3"))));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), is("No SQL datasource service is named 'missing'."));
        assertThat(datasourceAccess.get(), is(true));
    }

    /**
     * Verifies one additional UTF-8 byte is rejected before inline plain text
     * resource construction and datasource access.
     */
    @Test
    void rejectsPlainInlineContentOneByteAboveTheLimit() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "plain",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.content-plain", "€x",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-resource-size", "3",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-total-size", "3")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("per resource bootstrap byte limit of 3"));
        assertThat(datasourceAccess.get(), is(false));
    }

    /**
     * Verifies one additional decoded byte is rejected before inline Base64
     * resource construction and datasource access.
     */
    @Test
    void rejectsBase64InlineContentOneByteAboveTheLimit() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "binary",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.content", "4oKsWA==",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-resource-size", "3",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-total-size", "3")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("per resource bootstrap byte limit of 3"));
        assertThat(datasourceAccess.get(), is(false));
    }

    /**
     * Verifies drop and initialization preflight share one aggregate byte
     * budget in execution order.
     */
    @Test
    void sharesInlinePreflightBudgetAcrossDropAndInitialization() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "shared",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.drop-script.content-plain", "abc",
                "data.persistence-units.jdbc.0.init-script.content-plain", "def",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-resource-size", "4",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-total-size", "5")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("configured init script"));
        assertThat(failure.getMessage(), containsString("aggregate bootstrap byte limit of 5"));
        assertThat(datasourceAccess.get(), is(false));
    }

    /**
     * Verifies malformed inline Base64 is rejected with a safe diagnostic
     * before resource construction and datasource access.
     */
    @Test
    void rejectsInvalidInlineBase64DuringPreflight() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        String invalidContent = "private%%%content";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "binary",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.content", invalidContent)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("invalid Base64 content"));
        assertThat(failure.getMessage(), not(containsString(invalidContent)));
        assertThat(datasourceAccess.get(), is(false));
    }

    /**
     * Verifies the factory carries a configured statement limit into the
     * script runner before datasource resolution.
     */
    @Test
    void appliesConfiguredStatementLimitToBootstrapParsing() {
        AtomicBoolean datasourceAccess = new AtomicBoolean();
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "statements",
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.content-plain", "A;B;",
                "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-statements", "1")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    datasourceAccess.set(true);
                    return List.of();
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(), containsString("bootstrap statement limit of 1"));
        assertThat(datasourceAccess.get(), is(false));
    }

    @Test
    void acceptsDirectConnectionWithoutCredentials() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "connection.url", "jdbc:mysql://localhost/test",
                "connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver")));

        JdbcPersistenceUnitConfig unit = JdbcPersistenceUnitConfig.create(config);

        assertThat(unit.connection().orElseThrow().username().isEmpty(), is(true));
        assertThat(unit.connection().orElseThrow().password().isEmpty(), is(true));
    }

    @Test
    void mapsConfiguredResourceAndAcceptsProgrammaticResource() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data-source", "source",
                "init-script.content-plain", "SELECT 1")));

        JdbcPersistenceUnitConfig configured = JdbcPersistenceUnitConfig.create(config);
        JdbcPersistenceUnitConfig programmatic = JdbcPersistenceUnitConfig.builder()
                .dataSource("source")
                .initScript(Resource.create("programmatic script", "SELECT 1"))
                .buildPrototype();

        assertThat(configured.initScript().orElseThrow().sourceType(), is(Resource.Source.CONTENT));
        assertThat(configured.initScript().orElseThrow().string(), is("SELECT 1"));
        assertThat(programmatic.initScript().orElseThrow().sourceType(), is(Resource.Source.CONTENT));
        assertThat(programmatic.initScript().orElseThrow().string(), is("SELECT 1"));
    }

    @Test
    void rejectsConfiguredUriBeforeResourceConstructionOrDatasourceResolution() {
        String secret = "private-bootstrap-token";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.uri", "not a URI " + secret)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("URI validation must precede datasource resolution");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration does not support a URI value for 'init-script'."));
        assertThat(failure.getMessage(), not(containsString(secret)));
        assertThat(failure.getCause(), nullValue());
    }

    @Test
    void reportsScalarBootstrapScriptConfigurationWithoutInternalDescriptors() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", "jdbc:mysql://localhost/test",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.drop-script", "drop.sql",
                "data.persistence-units.jdbc.0.init-script", "init.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration has invalid values for 'drop-script' and 'init-script'. "
                              + "Configure 'drop-script' and 'init-script' as a resource object. Supported resource "
                              + "keys are 'path', 'resource-path', 'content-plain', and 'content'."));
        assertThat(failure.getMessage(), not(containsString("unspecified")));
        assertThat(failure.getMessage(), not(containsString("#1")));
    }

    @Test
    void reportsSingleScalarBootstrapScriptConfigurationWithTheInvalidKey() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", "jdbc:mysql://localhost/test",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.drop-script", "drop.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration has invalid value for 'drop-script'. "
                              + "Configure 'drop-script' as a resource object. Supported resource keys are 'path', "
                              + "'resource-path', 'content-plain', and 'content'."));
    }

    /**
     * Verifies that a script resource with more than one source key is
     * rejected before generated resource construction reports a generic
     * configuration failure.
     */
    @Test
    void reportsBootstrapScriptConfigurationWithMoreThanOneSourceKey() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", "jdbc:mysql://localhost/test",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.drop-script.path", "private-drop.sql",
                "data.persistence-units.jdbc.0.drop-script.resource-path", "private-drop.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration has invalid value for 'drop-script'. Configure exactly one "
                              + "resource source key for 'drop-script'. Configured keys are 'path' and "
                              + "'resource-path'. Supported resource keys are 'path', 'resource-path', "
                              + "'content-plain', and 'content'."));
        assertThat(failure.getMessage(), not(containsString("private-drop.sql")));
        assertThat(failure.getCause(), nullValue());
    }

    @Test
    void reportsUnavailableFilesystemBootstrapScriptPath() {
        String missingPath = "private-missing-drop.sql";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", "jdbc:mysql://localhost/test",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.drop-script.path", missingPath)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration could not load the file drop script configured by "
                              + "'drop-script.path'. Ensure the filesystem path points to an existing file readable "
                              + "by the application process."));
        assertThat(failure.getMessage(), not(containsString(missingPath)));
    }

    @Test
    void reportsUnavailableClasspathBootstrapScriptResource() {
        String missingResource = "private-missing-drop.sql";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", "jdbc:mysql://localhost/test",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.drop-script.resource-path", missingResource)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration could not load the classpath drop script configured by "
                              + "'drop-script.resource-path'. Ensure the classpath resource exists in the application "
                              + "runtime classpath."));
        assertThat(failure.getMessage(), not(containsString(missingResource)));
    }

    @Test
    void driverDiscoveryFailureDoesNotExposeTheConfiguredUrl() {
        String url = "jdbc:missing://user:private-password@host/database?token=private-token";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "private-driver",
                "data.persistence-units.jdbc.0.connection.url", url)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit 'private-driver' could not resolve a JDBC driver for its direct connection."));
        assertThat(failure.getCause(), instanceOf(SQLException.class));
        assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
        assertNoSecret(failure, url, "private-password", "private-token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void datasourceActivationFailureDoesNotExposeProviderDiagnostics() {
        ServiceInstance<DataSource> service = mock(ServiceInstance.class);
        when(service.qualifiers()).thenReturn(Set.of(Qualifier.createNamed("private-source")));
        when(service.get()).thenThrow(new IllegalStateException(
                "jdbc:test://user:private-password@host/database?token=private-token"));
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.name", "private-datasource",
                "data.persistence-units.jdbc.0.data-source", "private-source")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(() -> List.of(service),
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit 'private-datasource' could not resolve SQL datasource service "
                              + "'private-source'."));
        assertThat(failure.getCause().getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' "
                              + "while resolving a SQL datasource service."));
        assertNoSecret(failure, "private-password", "private-token");
    }

    @Test
    void directDriverRuntimeFailureDoesNotExposeConnectionConfiguration() throws Exception {
        String url = "jdbc:test://user:private-password@host/database?token=private-token";
        IllegalStateException driverFailure = new IllegalStateException(url);
        Driver driver = new FailingDriver(url, driverFailure);
        DriverManager.registerDriver(driver);
        try {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.connection.url", url,
                    "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", driver.getClass().getName())));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                                () -> config,
                                                                                new JdbcTransactionConnectionManager());
            JdbcClient client = factory.services().getFirst().get();

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

            assertThat(failure.getMessage(),
                       is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' "
                                  + "while opening a direct JDBC connection."));
            assertThat(failure, not(sameInstance(driverFailure)));
            assertThat(failure.getCause(), nullValue());
            assertNoSecret(failure, "private-password", "private-token");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Verifies provider properties remain local while direct connection
     * credentials retain their established JDBC driver mapping.
     */
    @Test
    void doesNotForwardProviderPropertiesToTheJdbcDriver() throws Exception {
        String url = "jdbc:test:provider-properties";
        FailingDriver driver = new FailingDriver(url, new IllegalStateException("expected connection failure"));
        DriverManager.registerDriver(driver);
        try {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "data.persistence-units.jdbc.0.connection.url", url,
                    "data.persistence-units.jdbc.0.connection.username", "database-user",
                    "data.persistence-units.jdbc.0.connection.password", "database-password",
                    "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", driver.getClass().getName(),
                    "data.persistence-units.jdbc.0.properties.jdbc.parameter-count-cache.capacity", "12",
                    "data.persistence-units.jdbc.0.properties.jdbc.parameter-count-cache.max-sql-length", "512",
                    "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-resource-size", "1 MiB",
                    "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-total-size", "2 MiB",
                    "data.persistence-units.jdbc.0.properties.jdbc.scripts.max-statements", "25")));
            JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                                () -> config,
                                                                                new JdbcTransactionConnectionManager());
            JdbcClient client = factory.services().getFirst().get();

            assertThrows(IllegalStateException.class, () -> client.create("UPDATE TEST SET VALUE = 1").execute());

            Properties connectionProperties = driver.connectionProperties();
            assertThat(connectionProperties.size(), is(2));
            assertThat(connectionProperties.getProperty("user"), is("database-user"));
            assertThat(connectionProperties.getProperty("password"), is("database-password"));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private static void assertNoSecret(Throwable failure, String... secrets) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            for (Throwable suppressed : current.getSuppressed()) {
                messages.append(suppressed.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        for (String secret : secrets) {
            assertThat(messages.toString(), not(containsString(secret)));
        }
    }

    /**
     * JDBC driver used to exercise the production direct-connection path.
     */
    private static final class FailingDriver implements Driver {

        private final String url;
        private final RuntimeException failure;
        private Properties connectionProperties;

        private FailingDriver(String url, RuntimeException failure) {
            this.url = url;
            this.failure = failure;
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            connectionProperties = new Properties();
            connectionProperties.putAll(info);
            throw failure;
        }

        @Override
        public boolean acceptsURL(String url) {
            return this.url.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(FailingDriver.class.getName());
        }

        /**
         * Returns the properties received by the most recent connection
         * attempt.
         *
         * @return received connection properties
         */
        private Properties connectionProperties() {
            return connectionProperties;
        }
    }
}
