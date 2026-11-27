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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.Data;
import io.helidon.data.sql.datasource.DataSourceHealthCheck;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.ObserveFeatureProvider;
import io.helidon.webserver.observe.health.HealthObserveProvider__ServiceDescriptor;
import io.helidon.webserver.observe.health.HealthObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcHealthRoutesTest {
    private final AtomicInteger acquisitionCount = new AtomicInteger();
    private JdbcClient client;
    private DataSource source;

    @BeforeEach
    void setUp() throws Exception {
        source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(_ -> {
            acquisitionCount.incrementAndGet();
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet rows = mock(ResultSet.class);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                if (((String) invocation.getArguments()[0]).contains("MISSING")) {
                    throw new SQLException("Secret driver message.");
                }
                return statement;
            });
            when(statement.execute()).thenReturn(true);
            when(statement.getResultSet()).thenReturn(rows);
            when(statement.getUpdateCount()).thenReturn(-1);
            return connection;
        });
        client = JdbcClient.builder().dataSource(source).build();
    }

    /**
     * Verifies existing route membership and status mappings through fresh deterministic JDBC probes.
     */
    @Test
    void namedRoutesRunTheSameFreshProbeAndPreserveMembership() throws Exception {
        verifyRoutes(false);
        verifyRoutes(true);
    }

    /**
     * Selects the first check in each membership and includes every colliding source and client check in aggregates.
     */
    @Test
    void duplicateDefaultPathsPreserveObserverSelection() {
        for (boolean datasource : new boolean[] {false, true}) {
            acquisitionCount.set(0);
            HealthCheck first = datasource
                    ? DataSourceHealthCheck.builder().dataSource(source).build()
                    : JdbcClientHealthCheck.builder().client(client).build();
            HealthCheck second = datasource
                    ? DataSourceHealthCheck.builder().dataSource(source).query("SELECT * FROM MISSING").build()
                    : JdbcClientHealthCheck.builder().client(client).query("SELECT * FROM MISSING").build();
            HealthCheck live = datasource
                    ? JdbcClientHealthCheck.builder().client(client).name(first.name()).type(HealthCheckType.LIVENESS).build()
                    : DataSourceHealthCheck.builder().dataSource(source).name(first.name())
                            .type(HealthCheckType.LIVENESS).build();
            assertThat(first.name(), is(second.name()));
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.just(HealthObserver.builder().useSystemServices(false)
                            .addCheck(live).addCheck(first).addCheck(second).build())).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/" + first.path(), 204);
                assertStatus(http, "/observe/health/check/" + first.path(), 204);
                assertStatus(http, "/observe/health/live/" + first.path(), 204);
                assertThat(acquisitionCount.get(), is(3));
                assertStatus(http, "/observe/health/ready", 503);
                assertThat(acquisitionCount.get(), is(5));
                assertStatus(http, "/observe/health/", 503);
                assertThat(acquisitionCount.get(), is(8));
            } finally {
                server.stop();
            }
        }
    }

    /**
     * Publishes only the client registration route when obsolete discovery naming is supplied.
     */
    @Test
    void obsoletePolicyNameCreatesNoAliasRoute() {
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false).discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(JdbcHealthCheckFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcClientFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcClientConfigFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcTransactionConnectionManager__ServiceDescriptor.INSTANCE).build());
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.empty());
            Services.set(JdbcClientConfig.class, JdbcClientConfig.builder().name("orders").dataSource(source)
                    .config(Config.just(ConfigSources.create(Map.of("health.enabled", "true",
                            "health.name", "legacy-db")))).buildPrototype());
            var checks = manager.registry().all(HealthCheck.class);
            assertThat(checks.size(), is(1));
            assertThat(checks.getFirst().name(), is("orders"));
            assertThat(acquisitionCount.get(), is(0));
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.just(HealthObserver.builder().useSystemServices(false)
                            .addCheck(checks.getFirst()).build())).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/orders", 204);
                assertStatus(http, "/observe/health/check/orders", 204);
                assertStatus(http, "/observe/health/check/legacy-db", 404);
                assertStatus(http, "/observe/health/live/orders", 404);
                assertStatus(http, "/observe/health/ready", 204);
                assertThat(acquisitionCount.get(), is(3));
            } finally {
                server.stop();
            }
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Selects explicit checks over two unregistered sources independently without transferring ownership.
     */
    @Test
    void unregisteredSourcesHaveIndependentNamedRoutes() throws Exception {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("Secret provider failure."));
        var orders = DataSourceHealthCheck.builder().dataSource(source).name("orders-db").build();
        var billing = DataSourceHealthCheck.builder().dataSource(broken).name("billing-db").build();
        assertThat(orders.prototype().dataSource(), is(source));
        assertThat(billing.prototype().dataSource(), is(broken));
        assertThat(acquisitionCount.get(), is(0));
        WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(HealthObserver.builder().useSystemServices(false)
                        .addCheck(orders).addCheck(billing).build())).build().start();
        try {
            Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
            assertStatus(http, "/observe/health/ready/orders-db", 204);
            assertStatus(http, "/observe/health/check/orders-db", 204);
            assertStatus(http, "/observe/health/ready/billing-db", 503);
            assertStatus(http, "/observe/health/check/billing-db", 503);
            assertStatus(http, "/observe/health/ready", 503);
            assertThat(acquisitionCount.get(), is(3));
        } finally {
            server.stop();
        }
    }

    /**
     * Runs the selected registration once per request through both observer construction paths.
     */
    @Test
    void overlappingQualifiersDoNotDuplicateAggregateProbes() {
        JdbcClient preferred = JdbcClient.builder().name("preferred").dataSource(source)
                .health(health -> health.enabled(true)).build();
        JdbcClient shadowed = JdbcClient.builder().name("shadowed").dataSource(source)
                .health(health -> health.enabled(true).query("UPDATE invalid")).build();
        var named = Qualifier.createNamed("orders");
        var provider = Qualifier.create(Data.ProviderType.TYPE, Jdbc.PROVIDER);
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false).discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(JdbcHealthCheckFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(HealthObserveProvider__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(JdbcClientConfigFactory__ServiceDescriptor.INSTANCE)
                .putServiceInstance(JdbcClientHealthCheckTest_PreferredClients__ServiceDescriptor.INSTANCE,
                        new JdbcClientHealthCheckTest.PreferredClients(
                                () -> List.of(Service.QualifiedInstance.create(preferred, named, provider))))
                .putServiceInstance(JdbcClientHealthCheckTest_FallbackClients__ServiceDescriptor.INSTANCE,
                        new JdbcClientHealthCheckTest.FallbackClients(
                                List.of(Service.QualifiedInstance.create(shadowed, named)))).build());
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.empty());
            assertThat(manager.registry().all(HealthCheck.class).size(), is(1));
            assertThat(acquisitionCount.get(), is(0));
            var configured = new ObserveFeatureProvider().create(Config.empty(), "observe", manager.registry());
            var explicit = ObserveFeature.just(HealthObserver.builder().useSystemServices(true).build());
            for (ObserveFeature feature : List.of(configured, explicit)) {
                int before = acquisitionCount.get();
                WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                        .addFeature(feature).build().start();
                try {
                    Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                    assertStatus(http, "/observe/health/ready/orders", 204);
                    assertStatus(http, "/observe/health/check/orders", 204);
                    assertStatus(http, "/observe/health/ready", 204);
                    assertStatus(http, "/observe/health/", 204);
                    assertStatus(http, "/observe/health/live/orders", 404);
                    assertStatus(http, "/observe/health/check", 404);
                    assertThat(acquisitionCount.get(), is(before + 4));
                } finally {
                    server.stop();
                }
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void observerStartupIsolatesUnavailableJdbcClients() {
        JdbcClient supported = JdbcClient.builder().dataSource(source)
                .health(health -> health.enabled(true)).build();
        JdbcClient shadowed = JdbcClient.builder().dataSource(source)
                .health(health -> health.enabled(true)).build();
        for (boolean configured : List.of(true, false)) {
            String observerPath = configured ? "Configured observer" : "Explicit observer";
            AtomicInteger failedProviderAttempts = new AtomicInteger();
            var providerFailure = new IllegalStateException("The JDBC provider failed to start.");
            var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .discoverServices(false).discoverServicesFromServiceLoader(false)
                    .addServiceDescriptor(JdbcHealthCheckFactory__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(HealthObserveProvider__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(JdbcHealthRoutesTest_RequestClients__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(JdbcHealthRoutesTest_LivenessCheck__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(JdbcClientConfigFactory__ServiceDescriptor.INSTANCE)
                    .putServiceInstance(JdbcClientHealthCheckTest_PreferredClients__ServiceDescriptor.INSTANCE,
                            new JdbcClientHealthCheckTest.PreferredClients(() -> {
                                failedProviderAttempts.incrementAndGet();
                                throw providerFailure;
                            }))
                    .putServiceInstance(JdbcClientHealthCheckTest_FallbackClients__ServiceDescriptor.INSTANCE,
                            new JdbcClientHealthCheckTest.FallbackClients(List.of(
                                    Service.QualifiedInstance.create(shadowed, Qualifier.createNamed("orders")))))
                    .build());
            GlobalServiceRegistry.registry(manager.registry());
            try {
                Services.set(Config.class, Config.empty());
                Services.addNamed(JdbcClient.class, 250, supported, "billing");
                assertThat(observerPath, manager.registry().allServices(JdbcClient.class).stream()
                                   .map(ServiceInfo::scope).toList(), hasItem(Service.PerRequest.TYPE));
                int before = acquisitionCount.get();
                ObserveFeature feature = configured
                        ? new ObserveFeatureProvider().create(Config.empty(), "observe", manager.registry())
                        : ObserveFeature.just(HealthObserver.builder().useSystemServices(true).build());
                assertThat(observerPath, failedProviderAttempts.get(), is(1));
                assertThat(observerPath, acquisitionCount.get(), is(before));
                Lookup eligibleClients = Lookup.builder().addContract(JdbcClient.class)
                        .addScope(Service.Singleton.TYPE).addScope(Service.PerLookup.TYPE).build();
                Lookup billing = Lookup.builder(eligibleClients).addQualifier(Qualifier.createNamed("billing")).build();
                Lookup orders = Lookup.builder(eligibleClients).addQualifier(Qualifier.createNamed("orders")).build();
                assertThat(observerPath, manager.registry().get(billing),
                           sameInstance(supported));
                assertThat(observerPath, assertThrows(IllegalStateException.class,
                        () -> manager.registry().get(orders), observerPath),
                           sameInstance(providerFailure));
                WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                        .addFeature(feature).build().start();
                try {
                    Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                    // Billing still contributes to readiness, and process liveness remains available.
                    var expectedStatuses = Map.of("/observe/health/ready/orders", 404,
                                                  "/observe/health/check/orders", 404,
                                                  "/observe/health/ready/billing", 204,
                                                  "/observe/health/ready", 204,
                                                  "/observe/health/live/process", 204,
                                                  "/observe/health/live", 204,
                                                  "/observe/health/", 204);
                    for (var route : expectedStatuses.entrySet()) {
                        try (Http1ClientResponse response = http.get(route.getKey()).request()) {
                            assertThat(observerPath + " " + route.getKey(), response.status().code(), is(route.getValue()));
                        }
                    }
                    assertThat(observerPath, acquisitionCount.get(), is(before + 3));
                } finally {
                    server.stop();
                }
            } finally {
                manager.shutdown();
            }
        }
    }

    @Test
    void multipleNamedQualifiersProduceOneDeterministicRoute() {
        JdbcClient supported = JdbcClient.builder().name("logical-name").dataSource(source)
                .health(health -> health.enabled(true)).build();
        var primary = Qualifier.createNamed("alpha");
        var alternate = Qualifier.createNamed("zulu");
        for (List<Qualifier> qualifiers : List.of(List.of(primary, alternate), List.of(alternate, primary))) {
            var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .discoverServices(false).discoverServicesFromServiceLoader(false)
                    .addServiceDescriptor(JdbcHealthCheckFactory__ServiceDescriptor.INSTANCE)
                    .putServiceInstance(JdbcClientHealthCheckTest_PreferredClients__ServiceDescriptor.INSTANCE,
                            new JdbcClientHealthCheckTest.PreferredClients(() -> List.of(
                                    Service.QualifiedInstance.create(supported, qualifiers.toArray(Qualifier[]::new)))))
                    .build());
            try {
                List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
                assertThat("Health names for " + qualifiers, checks.stream().map(HealthCheck::name).toList(),
                           contains("alpha"));
                WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                        .addFeature(ObserveFeature.just(HealthObserver.create(checks.getFirst()))).build().start();
                try {
                    Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                    assertStatus(http, "/observe/health/ready/alpha", 204);
                    assertStatus(http, "/observe/health/check/alpha", 204);
                    assertStatus(http, "/observe/health/check/zulu", 404);
                    assertStatus(http, "/observe/health/check/logical-name", 404);
                } finally {
                    server.stop();
                }
            } finally {
                manager.shutdown();
            }
        }
    }

    @Test
    void namedRoutesRequireOneDecodedPathSegment() {
        HealthCheck regular = JdbcClientHealthCheck.builder().client(client).name("orders").build();
        HealthCheck sourceWithSlash = DataSourceHealthCheck.builder().dataSource(source).name("orders/primary").build();
        HealthCheck clientWithSlash = JdbcClientHealthCheck.builder().client(client).name("orders/replica").build();
        assertThat("Direct data source check", sourceWithSlash.call().status(), is(HealthCheckResponse.Status.UP));
        assertThat("Direct client check", clientWithSlash.call().status(), is(HealthCheckResponse.Status.UP));
        var observer = HealthObserver.builder().details(true).useSystemServices(false)
                .addCheck(regular).addCheck(sourceWithSlash).addCheck(clientWithSlash).build();
        WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(observer)).build().start();
        try {
            Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
            for (String membership : List.of("ready", "check")) {
                String prefix = "/observe/health/" + membership + "/";
                assertStatus(http, prefix + "orders", 200);
                for (String suffix : List.of("primary", "replica")) {
                    assertStatus(http, prefix + "orders/" + suffix, 404);
                    assertStatus(http, prefix + "orders%2F" + suffix, 404);
                }
            }
            assertThat("Acquisitions from direct calls and routable names", acquisitionCount.get(), is(4));
            try (Http1ClientResponse response = http.get("/observe/health/ready").request()) {
                assertThat(response.status().code(), is(200));
                String body = response.as(String.class);
                assertThat(body, containsString(sourceWithSlash.name()));
                assertThat(body, containsString(clientWithSlash.name()));
            }
            assertThat("Acquisitions after aggregate readiness", acquisitionCount.get(), is(7));
        } finally {
            server.stop();
        }
    }

    private static void assertStatus(Http1Client http, String path, int status) {
        try (Http1ClientResponse response = http.get(path).request()) {
            assertThat(path, response.status().code(), is(status));
        }
    }

    private void verifyRoutes(boolean datasource) throws Exception {
        acquisitionCount.set(0);
        HealthCheck check = datasource
                ? DataSourceHealthCheck.builder().dataSource(source).query("SELECT 1").name("database").build()
                : JdbcClientHealthCheck.builder().client(client).query("SELECT 1").name("database").build();
        HealthCheck other = new HealthCheck() {
            @Override
            public String name() {
                return "other";
            }

            @Override
            public HealthCheckType type() {
                return HealthCheckType.READINESS;
            }

            @Override
            public HealthCheckResponse call() {
                return HealthCheckResponse.builder().status(false).build();
            }
        };
        var observer = HealthObserver.builder().useSystemServices(false).addCheck(check).addCheck(other).build();
        WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(observer)).build().start();
        try {
            Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
            assertStatus(http, "/observe/health/ready/database", 204);
            assertStatus(http, "/observe/health/check/database", 204);
            assertThat(acquisitionCount.get(), is(2));
            assertStatus(http, "/observe/health/ready", 503);
            assertThat(acquisitionCount.get(), is(3));
            assertStatus(http, "/observe/health/check", 404);
        } finally {
            server.stop();
        }
        HealthCheck live = datasource
                ? DataSourceHealthCheck.builder().dataSource(source).name("database").type(HealthCheckType.LIVENESS).build()
                : JdbcClientHealthCheck.builder().client(client).name("database").type(HealthCheckType.LIVENESS).build();
        HealthCheck broken = datasource
                ? DataSourceHealthCheck.builder().dataSource(source).name("broken").query("SELECT * FROM MISSING").build()
                : JdbcClientHealthCheck.builder().client(client).name("broken").query("SELECT * FROM MISSING").build();
        HealthCheck unexpected = new HealthCheck() {
            @Override
            public String name() {
                return "unexpected";
            }

            @Override
            public HealthCheckResponse call() {
                throw new IllegalStateException("Observer application failure.");
            }
        };
        server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(HealthObserver.builder().details(true).useSystemServices(false)
                        .addCheck(live).addCheck(broken).addCheck(unexpected).build())).build().start();
        try {
            Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
            assertStatus(http, "/observe/health/ready/database", 404);
            assertStatus(http, "/observe/health/live/database", 200);
            assertStatus(http, "/observe/health/check/database", 200);
            try (Http1ClientResponse response = http.get("/observe/health/check/broken").request()) {
                assertThat(response.status().code(), is(503));
                String body = response.as(String.class);
                assertThat(body, not(containsString("MISSING")));
                assertThat(body, containsString("DOWN"));
                assertThat(body, not(containsString("ERROR")));
            }
            try (Http1ClientResponse response = http.get("/observe/health/check/unexpected").request()) {
                assertThat(response.status().code(), is(500));
                String body = response.as(String.class);
                assertThat(body, containsString("ERROR"));
                assertThat(body, not(containsString("Observer application failure.")));
                assertThat(body, not(containsString("IllegalStateException")));
            }
        } finally {
            server.stop();
        }
    }

    @Service.PerRequest
    @Service.Named("orders")
    @Weight(300)
    static class RequestClients implements Service.ServicesFactory<JdbcClient> {
        @Override
        public List<Service.QualifiedInstance<JdbcClient>> services() {
            throw new AssertionError("Observer startup must not activate the request scope.");
        }
    }

    @Service.Singleton
    @Service.ExternalContracts(HealthCheck.class)
    static class LivenessCheck implements HealthCheck {
        @Override
        public String name() {
            return "process";
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.builder().status(true).build();
        }
    }
}
