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
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcClientFactoryTest {

    private static final Qualifier PROVIDER_QUALIFIER = Qualifier.builder()
            .typeName(Data.ProviderType.TYPE)
            .value(Jdbc.PROVIDER)
            .build();

    /**
     * Verifies default and named clients sharing one supplied data source are
     * published once with both required qualifiers and no JDBC access.
     */
    @Test
    void publishesQualifiedClientsSharingADataSource() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientConfig defaultConfig = JdbcClientConfig.builder()
                .dataSource(dataSource)
                .buildPrototype();
        JdbcClientConfig inventoryConfig = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource(dataSource)
                .buildPrototype();
        JdbcClientFactory factory = factory(List.of(defaultConfig, inventoryConfig), List::of);

        List<Service.QualifiedInstance<JdbcClient>> clients = factory.services();

        assertThat(clients.size(), is(2));
        assertQualifiedClient(clients.get(0), Service.Named.DEFAULT_NAME, defaultConfig);
        assertQualifiedClient(clients.get(1), "inventory", inventoryConfig);
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies a named SQL data source is activated only after the complete
     * client configuration list has passed validation.
     */
    @Test
    void resolvesNamedDataSourceAfterCompleteValidation() {
        DataSource dataSource = mock(DataSource.class);
        ServiceInstance<DataSource> instance = serviceInstance("inventory-source", dataSource);
        JdbcClientConfig config = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource("inventory-source")
                .buildPrototype();

        Service.QualifiedInstance<JdbcClient> client = factory(List.of(config), () -> List.of(instance))
                .services()
                .getFirst();

        assertQualifiedClient(client, "inventory", config);
        verify(instance).get();
        verifyZeroInteractions(dataSource);
    }

    /**
     * Verifies direct connection definitions resolve their driver without
     * opening a connection while the factory publishes the client.
     *
     * @throws SQLException when the test driver cannot be registered
     */
    @Test
    void publishesDirectConnectionClientWithoutOpeningAConnection() throws SQLException {
        RecordingDriver driver = new RecordingDriver("jdbc:test:client-factory");
        DriverManager.registerDriver(driver);
        try {
            JdbcClientConfig config = JdbcClientConfig.builder()
                    .name("direct")
                    .connection(connection -> connection
                            .url(driver.url())
                            .jdbcDriverClassName(driver.getClass().getName()))
                    .buildPrototype();

            Service.QualifiedInstance<JdbcClient> client = factory(List.of(config), List::of)
                    .services()
                    .getFirst();

            assertQualifiedClient(client, "direct", config);
            assertThat(driver.connectionAttempts(), is(0));
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Verifies a missing named data source reports the logical client and data
     * source names without activating unrelated services.
     */
    @Test
    void reportsMissingNamedDataSource() {
        ServiceInstance<DataSource> unrelated = serviceInstance("other-source", mock(DataSource.class));
        JdbcClientConfig config = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource("inventory-source")
                .buildPrototype();

        DataException failure = assertThrows(
                DataException.class,
                () -> factory(List.of(config), () -> List.of(unrelated)).services());

        assertThat(failure.getMessage(),
                   is("JDBC client 'inventory' could not resolve SQL data source 'inventory-source'."));
        verify(unrelated, never()).get();
    }

    /**
     * Verifies duplicate names fail before data source discovery or client
     * construction can cause an external side effect.
     */
    @Test
    void rejectsDuplicateNamesBeforeSourceResolution() {
        JdbcClientConfig first = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource("first-source")
                .buildPrototype();
        JdbcClientConfig second = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource("second-source")
                .buildPrototype();
        JdbcClientFactory factory = factory(
                List.of(first, second),
                () -> {
                    throw new AssertionError("Data source discovery occurred before duplicate validation");
                });

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("More than one JDBC client configuration uses the name 'inventory'."));
    }

    /**
     * Verifies every connection definition is resolved before a named data
     * source is activated.
     */
    @Test
    void validatesAllConnectionDefinitionsBeforeSourceActivation() {
        ServiceInstance<DataSource> namedSource = serviceInstance("inventory-source", mock(DataSource.class));
        JdbcClientConfig named = JdbcClientConfig.builder()
                .name("inventory")
                .dataSource("inventory-source")
                .buildPrototype();
        JdbcClientConfig invalidDirect = JdbcClientConfig.builder()
                .name("direct")
                .connection(connection -> connection
                        .url("jdbc:missing:client-factory")
                        .jdbcDriverClassName("example.MissingDriver"))
                .buildPrototype();

        assertThrows(DataException.class,
                     () -> factory(List.of(named, invalidDirect), () -> List.of(namedSource)).services());

        verify(namedSource, never()).get();
    }

    private static JdbcClientFactory factory(List<JdbcClientConfig> configurations,
                                             Supplier<List<ServiceInstance<DataSource>>> dataSources) {
        return new JdbcClientFactory(() -> configurations,
                                     dataSources,
                                     new JdbcTransactionConnectionManager());
    }

    @SuppressWarnings("unchecked")
    private static ServiceInstance<DataSource> serviceInstance(String name, DataSource dataSource) {
        ServiceInstance<DataSource> instance = mock(ServiceInstance.class);
        when(instance.qualifiers()).thenReturn(Set.of(Qualifier.createNamed(name)));
        when(instance.get()).thenReturn(dataSource);
        return instance;
    }

    private static void assertQualifiedClient(Service.QualifiedInstance<JdbcClient> qualified,
                                              String name,
                                              JdbcClientConfig config) {
        assertThat(qualified.qualifiers(), is(Set.of(Qualifier.createNamed(name), PROVIDER_QUALIFIER)));
        assertThat(qualified.get().prototype(), sameInstance(config));
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
