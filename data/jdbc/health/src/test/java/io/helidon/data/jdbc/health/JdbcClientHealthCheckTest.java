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
package io.helidon.data.jdbc.health;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientConfig;
import io.helidon.data.jdbc.JdbcClientConfigFactory__ServiceDescriptor;
import io.helidon.data.jdbc.JdbcClientFactory__ServiceDescriptor;
import io.helidon.data.jdbc.JdbcTransactionConnectionManager__ServiceDescriptor;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcClientHealthCheckTest {
    /**
     * Verifies configuration parity and construction defaults without acquiring resources.
     */
    @Test
    void defaultsAndConfigurationMatchBuildersWithoutAcquisition() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        JdbcClientHealthCheck defaults = JdbcClientHealthCheck.builder().client(client).build();
        assertThat(defaults.name(), is("JdbcClientHealthCheck"));
        assertThat(defaults.path(), is(defaults.name()));
        assertThat(defaults.type(), is(HealthCheckType.READINESS));
        assertThat(defaults.prototype().query(), is("SELECT 1"));
        assertThat(defaults.prototype().queryTimeout(), is(Duration.ofSeconds(2)));
        Config config = Config.just(ConfigSources.create(Map.of("query", "SELECT 2", "name", "database",
                                                               "query-timeout", "PT3S", "type", "liveness")));
        JdbcClientHealthCheck configured = JdbcClientHealthCheck.builder().config(config).client(client).build();
        JdbcClientHealthCheck built = JdbcClientHealthCheck.builder().client(client).query("SELECT 2").name("database")
                .queryTimeout(Duration.ofSeconds(3)).type(HealthCheckType.LIVENESS).build();
        assertThat(configured.prototype(), is(built.prototype()));
        assertThat(configured.prototype().client(), sameInstance(client));
        assertThrows(IllegalArgumentException.class, () -> JdbcClientHealthCheck.builder().client(client).name(" ").build());
        assertThrows(IllegalArgumentException.class,
                     () -> JdbcClientHealthCheck.builder().client(client).query("UPDATE t SET x=1").build());
        assertThrows(NullPointerException.class, () -> JdbcClientHealthCheck.create((JdbcClientHealthCheckConfig) null));
        verifyZeroInteractions(source);
    }

    /**
     * Redacts trusted SQL in configuration rendering while preserving the query without acquiring resources.
     */
    @Test
    void queryIsConfidentialInBuilderAndPrototypeRendering() {
        String query = "SELECT 'HEALTH_SQL_LITERAL_CANARY'";
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        Config config = Config.just(ConfigSources.create(Map.of("query", query)));
        for (var builder : List.of(JdbcClientHealthCheck.builder().client(client).query(query),
                                   JdbcClientHealthCheck.builder().client(client).config(config))) {
            assertThat(builder.toString(), containsString("query=****"));
            assertThat(builder.toString(), not(containsString("HEALTH_SQL_LITERAL_CANARY")));
            JdbcClientHealthCheck check = builder.build();
            assertThat(check.prototype().toString(), containsString("query=****"));
            assertThat(check.prototype().toString(), not(containsString("HEALTH_SQL_LITERAL_CANARY")));
            assertThat(check.prototype().query(), is(query));
        }
        verifyZeroInteractions(source);
    }

    /**
     * Contains expected JDBC diagnostics and preserves fatal errors.
     */
    @Test
    void responsesContainOnlyStatusAndFatalErrorsPropagate() throws Exception {
        DataSource source = mock(DataSource.class);
        JdbcClientHealthCheck check = JdbcClientHealthCheck.builder()
                .client(JdbcClient.builder().dataSource(source).build()).build();
        when(source.getConnection()).thenThrow(new SQLException("Secret password and SQL"));
        assertThat(check.call().status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(check.call().details().isEmpty(), is(true));
        Error fatal = new OutOfMemoryError("fatal");
        doThrow(fatal).when(source).getConnection();
        assertThat(assertThrows(OutOfMemoryError.class, check::call), sameInstance(fatal));
    }

    /**
     * Verifies disabled discovery without activating the client supplier.
     */
    @Test
    void disabledOrAbsentDefinitionsDoNotResolveClients() {
        for (Map<String, String> values : List.of(Map.<String, String>of(),
                                                 Map.of("data.clients.jdbc.0.health-check.enabled", "false"))) {
            JdbcHealthCheckFactory factory = new JdbcHealthCheckFactory(() -> Config.just(ConfigSources.create(values)),
                    () -> { throw new AssertionError("Disabled discovery must not resolve clients."); });
            assertThat(factory.services(), empty());
        }
    }

    /**
     * Binds the effective client while retaining the configured health policy.
     */
    @Test
    void missingClientFailsAndEffectiveClientReplacesOnlyConnectionPolicy() {
        Config root = Config.just(ConfigSources.create(Map.of("data.clients.jdbc.0.name", "orders",
                "data.clients.jdbc.0.health-check.enabled", "true", "data.clients.jdbc.0.health-check.query", "SELECT 2",
                "data.clients.jdbc.0.health-check.name", "orders-db")));
        assertThrows(DataException.class, () -> new JdbcHealthCheckFactory(() -> root, List::of).services());
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        @SuppressWarnings("unchecked")
        ServiceInstance<JdbcClient> instance = mock(ServiceInstance.class);
        when(instance.qualifiers()).thenReturn(Set.of(Qualifier.createNamed("orders")));
        when(instance.get()).thenReturn(client);
        JdbcClientHealthCheck check = (JdbcClientHealthCheck) new JdbcHealthCheckFactory(() -> root, () -> List.of(instance))
                .services().getFirst().get();
        assertThat(check.name(), is("orders-db"));
        assertThat(check.prototype().client(), sameInstance(client));
        assertThat(check.prototype().query(), is("SELECT 2"));
        verifyZeroInteractions(source);
    }

    /**
     * Uses two live registries to prove that discovery resolves only the owning registry source.
     */
    @Test
    void registryFactoriesResolveOnlyTheirOwnEffectiveSources() throws Exception {
        Config root = Config.just(ConfigSources.create(Map.of("data.clients.jdbc.0.name", "orders",
                "data.clients.jdbc.0.health-check.enabled", "true", "data.clients.jdbc.0.health-check.name", "orders-db")));
        DataSource healthy = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(healthy.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getUpdateCount()).thenReturn(-1);
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("Secret"));
        var first = registry(root, healthy);
        var second = registry(root, broken);
        try {
            HealthCheck a = first.registry().all(HealthCheck.class).getFirst();
            HealthCheck b = second.registry().all(HealthCheck.class).getFirst();
            verifyZeroInteractions(healthy, broken);
            assertThat(a.call().status(), is(HealthCheckResponse.Status.UP));
            assertThat(b.call().status(), is(HealthCheckResponse.Status.DOWN));
            assertThat(a.call().status(), is(HealthCheckResponse.Status.UP));
        } finally {
            try {
                first.shutdown();
            } finally {
                second.shutdown();
            }
        }
    }

    private static ServiceRegistryManager registry(Config root, DataSource source) {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(JdbcHealthCheckFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcClientConfigFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcClientFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcTransactionConnectionManager__ServiceDescriptor.INSTANCE)
                .build());
        GlobalServiceRegistry.registry(manager.registry());
        Services.set(Config.class, root);
        Services.set(JdbcClientConfig.class, JdbcClientConfig.builder().name("orders").dataSource(source).buildPrototype());
        return manager;
    }

}
