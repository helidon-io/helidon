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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.sql.DataSource;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
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
     * Keeps policy defaults, configuration, owner builders, and copies consistent without acquiring connections.
     */
    @Test
    void healthPolicyDefaultsAndOwnerBuilderParity() {
        JdbcClientHealthConfig defaults = JdbcClientHealthConfig.create();
        assertThat(defaults.enabled(), is(false));
        assertThat(defaults.query(), is("SELECT 1"));
        assertThat(defaults.queryTimeout(), is(Duration.ofSeconds(2)));
        assertThat(defaults.type(), is(HealthCheckType.READINESS));
        assertThat(defaults.name().isEmpty(), is(true));

        String query = "SELECT 'POLICY_SQL_CANARY'";
        Config config = Config.just(ConfigSources.create(Map.of("name", "orders",
                "health.enabled", "true", "health.query", query, "health.query-timeout", "PT3S",
                "health.name", "orders-db", "health.type", "liveness")));
        DataSource source = mock(DataSource.class);
        var configured = JdbcClientConfig.builder().config(config).dataSource(source);
        var policy = JdbcClientHealthConfig.builder().enabled(true).query(query).queryTimeout(Duration.ofSeconds(3))
                .name("orders-db").type(HealthCheckType.LIVENESS);
        var typed = JdbcClientConfig.builder().name("orders").dataSource(source).health(policy.build());
        var nested = JdbcClientConfig.builder().name("orders").dataSource(source)
                .health(health -> health.enabled(true).query(query).queryTimeout(Duration.ofSeconds(3))
                        .name("orders-db").type(HealthCheckType.LIVENESS));
        assertThat(JdbcClientConfig.builder().dataSource(source).buildPrototype().health(), is(defaults));
        for (var builder : List.of(configured, typed, nested)) {
            JdbcClientConfig owner = builder.buildPrototype();
            assertThat(owner.health(), is(policy.build()));
            assertThat(JdbcClientConfig.builder(owner).buildPrototype(), is(owner));
            assertThat(JdbcClientHealthConfig.builder(owner.health()).build(), is(owner.health()));
            assertThat(builder.toString(), not(containsString("POLICY_SQL_CANARY")));
            assertThat(owner.toString(), not(containsString("POLICY_SQL_CANARY")));
            assertThat(owner.health().query(), is(query));
        }
        assertThat(policy.toString(), containsString("query=****"));
        assertThat(policy.build().toString(), not(containsString("POLICY_SQL_CANARY")));
        assertThrows(NullPointerException.class, () -> JdbcClientHealthConfig.create((Config) null));
        assertThrows(NullPointerException.class, () -> JdbcClientHealthConfig.builder(null));
        assertThrows(NullPointerException.class, () -> policy.from((JdbcClientHealthConfig) null));
        assertThrows(NullPointerException.class,
                     () -> policy.from((JdbcClientHealthConfig.BuilderBase<?, ?>) null));
        assertThrows(NullPointerException.class, () -> policy.query(null));
        assertThrows(NullPointerException.class, () -> policy.queryTimeout(null));
        assertThrows(NullPointerException.class, () -> policy.type(null));
        assertThrows(NullPointerException.class, () -> policy.name((String) null));
        assertThrows(NullPointerException.class, () -> policy.config(null));
        assertThrows(NullPointerException.class, () -> typed.health((JdbcClientHealthConfig) null));
        assertThrows(NullPointerException.class,
                     () -> typed.health((Consumer<JdbcClientHealthConfig.Builder>) null));
        verifyZeroInteractions(source);
    }

    /**
     * Rejects null configuration arguments without changing builder values or acquiring connections.
     */
    @Test
    void generatedConfigurationRejectsNullAndPreservesCopies() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        var builder = JdbcClientHealthCheck.builder().client(client).query("SELECT 2").name("database");
        JdbcClientHealthCheckConfig prototype = builder.buildPrototype();

        assertThrows(NullPointerException.class, () -> JdbcClientHealthCheckConfig.builder(null));
        assertThrows(NullPointerException.class, () -> JdbcClientHealthCheckConfig.create((Config) null));
        assertThrows(NullPointerException.class, () -> builder.from((JdbcClientHealthCheckConfig) null));
        assertThrows(NullPointerException.class, () -> builder.from((JdbcClientHealthCheckConfig.BuilderBase<?, ?>) null));
        assertThrows(NullPointerException.class, () -> builder.config(null));
        assertThrows(NullPointerException.class, () -> builder.client((JdbcClient) null));
        assertThrows(NullPointerException.class, () -> builder.client((JdbcClientConfig) null));
        assertThrows(NullPointerException.class, () -> builder.client((Consumer<JdbcClientConfig.Builder>) null));
        assertThrows(NullPointerException.class, () -> builder.query(null));
        assertThrows(NullPointerException.class, () -> builder.queryTimeout(null));
        assertThrows(NullPointerException.class, () -> builder.type(null));
        assertThrows(NullPointerException.class, () -> builder.name((String) null));

        assertThat(builder.buildPrototype(), is(prototype));
        assertThat(JdbcClientHealthCheckConfig.builder(prototype).buildPrototype(), is(prototype));
        assertThat(JdbcClientHealthCheckConfig.builder().from(builder).buildPrototype(), is(prototype));
        assertThat(prototype.client(), sameInstance(client));
        verifyZeroInteractions(source);
    }

    /**
     * Verifies configuration parity and construction defaults without acquiring resources.
     */
    @Test
    void defaultsAndConfigurationMatchBuildersWithoutAcquisition() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().name("orders").dataSource(source).build();
        JdbcClientHealthCheck defaults = JdbcClientHealthCheck.builder().client(client).build();
        assertThat(defaults.name(), is(client.prototype().name()));
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
        assertThrows(NullPointerException.class,
                     () -> JdbcClientHealthCheck.create((Consumer<JdbcClientHealthCheckConfig.Builder>) null));
        assertThrows(NullPointerException.class, () -> JdbcClientHealthCheck.builder().client((JdbcClient) null));
        assertThrows(Errors.ErrorMessagesException.class, () -> JdbcClientHealthCheck.builder().build());
        verifyZeroInteractions(source);
    }

    /**
     * Redacts SQL in builder and prototype text while retaining the query and acquiring no resources.
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
     * Returns only DOWN for expected JDBC failures and propagates fatal errors.
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
                                                 Map.of("data.clients.jdbc.0.health.enabled", "false"),
                                                 Map.of("data.clients.jdbc.0.health-check.enabled", "true"))) {
            JdbcHealthCheckFactory factory = new JdbcHealthCheckFactory(
                    () -> configurations(Config.just(ConfigSources.create(values))),
                    () -> { throw new AssertionError("Disabled discovery must not resolve clients."); });
            assertThat(factory.services(), empty());
        }
        Config invalid = mock(Config.class);
        when(invalid.get("data.clients.jdbc")).thenThrow(new ConfigException("CONFIG_CANARY"));
        DataException failure = assertThrows(DataException.class,
                () -> new JdbcHealthCheckFactory(() -> configurations(invalid), () -> {
                    throw new AssertionError("Invalid discovery must not resolve its target.");
                }).services());
        assertThat(failure.getMessage(), not(containsString("CONFIG_CANARY")));
        assertThat(failure.getCause(), is((Throwable) null));
    }

    /**
     * Binds the effective client while retaining the configured health policy.
     */
    @Test
    void missingClientFailsAndEffectiveClientReplacesOnlyConnectionPolicy() {
        Config root = Config.just(ConfigSources.create(Map.of("data.clients.jdbc.0.name", "orders",
                "data.clients.jdbc.0.health.enabled", "true", "data.clients.jdbc.0.health.query", "SELECT 2",
                "data.clients.jdbc.0.health.name", "orders-db")));
        assertThrows(DataException.class, () -> new JdbcHealthCheckFactory(() -> configurations(root), List::of).services());
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        @SuppressWarnings("unchecked")
        ServiceInstance<JdbcClient> instance = mock(ServiceInstance.class);
        when(instance.qualifiers()).thenReturn(Set.of(Qualifier.createNamed("orders")));
        when(instance.get()).thenReturn(client);
        var services = new JdbcHealthCheckFactory(() -> configurations(root), () -> List.of(instance)).services();
        assertThat(services.size(), is(1));
        assertThat(services.getFirst().qualifiers().isEmpty(), is(true));
        JdbcClientHealthCheck check = (JdbcClientHealthCheck) services.getFirst().get();
        assertThat(check.name(), is("orders-db"));
        assertThat(check.prototype().client(), sameInstance(client));
        assertThat(check.prototype().query(), is("SELECT 2"));
        verifyZeroInteractions(source);
        Config invalid = Config.just(ConfigSources.create(Map.of("data.clients.jdbc.0.name", "orders",
                "data.clients.jdbc.0.health.enabled", "true",
                "data.clients.jdbc.0.health.query-timeout", "CONFIG_CANARY")));
        DataException failure = assertThrows(DataException.class,
                () -> new JdbcHealthCheckFactory(() -> configurations(invalid), () -> List.of(instance)).services());
        assertThat(failure.getMessage(), not(containsString("CONFIG_CANARY")));
        assertThat(failure.getCause(), is((Throwable) null));
        verifyZeroInteractions(source);
    }

    /**
     * Verifies that each registry resolves its own source even when source names match.
     */
    @Test
    void registryFactoriesResolveOnlyTheirOwnEffectiveSources() throws Exception {
        Config root = Config.just(ConfigSources.create(Map.of("data.clients.jdbc.0.name", "orders",
                "data.clients.jdbc.0.health.enabled", "true", "data.clients.jdbc.0.health.name", "orders-db")));
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
            GlobalServiceRegistry.registry(first.registry());
            Services.setNamed(JdbcClient.class, JdbcClient.builder().dataSource(broken).build(), "orders");
            assertThat(a.call().status(), is(HealthCheckResponse.Status.UP));
        } finally {
            try {
                first.shutdown();
            } finally {
                second.shutdown();
            }
        }
    }

    /**
     * Uses the preferred definition policy while retaining duplicate health paths and the effective client source.
     */
    @Test
    void preferredDefinitionsRetainNamesAndPermitCollidingPaths() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().name("replacement").dataSource(source)
                .health(health -> health.enabled(true).name("replacement-db").query("SELECT 3")).build();
        JdbcClientConfig preferred = JdbcClientConfig.builder().name("orders").dataSource(mock(DataSource.class))
                .health(health -> health.enabled(true).query("SELECT 2")).buildPrototype();
        JdbcClientConfig shadowed = JdbcClientConfig.builder(preferred)
                .health(health -> health.enabled(true).query("UPDATE invalid")).buildPrototype();
        JdbcClientConfig other = JdbcClientConfig.builder().name("other").dataSource(source)
                .health(health -> health.enabled(true).name("orders")).buildPrototype();
        @SuppressWarnings("unchecked")
        ServiceInstance<JdbcClient> instance = mock(ServiceInstance.class);
        when(instance.qualifiers()).thenReturn(Set.of(Qualifier.createNamed("orders"), Qualifier.createNamed("other")));
        when(instance.get()).thenReturn(client);
        var checks = new JdbcHealthCheckFactory(() -> List.of(preferred, shadowed, other), () -> List.of(instance)).services();
        assertThat(checks.size(), is(2));
        assertThat(checks.get(0).get().name(), is("orders"));
        assertThat(checks.get(1).get().name(), is("orders"));
        assertThat(((JdbcClientHealthCheck) checks.get(0).get()).prototype().query(), is("SELECT 2"));
        assertThat(((JdbcClientHealthCheck) checks.get(1).get()).prototype().query(), is("SELECT 1"));
        assertThat(((JdbcClientHealthCheck) checks.get(0).get()).prototype().client(), sameInstance(client));
        JdbcClientConfig disabled = JdbcClientConfig.builder(preferred).health(JdbcClientHealthConfig.create()).buildPrototype();
        assertThat(new JdbcHealthCheckFactory(() -> List.of(disabled, shadowed), () -> {
            throw new AssertionError("The disabled preferred definition must not resolve a client.");
        }).services().isEmpty(), is(true));
        for (JdbcClientHealthConfig invalid : List.of(
                JdbcClientHealthConfig.builder().enabled(true).query("UPDATE invalid").build(),
                JdbcClientHealthConfig.builder().enabled(true).name(" ").build(),
                JdbcClientHealthConfig.builder().enabled(true).queryTimeout(Duration.ZERO).build())) {
            JdbcClientConfig definition = JdbcClientConfig.builder(preferred).health(invalid).buildPrototype();
            assertThrows(IllegalArgumentException.class,
                         () -> new JdbcHealthCheckFactory(() -> List.of(definition), () -> List.of(instance)).services());
        }
        verifyZeroInteractions(source);
    }

    private static List<JdbcClientConfig> configurations(Config root) {
        return root.get("data.clients.jdbc").asNodeList().orElse(List.of()).stream()
                .map(definition -> JdbcClientConfig.builder().config(definition)
                        .dataSource(mock(DataSource.class)).buildPrototype())
                .toList();
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
        Services.set(JdbcClientConfig.class, JdbcClientConfig.builder().name("orders").dataSource(source)
                .health(configurations(root).getFirst().health()).buildPrototype());
        return manager;
    }

}
