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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.common.Errors;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcClientHealthCheckTest {
    private static final Qualifier PROVIDER = Qualifier.create(Data.ProviderType.TYPE, Jdbc.PROVIDER);

    /**
     * Keeps policy defaults, configuration, and owner builders consistent without acquiring connections.
     */
    @Test
    void healthPolicyDefaultsAndOwnerBuilderParity() {
        JdbcClientHealthConfig defaults = JdbcClientHealthConfig.create();
        assertThat(defaults.enabled(), is(false));
        assertThat(defaults.query(), is("SELECT 1"));
        assertThat(defaults.queryTimeout(), is(Duration.ofSeconds(2)));
        assertThat(defaults.type(), is(HealthCheckType.READINESS));

        String query = "SELECT 'POLICY_SQL_CANARY'";
        Config config = Config.just(ConfigSources.create(Map.of("name", "orders",
                "health.enabled", "true", "health.query", query, "health.query-timeout", "PT3S",
                "health.type", "liveness")));
        DataSource source = mock(DataSource.class);
        var configured = JdbcClientConfig.builder().config(config).dataSource(source);
        var policy = JdbcClientHealthConfig.builder().enabled(true).query(query).queryTimeout(Duration.ofSeconds(3))
                .type(HealthCheckType.LIVENESS);
        var typed = JdbcClientConfig.builder().name("orders").dataSource(source).health(policy.build());
        var nested = JdbcClientConfig.builder().name("orders").dataSource(source)
                .health(health -> health.enabled(true).query(query).queryTimeout(Duration.ofSeconds(3))
                        .type(HealthCheckType.LIVENESS));
        assertThat(JdbcClientConfig.builder().dataSource(source).buildPrototype().health(), is(defaults));
        for (var builder : List.of(configured, typed, nested)) {
            JdbcClientConfig owner = builder.buildPrototype();
            assertThat(owner.health(), is(policy.build()));
            assertThat(builder.toString(), not(containsString("POLICY_SQL_CANARY")));
            assertThat(owner.toString(), not(containsString("POLICY_SQL_CANARY")));
            assertThat(owner.health().query(), is(query));
        }
        assertThat(policy.toString(), containsString("query=****"));
        assertThat(policy.build().toString(), not(containsString("POLICY_SQL_CANARY")));
        verifyZeroInteractions(source);
    }

    /**
     * Ignores obsolete naming nodes while retaining policy values and confidential rendering.
     */
    @Test
    void obsoletePolicyNamesDoNotChangeDiscovery() {
        DataSource source = mock(DataSource.class);
        for (Map<String, String> obsolete : List.of(Map.<String, String>of(), Map.of("name", "legacy-db"),
                Map.of("name", " "), Map.of("name.child", "legacy-db"), Map.of("name.0", "legacy-db"))) {
            Map<String, String> values = new HashMap<>(obsolete);
            values.putAll(Map.of("enabled", "true", "query", "SELECT 'SQL_CANARY'", "query-timeout", "PT3S",
                    "type", "liveness"));
            JdbcClientHealthConfig policy = JdbcClientHealthConfig.create(Config.just(ConfigSources.create(values)));
            JdbcClientHealthConfig expected = JdbcClientHealthConfig.builder().enabled(true).query("SELECT 'SQL_CANARY'")
                    .queryTimeout(Duration.ofSeconds(3)).type(HealthCheckType.LIVENESS).build();
            assertThat(policy, is(expected));
            assertThat(policy.toString(), not(containsString("legacy-db")));
            assertThat(policy.toString(), not(containsString("SQL_CANARY")));
            Map<String, String> ownerValues = new HashMap<>();
            values.forEach((key, value) -> ownerValues.put("health." + key, value));
            ownerValues.put("name", "logical");
            JdbcClient client = JdbcClient.builder().config(Config.just(ConfigSources.create(ownerValues)))
                    .dataSource(source).build();
            assertThat(client.prototype().health(), is(policy));
            assertThat(client.prototype().toString(), not(containsString("SQL_CANARY")));
            var manager = clientRegistry(() -> List.of(registered(client, "orders")), List.of());
            try {
                List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
                assertThat(checks.size(), is(1));
                var check = (JdbcClientHealthCheck) checks.getFirst();
                assertThat(check.name(), is("orders"));
                assertThat(check.prototype().query(), is(policy.query()));
                assertThat(check.prototype().queryTimeout(), is(policy.queryTimeout()));
                assertThat(check.type(), is(policy.type()));
            } finally {
                manager.shutdown();
            }
            JdbcClientHealthConfig nameOnly = JdbcClientHealthConfig.create(Config.just(ConfigSources.create(obsolete)));
            assertThat(nameOnly, is(JdbcClientHealthConfig.create()));
        }
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
     * Resolves disabled client policies without publishing checks or acquiring connections.
     */
    @Test
    void disabledOrAbsentPoliciesPublishNoChecks() {
        for (Map<String, String> values : List.of(Map.<String, String>of(),
                                                 Map.of("health.enabled", "false"),
                                                 Map.of("health-check.enabled", "true"))) {
            DataSource source = mock(DataSource.class);
            JdbcClient client = JdbcClient.builder().config(Config.just(ConfigSources.create(values)))
                    .dataSource(source).build();
            AtomicInteger activations = new AtomicInteger();
            var manager = clientRegistry(() -> {
                activations.incrementAndGet();
                return List.of(Service.QualifiedInstance.create(client));
            }, List.of());
            try {
                assertThat(manager.registry().all(HealthCheck.class), empty());
                assertThat(activations.get(), is(1));
                verifyZeroInteractions(source);
            } finally {
                manager.shutdown();
            }
        }
        var manager = clientRegistry(List::of, List.of());
        try {
            // An enabled configuration alone is no longer a health definition targeting a missing client.
            GlobalServiceRegistry.registry(manager.registry());
            Services.set(JdbcClientConfig.class, JdbcClientConfig.builder().dataSource(mock(DataSource.class))
                    .health(health -> health.enabled(true)).buildPrototype());
            assertThat(manager.registry().all(HealthCheck.class), empty());
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Uses the selected client's complete policy instead of an earlier configuration or a shadowed client.
     */
    @Test
    void replacementClientOwnsEveryHealthSetting() {
        DataSource source = mock(DataSource.class);
        JdbcClient original = JdbcClient.builder().name("original").dataSource(source)
                .health(health -> health.enabled(true).query("SELECT 2")).build();
        for (boolean enabled : List.of(false, true)) {
            JdbcClient replacement = JdbcClient.builder().name("replacement").dataSource(source)
                    .health(health -> health.enabled(enabled).query("SELECT 3")
                            .queryTimeout(Duration.ofSeconds(4)).type(HealthCheckType.LIVENESS)).build();
            var manager = clientRegistry(() -> List.of(registered(replacement, "orders")),
                    List.of(registered(original, "orders")));
            try {
                GlobalServiceRegistry.registry(manager.registry());
                Services.set(JdbcClientConfig.class, original.prototype());
                List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
                assertThat(checks.size(), is(enabled ? 1 : 0));
                if (enabled) {
                    var check = (JdbcClientHealthCheck) checks.getFirst();
                    assertThat(check.prototype().client(), sameInstance(replacement));
                    assertThat(check.name(), is("orders"));
                    assertThat(check.prototype().query(), is("SELECT 3"));
                    assertThat(check.prototype().queryTimeout(), is(Duration.ofSeconds(4)));
                    assertThat(check.type(), is(HealthCheckType.LIVENESS));
                }
                verifyZeroInteractions(source);
            } finally {
                manager.shutdown();
            }
        }
        JdbcClient disabled = JdbcClient.builder().dataSource(source).build();
        var manager = clientRegistry(() -> List.of(registered(original, "orders")),
                List.of(registered(disabled, "orders")));
        try {
            assertThat(manager.registry().all(HealthCheck.class).size(), is(1));
        } finally {
            manager.shutdown();
        }
        verifyZeroInteractions(source);
    }

    /**
     * Skips an unsupported winner without inspecting it or falling back to a supported client.
     */
    @Test
    void customClientExclusionDoesNotCallItsMethods() {
        JdbcClient custom = mock(JdbcClient.class);
        DataSource source = mock(DataSource.class);
        JdbcClient shadowed = JdbcClient.builder().dataSource(source).health(health -> health.enabled(true)).build();
        var manager = clientRegistry(() -> List.of(registered(custom, "orders")),
                List.of(registered(shadowed, "orders")));
        try {
            assertThat(manager.registry().all(HealthCheck.class), empty());
            assertThrows(IllegalArgumentException.class, () -> JdbcClientHealthCheck.builder().client(custom).build());
            verifyZeroInteractions(custom, source);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Applies the same effective policy to an explicitly bound standalone client without changing its source.
     */
    @Test
    void explicitServiceRegistrationUsesTheReplacementPolicy() {
        DataSource original = mock(DataSource.class);
        DataSource selected = mock(DataSource.class);
        for (boolean enabled : List.of(false, true)) {
            Config root = Config.just(ConfigSources.create(Map.of("data.clients.jdbc.0.name", "orders",
                    "data.clients.jdbc.0.health.enabled", Boolean.toString(!enabled))));
            var manager = registry(root, original);
            try {
                JdbcClient replacement = JdbcClient.builder().name("replacement").dataSource(selected)
                        .health(health -> health.enabled(enabled).query("SELECT 3")).build();
                Services.setQualified(JdbcClient.class, replacement, Qualifier.createNamed("orders"), PROVIDER);
                List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
                assertThat(checks.size(), is(enabled ? 1 : 0));
                if (enabled) {
                    var check = (JdbcClientHealthCheck) checks.getFirst();
                    assertThat(check.name(), is("orders"));
                    assertThat(check.prototype().query(), is("SELECT 3"));
                    assertThat(check.prototype().client(), sameInstance(replacement));
                }
                assertThat(manager.registry().get(JdbcClient.class, Qualifier.createNamed("orders"), PROVIDER),
                           sameInstance(replacement));
            } finally {
                manager.shutdown();
            }
        }
        verifyZeroInteractions(original, selected);
    }

    /**
     * Sanitizes activation failures at registry boundaries while preserving fatal errors.
     */
    @Test
    void registryFailuresAreSanitizedAndFatalErrorsPropagate() {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        Lookup lookup = Lookup.create(JdbcClient.class);
        for (RuntimeException failure : List.of(new ConfigException("SECRET_CONFIG"),
                new ServiceRegistryException("SECRET_REGISTRY"), new IllegalStateException("SECRET_PROVIDER"))) {
            doThrow(failure).when(registry).lookupInstances(lookup);
            DataException actual = assertThrows(DataException.class, () -> new JdbcHealthCheckFactory(registry).services());
            assertThat(actual.getMessage(), not(containsString("SECRET")));
            assertThat(actual.getCause(), is((Throwable) null));
        }
        Error fatal = new OutOfMemoryError("Fatal activation failure.");
        doThrow(fatal).when(registry).lookupInstances(lookup);
        assertThat(assertThrows(OutOfMemoryError.class, () -> new JdbcHealthCheckFactory(registry).services()),
                   sameInstance(fatal));
    }

    /**
     * Sanitizes failures selecting a named client and permits an empty selection without inspecting a fallback.
     */
    @Test
    void clientResolutionFailuresAreSanitizedAndFatalErrorsPropagate() {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        @SuppressWarnings("unchecked")
        ServiceInstance<JdbcClient> candidate = mock(ServiceInstance.class);
        Set<Qualifier> qualifiers = Set.of(Qualifier.createNamed("orders"), PROVIDER);
        when(candidate.qualifiers()).thenReturn(qualifiers);
        when(registry.<JdbcClient>lookupInstances(Lookup.create(JdbcClient.class))).thenReturn(List.of(candidate));
        Lookup lookup = Lookup.builder().addContract(JdbcClient.class).qualifiers(qualifiers).build();
        doThrow(new ServiceRegistryException("SECRET_RESOLUTION")).when(registry).first(lookup);
        var factory = new JdbcHealthCheckFactory(registry);
        DataException failure = assertThrows(DataException.class, factory::services);
        assertThat(failure.getMessage(), not(containsString("SECRET")));
        assertThat(failure.getCause(), is((Throwable) null));
        Error fatal = new OutOfMemoryError("Fatal client resolution failure.");
        doThrow(fatal).when(registry).first(lookup);
        assertThat(assertThrows(OutOfMemoryError.class, factory::services), sameInstance(fatal));
        doReturn(Optional.empty()).when(registry).first(lookup);
        assertThat(factory.services(), empty());
    }

    /**
     * Reports an unused provider activation failure even when another resolved client disables health.
     */
    @Test
    void disabledHealthDoesNotSuppressClientActivationFailures() {
        DataSource source = mock(DataSource.class);
        JdbcClient disabled = JdbcClient.builder().dataSource(source).build();
        var manager = clientRegistry(() -> {
            throw new IllegalStateException("SECRET_PROVIDER_CONFIGURATION");
        }, List.of(registered(disabled, "orders")));
        try {
            DataException failure = assertThrows(DataException.class,
                    () -> new JdbcHealthCheckFactory(manager.registry()).services());
            assertThat(failure.getMessage(), not(containsString("SECRET")));
            assertThat(failure.getCause(), is((Throwable) null));
            verifyZeroInteractions(source);
        } finally {
            manager.shutdown();
        }
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
            GlobalServiceRegistry.registry(second.registry());
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
     * Keeps qualified and unqualified registrations distinct and lets the registry select their preferred clients.
     */
    @Test
    void registryPreferenceAndRegistrationNamesDetermineChecks() {
        DataSource source = mock(DataSource.class);
        JdbcClient preferred = JdbcClient.builder().name("replacement").dataSource(source)
                .health(health -> health.enabled(true).query("SELECT 3")).build();
        JdbcClient shadowed = JdbcClient.builder().name("shadowed").dataSource(source)
                .health(health -> health.enabled(true).query("UPDATE invalid")).build();
        JdbcClient unqualified = JdbcClient.builder().name("orders").dataSource(source)
                .health(health -> health.enabled(true)).build();
        Qualifier tenant = Qualifier.create(Data.ProviderType.TYPE, "tenant");
        var manager = clientRegistry(() -> List.of(registered(preferred, "orders"), registered(preferred, "other"),
                Service.QualifiedInstance.create(preferred, tenant)),
                List.of(Service.QualifiedInstance.create(unqualified), registered(shadowed, "orders")));
        try {
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks.size(), is(4));
            assertThat(checks.stream().map(HealthCheck::name).toList(),
                       is(List.of("orders", "orders", "other", "replacement")));
            assertThat(((JdbcClientHealthCheck) checks.getFirst()).prototype().client(), sameInstance(unqualified));
            for (HealthCheck check : checks.subList(1, checks.size())) {
                assertThat(((JdbcClientHealthCheck) check).prototype().client(), sameInstance(preferred));
                assertThat(((JdbcClientHealthCheck) check).prototype().query(), is("SELECT 3"));
            }
            assertThat(manager.registry().get(JdbcClient.class, Qualifier.createNamed("orders"), PROVIDER),
                       sameInstance(preferred));
            assertThat(new JdbcHealthCheckFactory(manager.registry()).services().stream()
                    .allMatch(check -> check.qualifiers().isEmpty()), is(true));
            verifyZeroInteractions(source);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Preserves independent registration names and policies and rejects invalid enabled SQL without fallback.
     */
    @Test
    void registrationNamesDoNotMergePoliciesAndInvalidSqlDoesNotFallBack() {
        DataSource source = mock(DataSource.class);
        JdbcClient ready = JdbcClient.builder().dataSource(source)
                .health(health -> health.enabled(true).query("SELECT 2")).build();
        JdbcClient live = JdbcClient.builder().dataSource(source)
                .health(health -> health.enabled(true).type(HealthCheckType.LIVENESS)).build();
        var manager = clientRegistry(() -> List.of(registered(ready, "one"), registered(ready, "two"),
                registered(live, "three")), List.of());
        try {
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks.size(), is(3));
            assertThat(checks.stream().map(HealthCheck::name).toList(), is(List.of("one", "two", "three")));
            assertThat(checks.stream().map(HealthCheck::type).toList(),
                       is(List.of(HealthCheckType.READINESS, HealthCheckType.READINESS, HealthCheckType.LIVENESS)));
        } finally {
            manager.shutdown();
        }
        for (JdbcClientHealthConfig invalid : List.of(
                JdbcClientHealthConfig.builder().enabled(true).query("UPDATE invalid").build(),
                JdbcClientHealthConfig.builder().enabled(true).queryTimeout(Duration.ZERO).build())) {
            JdbcClient client = JdbcClient.builder().dataSource(source).health(invalid).build();
            manager = clientRegistry(() -> List.of(registered(client, "orders")), List.of());
            try {
                var factory = new JdbcHealthCheckFactory(manager.registry());
                assertThrows(IllegalArgumentException.class, factory::services);
            } finally {
                manager.shutdown();
            }
        }
        verifyZeroInteractions(source);
    }

    private static Service.QualifiedInstance<JdbcClient> registered(JdbcClient client, String name) {
        return Service.QualifiedInstance.create(client, Qualifier.createNamed(name), PROVIDER);
    }

    private static ServiceRegistryManager clientRegistry(Supplier<List<Service.QualifiedInstance<JdbcClient>>> preferred,
                                                         List<Service.QualifiedInstance<JdbcClient>> fallback) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false).discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(JdbcHealthCheckFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcClientConfigFactory__ServiceDescriptor.INSTANCE)
                .putServiceInstance(JdbcClientHealthCheckTest_PreferredClients__ServiceDescriptor.INSTANCE,
                        new PreferredClients(preferred))
                .putServiceInstance(JdbcClientHealthCheckTest_FallbackClients__ServiceDescriptor.INSTANCE,
                        new FallbackClients(fallback)).build());
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

    /**
     * Supplies preferred clients through the real registry so tests exercise its qualifier and weight rules.
     */
    @Service.Singleton
    @Weight(200)
    static class PreferredClients implements Service.ServicesFactory<JdbcClient> {
        private final Supplier<List<Service.QualifiedInstance<JdbcClient>>> clients;

        @Service.Inject
        PreferredClients(Supplier<List<Service.QualifiedInstance<JdbcClient>>> clients) {
            this.clients = clients;
        }

        @Override
        public List<Service.QualifiedInstance<JdbcClient>> services() {
            return clients.get();
        }
    }

    /**
     * Supplies lower priority candidates that must not produce checks when a matching preferred client exists.
     */
    @Service.Singleton
    @Weight(100)
    static class FallbackClients implements Service.ServicesFactory<JdbcClient> {
        private final List<Service.QualifiedInstance<JdbcClient>> clients;

        @Service.Inject
        FallbackClients(List<Service.QualifiedInstance<JdbcClient>> clients) {
            this.clients = clients;
        }

        @Override
        public List<Service.QualifiedInstance<JdbcClient>> services() {
            return clients;
        }
    }

}
