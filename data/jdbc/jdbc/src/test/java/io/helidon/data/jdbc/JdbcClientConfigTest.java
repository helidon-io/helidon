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
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.SqlConfig;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcClientConfigTest {

    /**
     * Verifies the public builder creates a standalone client from inherited
     * direct connection settings without opening a connection.
     *
     * @throws SQLException when the test driver registration cannot be changed
     */
    @Test
    void buildsClientFromDirectConnection() throws SQLException {
        RecordingDriver driver = new RecordingDriver("jdbc:test:direct-builder");
        DriverManager.registerDriver(driver);
        try {
            JdbcClient client = JdbcClient.builder()
                    .connection(connection -> connection
                            .url(driver.url())
                            .jdbcDriverClassName(driver.getClass().getName()))
                    .build();

            assertThat(client.prototype().name(), is(Service.Named.DEFAULT_NAME));
            assertThat(client.prototype().connection().orElseThrow().url(), is(driver.url()));
            assertThat(driver.connectionAttempts(), is(0));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Verifies the public factory retains the exact immutable configuration
     * supplied by the application without opening a connection.
     *
     * @throws SQLException when the test driver registration cannot be changed
     */
    @Test
    void createsClientFromImmutableConfiguration() throws SQLException {
        RecordingDriver driver = new RecordingDriver("jdbc:test:immutable-configuration");
        DriverManager.registerDriver(driver);
        try {
            JdbcClientConfig config = JdbcClientConfig.builder()
                    .name("inventory")
                    .connection(connection -> connection
                            .url(driver.url())
                            .jdbcDriverClassName(driver.getClass().getName()))
                    .buildPrototype();

            JdbcClient client = JdbcClient.create(config);

            assertThat(client.prototype(), sameInstance(config));
            assertThat(client.prototype().name(), is("inventory"));
            assertThat(driver.connectionAttempts(), is(0));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Verifies the runtime type consumer factory delegates through the same
     * public builder and direct connection construction path.
     *
     * @throws SQLException when the test driver registration cannot be changed
     */
    @Test
    void createsClientFromBuilderConsumer() throws SQLException {
        RecordingDriver driver = new RecordingDriver("jdbc:test:builder-consumer");
        DriverManager.registerDriver(driver);
        try {
            JdbcClient client = JdbcClient.create(builder -> builder
                    .name("reporting")
                    .connection(connection -> connection
                            .url(driver.url())
                            .jdbcDriverClassName(driver.getClass().getName())));

            assertThat(client.prototype().name(), is("reporting"));
            assertThat(driver.connectionAttempts(), is(0));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Verifies the client configuration inherits both shared SQL source
     * choices and remains assignable to the shared contract.
     */
    @Test
    void inheritsSqlConfigurationSources() {
        Config configNode = Config.just(ConfigSources.create(Map.of(
                "name", "reporting",
                "data-source", "reporting-source")));
        JdbcClientConfig namedConfig = JdbcClientConfig.create(configNode);
        JdbcClientConfig directConfig = JdbcClientConfig.builder()
                .connection(connection -> connection.url("jdbc:example:local"))
                .buildPrototype();
        SqlConfig sqlConfig = namedConfig;

        assertThat(sqlConfig, sameInstance(namedConfig));
        assertThat(namedConfig.dataSource().orElseThrow(), is("reporting-source"));
        assertThat(directConfig.connection().orElseThrow().url(), is("jdbc:example:local"));
    }

    /**
     * Verifies invalid source selection produces a simple diagnostic without
     * including configuration values or object details.
     */
    @Test
    void rejectsInvalidSourceSelectionWithoutExposingConfiguration() {
        String sensitiveValue = "jdbc:example://private-host/secret-database";
        Config configNode = Config.just(ConfigSources.create(Map.of(
                "name", sensitiveValue)));

        DataException missingFailure = assertThrows(DataException.class, () -> JdbcClientConfig.create(configNode));
        DataException conflictFailure = assertThrows(
                DataException.class,
                () -> JdbcClient.builder()
                        .dataSource("private-source")
                        .connection(connection -> connection.url(sensitiveValue))
                        .buildPrototype());

        assertThat(missingFailure.getMessage(), is("Both connection and DataSource config options are missing"));
        assertThat(conflictFailure.getMessage(), is("Both connection and DataSource config options are present"));
        assertThat(missingFailure.getMessage(), not(containsString(sensitiveValue)));
        assertThat(conflictFailure.getMessage(), not(containsString("private-source")));
        assertThat(conflictFailure.getMessage(), not(containsString(sensitiveValue)));
    }

    /**
     * Verifies selecting both inherited connection sources is rejected before
     * a client can be constructed.
     */
    @Test
    void rejectsConflictingConnectionSources() {
        assertSourceConflict(() -> JdbcClient.builder()
                .dataSource("inventory-source")
                .connection(connection -> connection.url("jdbc:example:local"))
                .buildPrototype());
    }

    /**
     * Verifies invalid client and data source names fail during configuration validation.
     */
    @Test
    void rejectsInvalidClientAndDataSourceNames() {
        DataException blankClientName = assertThrows(
                DataException.class,
                () -> JdbcClient.builder().name("  ").dataSource("inventory-source").buildPrototype());
        DataException blankDataSourceName = assertThrows(
                DataException.class,
                () -> JdbcClient.builder().dataSource("  ").buildPrototype());

        assertThat(blankClientName.getMessage(), is("A JDBC client name must not be blank."));
        assertThat(blankDataSourceName.getMessage(), is("A JDBC data source name must not be blank."));
    }

    /**
     * Verifies typed provider options remain part of the effective client
     * configuration when a public client is constructed.
     */
    @Test
    void preservesParameterCountCacheConfiguration() {
        JdbcClientConfig config = JdbcClient.builder()
                .dataSource("inventory-source")
                .parameterCountCacheCapacity(17)
                .parameterCountCacheMaxSqlLength(2_048)
                .buildPrototype();

        assertThat(config.parameterCountCacheCapacity(), is(17));
        assertThat(config.parameterCountCacheMaxSqlLength(), is(2_048));
    }

    /**
     * Verifies named data source lookup sanitizes Service Registry activation
     * details before returning a public client construction failure.
     */
    @Test
    void sanitizesNamedDataSourceRegistryFailure() {
        String sensitiveDetail = "private-named-data-source-detail";
        ServiceRegistry originalRegistry = GlobalServiceRegistry.registry();
        ServiceRegistry failingRegistry = mock(ServiceRegistry.class);
        when(failingRegistry.first(any(Lookup.class)))
                .thenThrow(new ServiceRegistryException(sensitiveDetail,
                                                        new IllegalStateException("private-provider-cause")));
        GlobalServiceRegistry.registry(failingRegistry);
        try {
            JdbcClientConfig config = JdbcClient.builder()
                    .name("inventory")
                    .dataSource("inventory-source")
                    .buildPrototype();

            DataException failure = assertThrows(DataException.class, () -> JdbcClient.create(config));

            assertThat(failure.getMessage(),
                       is("JDBC client 'inventory' could not resolve SQL data source 'inventory-source'."));
            assertThat(failure.getCause().toString(), not(containsString(sensitiveDetail)));
            assertThat(failure.getCause().toString(), not(containsString("private-provider-cause")));
            assertThat(failure.getCause().getCause(), nullValue());
        } finally {
            GlobalServiceRegistry.registry(originalRegistry);
        }
    }

    /**
     * Verifies an unrelated runtime failure from named data source lookup is
     * not reclassified as a Service Registry failure.
     */
    @Test
    void propagatesUnexpectedNamedDataSourceRegistryFailure() {
        ServiceRegistry originalRegistry = GlobalServiceRegistry.registry();
        ServiceRegistry failingRegistry = mock(ServiceRegistry.class);
        IllegalStateException unexpected = new IllegalStateException("unexpected registry failure");
        when(failingRegistry.first(any(Lookup.class))).thenThrow(unexpected);
        GlobalServiceRegistry.registry(failingRegistry);
        try {
            JdbcClientConfig config = JdbcClient.builder()
                    .name("inventory")
                    .dataSource("inventory-source")
                    .buildPrototype();

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> JdbcClient.create(config));

            assertThat(failure, sameInstance(unexpected));
        } finally {
            GlobalServiceRegistry.registry(originalRegistry);
        }
    }

    /**
     * Verifies direct driver resolution failures do not expose connection
     * settings through the public construction path.
     */
    @Test
    void sanitizesDirectDriverResolutionFailure() {
        String sensitiveUrl = "jdbc:missing://private-host/database?token=private-token";
        String sensitiveDriver = "example.private.PrivateTokenDriver";

        DataException failure = assertThrows(
                DataException.class,
                () -> JdbcClient.builder()
                        .name("inventory")
                        .connection(connection -> connection
                                .url(sensitiveUrl)
                                .jdbcDriverClassName(sensitiveDriver))
                        .build());

        assertThat(failure.getMessage(),
                   is("JDBC client 'inventory' could not resolve a JDBC driver for its direct connection."));
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            diagnostic.append(current.getMessage()).append('\n');
            for (Throwable suppressed : current.getSuppressed()) {
                diagnostic.append(suppressed.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        assertThat(diagnostic.toString(), not(containsString(sensitiveUrl)));
        assertThat(diagnostic.toString(), not(containsString(sensitiveDriver)));
        assertThat(diagnostic.toString(), not(containsString("private-token")));
    }

    /**
     * Verifies generated diagnostic text does not reveal an inherited direct
     * connection password.
     */
    @Test
    void redactsDirectConnectionPasswordFromConfigurationText() {
        String sensitiveValue = "private-database-password";
        JdbcClientConfig.Builder builder = JdbcClient.builder()
                .connection(connection -> connection
                        .url("jdbc:example:local")
                        .password(sensitiveValue));
        JdbcClientConfig config = builder.buildPrototype();

        assertThat(builder.toString(), not(containsString(sensitiveValue)));
        assertThat(config.toString(), not(containsString(sensitiveValue)));
        assertThat(builder.toString(), containsString("****"));
        assertThat(config.toString(), containsString("****"));
    }

    /**
     * Verifies public construction boundaries reject null input with simple
     * diagnostics that contain no application state.
     */
    @Test
    void rejectsNullConstructionInputs() {
        NullPointerException configFailure = assertThrows(
                NullPointerException.class,
                () -> JdbcClient.create((JdbcClientConfig) null));
        NullPointerException consumerFailure = assertThrows(
                NullPointerException.class,
                () -> JdbcClient.create((Consumer<JdbcClientConfig.Builder>) null));

        assertThat(configFailure.getMessage(), is("The JDBC client configuration must not be null."));
        assertThat(consumerFailure.getMessage(), is("The JDBC client builder consumer must not be null."));
    }

    private static void assertSourceConflict(Runnable construction) {
        DataException failure = assertThrows(DataException.class, construction::run);
        assertThat(failure.getMessage(), is("Both connection and DataSource config options are present"));
    }

    /**
     * JDBC driver that records connection attempts without retaining
     * connection configuration.
     */
    private static final class RecordingDriver implements Driver {

        private final String url;
        private int connectionAttempts;

        private RecordingDriver(String url) {
            this.url = url;
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            connectionAttempts++;
            return null;
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
            return Logger.getLogger(RecordingDriver.class.getName());
        }

        private String url() {
            return url;
        }

        private int connectionAttempts() {
            return connectionAttempts;
        }
    }
}
