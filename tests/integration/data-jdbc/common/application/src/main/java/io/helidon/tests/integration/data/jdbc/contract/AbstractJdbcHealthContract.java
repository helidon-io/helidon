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
package io.helidon.tests.integration.data.jdbc.contract;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientConfig;
import io.helidon.data.jdbc.JdbcClientHealthCheck;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.datasource.DataSourceHealthCheck;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.dbclient.jdbc.JdbcClientBuilder;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.tests.integration.data.jdbc.support.DataSourceHealthProcess;
import io.helidon.tests.integration.data.jdbc.support.JdbcHealthApplication;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.ObserveFeatureProvider;
import io.helidon.webserver.observe.health.HealthObserver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies independent health execution using real drivers and bounded pooled acquisitions.
 */
@SuppressWarnings("helidon:api:internal")
public abstract class AbstractJdbcHealthContract {
    private final List<Connection> acquisitions = new ArrayList<>();
    private final AtomicInteger acquisitionCount = new AtomicInteger();
    @TempDir
    Path healthProcessDirectory;

    private HikariDataSource pool;
    private DataSource source;
    private JdbcClient client;

    /**
     * Supplies connection configuration from the running database fixture.
     *
     * @return The configuration contains the existing default client connection.
     */
    protected abstract Config databaseConfig();

    /**
     * Supplies a SELECT appropriate for the database version.
     *
     * @return The trusted SELECT returns one row.
     */
    protected abstract String query();

    /**
     * Supplies bounded slow SQL for driver query timeout coverage.
     *
     * @return The SELECT takes longer than one second without cancellation.
     */
    protected abstract String slowQuery();

    /**
     * Supplies driver properties through the existing pool configuration.
     *
     * @return The database fixture supplies any required connection properties.
     */
    protected Map<String, String> poolProperties() {
        return Map.of();
    }

    @BeforeEach
    protected void setUpHealth() throws Exception {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connection.get("url").asString().orElseThrow());
        config.setUsername(connection.get("username").asString().orElseThrow());
        config.setPassword(connection.get("password").asString().orElseThrow());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(500);
        poolProperties().forEach(config::addDataSourceProperty);
        pool = new HikariDataSource(config);
        source = mock(DataSource.class);
        // Each recording connection delegates provider calls while the real pool and driver own physical resources.
        when(source.getConnection()).thenAnswer(_ -> {
            Connection leased = recordingConnection(pool.getConnection());
            acquisitionCount.incrementAndGet();
            synchronized (acquisitions) {
                acquisitions.add(leased);
            }
            return leased;
        });
        client = JdbcClient.builder().dataSource(source).build();
    }

    @AfterEach
    protected void closeHealth() {
        if (pool != null) {
            pool.close();
        }
    }

    /**
     * Records real acquisitions and verifies that probes leave transaction control and data source ownership untouched.
     */
    @Test
    protected void freshProbesPreserveOwnershipAndOrdinaryReuse() throws Exception {
        var probe = probe(client, query(), Duration.ofSeconds(2));
        assertThat(acquisitionCount.get(), is(0));
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(acquisitionCount.get(), is(2));
        for (Connection connection : acquisitions) {
            verify(connection, never()).commit();
            verify(connection, never()).rollback();
            verify(connection, never()).setAutoCommit(anyBoolean());
        }
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        assertThat(pool.isClosed(), is(false));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * Exhausts the pool with caller connections and verifies independent failure followed by ordinary reuse.
     */
    @Test
    protected void poolExhaustionDoesNotBorrowCallerConnectionsAndRecovers() throws Exception {
        var probe = probe(client, query(), Duration.ofSeconds(2));
        var sourceProbe = probe(source, query(), Duration.ofSeconds(2));
        try (Connection a = pool.getConnection(); Connection _ = pool.getConnection(); Connection _ = pool.getConnection()) {
            a.setAutoCommit(false);
            assertThat(probe.getAsBoolean(), is(false));
            assertThat(sourceProbe.getAsBoolean(), is(false));
            assertThat(a.getAutoCommit(), is(false));
            a.rollback();
        }
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(sourceProbe.getAsBoolean(), is(true));
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
    }

    /**
     * Executes valid slow SQL before testing cancellation and subsequent pooled reuse.
     */
    @Test
    protected void driverTimeoutAndSqlFailureLeaveThePoolUsable() throws Exception {
        // Executing without cancellation verifies the SQL before testing the query timeout.
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(slowQuery())) {
            statement.setQueryTimeout(10);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next(), is(true));
            }
        }
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(slowQuery())) {
            statement.setQueryTimeout(1);
            SQLException cancellation = assertThrows(SQLException.class, statement::execute);
            assertThat("The driver must report timeout or query cancellation.",
                       cancellation instanceof SQLTimeoutException || "57014".equals(cancellation.getSQLState()), is(true));
        }
        assertThat(probe(client, slowQuery(), Duration.ofSeconds(1)).getAsBoolean(), is(false));
        assertThat(probe(source, slowQuery(), Duration.ofSeconds(1)).getAsBoolean(), is(false));
        var sourceFailure = DataSourceHealthCheck.builder().dataSource(source)
                .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build().call();
        assertThat(sourceFailure.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(sourceFailure.details().isEmpty(), is(true));
        JdbcClientHealthCheck failed = JdbcClientHealthCheck.builder().client(client)
                .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build();
        assertThat(failed.call().status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(failed.call().details().isEmpty(), is(true));
        assertThat(probe(client, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
    }

    /**
     * Observes pending writes from separate connections and verifies that the caller retains commit and rollback control.
     */
    @Test
    protected void callerTransactionRetainsPendingWorkAndCompletionOwnership() throws Exception {
        // The registry client factory supplies a client that participates in transactions over the same pool.
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder().build());
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.empty());
            Services.set(JdbcClientConfig.class, JdbcClientConfig.builder().dataSource(source).buildPrototype());
            JdbcClient managed = manager.registry().get(JdbcClient.class);
            TxSupport transactions = manager.registry().get(TxSupport.class);
            JdbcClient observer = JdbcClient.builder().dataSource(pool).build();
            observer.create("DELETE FROM TX_MATRIX").execute();
            var healthy = probe(managed, query(), Duration.ofSeconds(2));
            var broken = probe(managed, "SELECT * FROM JDBC_HEALTH_TABLE_MISSING", Duration.ofSeconds(2));
            var healthySource = probe(source, query(), Duration.ofSeconds(2));
            var brokenSource = probe(source, "SELECT * FROM JDBC_HEALTH_TABLE_MISSING", Duration.ofSeconds(2));
            transactions.transaction(Tx.Type.REQUIRED, () -> {
                managed.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES ('health-pending')").execute();
                int before = acquisitionCount.get();
                assertThat(healthy.getAsBoolean(), is(true));
                assertThat(broken.getAsBoolean(), is(false));
                assertThat(healthySource.getAsBoolean(), is(true));
                assertThat(brokenSource.getAsBoolean(), is(false));
                assertThat(acquisitionCount.get(), is(before + 4));
                Connection caller = acquisitions.get(before - 1);
                try (Connection _ = pool.getConnection(); Connection _ = pool.getConnection()) {
                    assertThat(healthy.getAsBoolean(), is(false));
                    assertThat(broken.getAsBoolean(), is(false));
                    assertThat(healthySource.getAsBoolean(), is(false));
                    assertThat(brokenSource.getAsBoolean(), is(false));
                    assertThat(acquisitionCount.get(), is(before + 4));
                    assertThat(caller.getAutoCommit(), is(false));
                    verify(caller, never()).commit();
                    verify(caller, never()).rollback();
                }
                assertThat(observer.create("SELECT COUNT(*) FROM TX_MATRIX").map(row -> row.get(1, Long.class)).one(), is(0L));
                assertThat(managed.create("SELECT COUNT(*) FROM TX_MATRIX").map(row -> row.get(1, Long.class)).one(), is(1L));
                return null;
            });
            assertThat(observer.create("SELECT COUNT(*) FROM TX_MATRIX").map(row -> row.get(1, Long.class)).one(), is(1L));
            IllegalStateException application = new IllegalStateException("Application failure.");
            TxException escaped = assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                managed.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES ('health-rollback')").execute();
                assertThat(healthy.getAsBoolean(), is(true));
                throw application;
            }));
            assertThat(escaped.getCause(), sameInstance(application));
            assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                managed.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES ('health-marked')").execute();
                TxException joined = assertThrows(TxException.class, () -> transactions.transaction(Tx.Type.REQUIRED, () -> {
                    throw application;
                }));
                assertThat(joined.getCause(), sameInstance(application));
                assertThat(healthy.getAsBoolean(), is(true));
                assertThat(broken.getAsBoolean(), is(false));
                assertThat(healthySource.getAsBoolean(), is(true));
                assertThat(brokenSource.getAsBoolean(), is(false));
                return null;
            }));
            assertThat(observer.create("SELECT COUNT(*) FROM TX_MATRIX").map(row -> row.get(1, Long.class)).one(), is(1L));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Verifies unsafe mode rejection and injected driver faults against a source that evicts aborted pool entries.
     */
    @Test
    protected void unsafeModesAndInjectedDriverFaultsInvalidateWithoutTransactionControl() throws Exception {
        verifyUnsafeProbeFailures(true);
        verifyUnsafeProbeFailures(false);
    }

    /**
     * Exercises direct driver configuration and a named source through the same public client health check.
     */
    @Test
    protected void directAndNamedSourcesRemainBoundWithoutEagerAcquisition() {
        ConnectionConfig connection = ConnectionConfig.create(databaseConfig().get("data.clients.jdbc")
                .asNodeList().orElseThrow().getFirst().get("connection"));
        JdbcClient direct = JdbcClient.builder().connection(connection).build();
        assertThat(probe(direct, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.setNamed(DataSource.class, source, "health-source");
            JdbcClient named = JdbcClient.builder().dataSourceName("health-source").build();
            var probe = probe(named, query(), Duration.ofSeconds(2));
            assertThat(acquisitionCount.get(), is(0));
            assertThat(probe.getAsBoolean(), is(true));
            assertThat(acquisitionCount.get(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Coordinates simultaneous acquisitions with bounded waits and releases workers even after a failure.
     */
    @Test
    protected void concurrentProbesReleaseEveryAcquisition() throws Exception {
        verifyConcurrentProbesReleaseEveryAcquisition(true);
        verifyConcurrentProbesReleaseEveryAcquisition(false);
    }

    /**
     * Verifies fresh named probes, aggregate membership, expected failures, and unexpected observer diagnostics.
     */
    @Test
    protected void namedRoutesRunTheSameFreshProbeAndPreserveMembership() throws Exception {
        JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client).query(query()).name("database").build();
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
        var live = JdbcClientHealthCheck.builder().client(client).query(query()).name("database")
                .type(HealthCheckType.LIVENESS).build();
        JdbcClientHealthCheck broken = JdbcClientHealthCheck.builder().client(client).name("broken")
                .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build();
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
                assertThat(body.contains("JDBC_HEALTH_TABLE_MISSING"), is(false));
                assertThat(body.contains("DOWN"), is(true));
                assertThat(body.contains("ERROR"), is(false));
            }
            try (Http1ClientResponse response = http.get("/observe/health/check/unexpected").request()) {
                assertThat(response.status().code(), is(500));
                String body = response.as(String.class);
                assertThat(body.contains("ERROR"), is(true));
                assertThat(body.contains("Observer application failure."), is(false));
                assertThat(body.contains("IllegalStateException"), is(false));
            }
        } finally {
            server.stop();
        }
    }

    /**
     * Discovers configured checks through generated services and invokes them through injection and the existing observer.
     */
    @Test
    protected void configuredChecksAreInjectedAndDiscoveredByObserver() throws Exception {
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < 3; index++) {
            String prefix = "data.clients.jdbc." + index;
            values.put(prefix + ".name", "health-client-" + index);
            values.put(prefix + ".data-source", "health-source");
            values.put(prefix + ".health.enabled", "true");
            values.put(prefix + ".health.name", "database-" + index);
            values.put(prefix + ".health.type", index == 1 ? "liveness" : "readiness");
            values.put(prefix + ".health.query", index == 2 ? "SELECT * FROM JDBC_HEALTH_TABLE_MISSING" : query());
        }
        Config root = Config.just(ConfigSources.create(values));
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, root);
            Services.setNamed(DataSource.class, source, "health-source");
            JdbcHealthApplication application = manager.registry().get(JdbcHealthApplication.class);
            var provider = manager.registry().get(ObserveFeatureProvider.class);
            ObserveFeature feature = provider.create(Config.empty(), "observe", manager.registry());
            assertThat(acquisitionCount.get(), is(0));
            assertThat(application.check("database-0").status(), is(HealthCheckResponse.Status.UP));
            assertThat(application.check("database-1").status(), is(HealthCheckResponse.Status.UP));
            HealthCheckResponse failed = application.check("database-2");
            assertThat(failed.status(), is(HealthCheckResponse.Status.DOWN));
            assertThat(failed.details().isEmpty(), is(true));
            assertThat(acquisitionCount.get(), is(3));
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(feature).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/database-0", 204);
                assertStatus(http, "/observe/health/check/database-0", 204);
                assertThat(acquisitionCount.get(), is(5));
                assertStatus(http, "/observe/health/ready", 503);
                assertThat(acquisitionCount.get(), is(7));
                assertStatus(http, "/observe/health/ready/database-1", 404);
                assertStatus(http, "/observe/health/live/database-1", 204);
                assertStatus(http, "/observe/health/check/database-1", 204);
                assertStatus(http, "/observe/health/check/database-2", 503);
                assertStatus(http, "/observe/health/check", 404);
                assertThat(acquisitionCount.get(), is(10));
            } finally {
                server.stop();
            }
            assertThat(pool.isClosed(), is(false));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            for (Connection connection : acquisitions) {
                verify(connection, never()).setAutoCommit(anyBoolean());
                verify(connection, never()).commit();
                verify(connection, never()).rollback();
            }
        } finally {
            manager.shutdown();
        }
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
    }

    /**
     * Observes a failure before peer coordination and verifies bounded cleanup and subsequent pooled reuse.
     */
    @Test
    protected void failedConcurrentProbeDoesNotStrandItsPeers() throws Exception {
        verifyFailedConcurrentProbeDoesNotStrandItsPeers(true);
        verifyFailedConcurrentProbeDoesNotStrandItsPeers(false);
    }

    /**
     * Executes real data source probes and pool reuse in a process that cannot link the JDBC client runtime.
     */
    @Test
    protected void datasourceOnlyHealthNeedsNoClientRuntime() throws Exception {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        Properties settings = new Properties();
        for (String name : List.of("url", "username", "password")) {
            settings.setProperty(name, connection.get(name).asString().orElseThrow());
        }
        settings.setProperty("query", query());
        poolProperties().forEach((name, value) -> settings.setProperty("property." + name, value));
        String libraries = Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                                                .split(File.pathSeparator))
                .map(value -> Path.of(value.replace('\\', '/')).normalize())
                .filter(path -> !path.endsWith(Path.of("data", "jdbc", "jdbc", "target", "classes"))
                        && !path.getFileName().toString().matches("helidon-data-jdbc-[0-9].*\\.jar"))
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path output = healthProcessDirectory.resolve("datasource-process.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "--class-path", libraries, DataSourceHealthProcess.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        Throwable primary = null;
        try {
            try (var input = process.getOutputStream()) {
                settings.store(input, "Temporary database fixture settings.");
            }
            assertThat("The datasource process must finish within its bound.", process.waitFor(45, TimeUnit.SECONDS), is(true));
            assertThat(Files.readString(output), process.exitValue(), is(0));
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS), is(true));
            } catch (Exception | Error failure) {
                if (primary == null) {
                    throw failure;
                }
                if (primary != failure) {
                    primary.addSuppressed(failure);
                }
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Discovers a directly configured client with no data source definition and retains its ordinary query behavior.
     */
    @Test
    protected void directClientDiscoveryDoesNotRequireDatasourceConfiguration() {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        Map<String, String> values = new HashMap<>();
        values.put("data.clients.jdbc.0.name", "direct");
        values.put("data.clients.jdbc.0.health.enabled", "true");
        for (String name : List.of("url", "username", "password")) {
            values.put("data.clients.jdbc.0.connection." + name, connection.get(name).asString().orElseThrow());
        }
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.just(ConfigSources.create(values)));
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks.size(), is(1));
            var check = (JdbcClientHealthCheck) checks.getFirst();
            assertThat(check.name(), is("direct"));
            assertThat(check.prototype().query(), is("SELECT 1"));
            assertThat(check.call().status(), is(HealthCheckResponse.Status.UP));
            JdbcClient actual = manager.registry().getNamed(JdbcClient.class, "direct");
            assertThat(check.prototype().client(), sameInstance(actual));
            assertThat(actual.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Keeps data source and shared-client checks individually addressable with independent SQL and membership.
     */
    @Test
    protected void sharedSourceChecksKeepIndependentPolicyAndRoutes() throws Exception {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        Map<String, String> values = new HashMap<>();
        String sourcePrefix = "data.sources.sql.0";
        values.put(sourcePrefix + ".name", "shared");
        values.put(sourcePrefix + ".health.enabled", "true");
        values.put(sourcePrefix + ".health.name", "source-db");
        values.put(sourcePrefix + ".health.query", query().replace("1", "3"));
        values.put(sourcePrefix + ".health.query-timeout", "PT3S");
        for (String name : List.of("url", "username", "password")) {
            values.put(sourcePrefix + ".provider.hikari." + name, connection.get(name).asString().orElseThrow());
        }
        values.put(sourcePrefix + ".provider.hikari.maximum-pool-size", "2");
        values.put(sourcePrefix + ".provider.hikari.minimum-idle", "0");
        values.put(sourcePrefix + ".provider.hikari.connection-timeout", "500");
        for (int index = 0; index < 2; index++) {
            String prefix = "data.clients.jdbc." + index;
            values.put(prefix + ".name", "client-" + index);
            values.put(prefix + ".data-source", "shared");
            values.put(prefix + ".health.enabled", "true");
            values.put(prefix + ".health.name", "client-db-" + index);
        }
        values.put("data.clients.jdbc.1.health.query", query().replace("1", "2"));
        values.put("data.clients.jdbc.1.health.type", "liveness");
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.just(ConfigSources.create(values)));
            Services.setNamed(DataSource.class, source, "shared");
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks.size(), is(3));
            assertThat(checks.stream().filter(check -> check instanceof DataSourceHealthCheck).count(), is(1L));
            var sourceCheck = (DataSourceHealthCheck) checks.stream().filter(check -> check.name().equals("source-db"))
                    .findFirst().orElseThrow();
            var clientCheck = (JdbcClientHealthCheck) checks.stream().filter(check -> check.name().equals("client-db-0"))
                    .findFirst().orElseThrow();
            assertThat(sourceCheck.prototype().dataSource(), sameInstance(source));
            assertThat(sourceCheck.prototype().query(), is(query().replace("1", "3")));
            assertThat(clientCheck.prototype().query(), is("SELECT 1"));
            assertThat(clientCheck.prototype().queryTimeout(), is(Duration.ofSeconds(2)));
            assertThat(acquisitionCount.get(), is(0));
            var provider = manager.registry().get(ObserveFeatureProvider.class);
            ObserveFeature feature = provider.create(Config.empty(), "observe", manager.registry());
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(feature).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/source-db", 204);
                assertStatus(http, "/observe/health/check/source-db", 204);
                assertThat(acquisitionCount.get(), is(2));
                for (Connection acquisition : acquisitions) {
                    verify(acquisition).prepareStatement(query().replace("1", "3"));
                }
                assertStatus(http, "/observe/health/ready", 204);
                assertThat(acquisitionCount.get(), is(4));
                assertStatus(http, "/observe/health/", 204);
                assertThat(acquisitionCount.get(), is(7));
                assertStatus(http, "/observe/health/ready/client-db-1", 404);
                assertStatus(http, "/observe/health/live/client-db-1", 204);
                assertStatus(http, "/observe/health/check/client-db-1", 204);
                assertThat(acquisitionCount.get(), is(9));
            } finally {
                server.stop();
            }
            for (Connection acquisition : acquisitions) {
                verify(acquisition, never()).setAutoCommit(anyBoolean());
                verify(acquisition, never()).commit();
                verify(acquisition, never()).rollback();
            }
            assertThat(pool.isClosed(), is(false));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Keeps source-only enablement independent of client policies through both observer construction paths.
     */
    @Test
    protected void sourceOnlyEnablementDoesNotCreateClientProbes() throws Exception {
        for (boolean explicitlyDisabled : List.of(false, true)) {
            Map<String, String> values = namedHealthConfiguration();
            values.put("data.clients.jdbc.0.name", "pokemon");
            values.put("data.clients.jdbc.0.data-source", "shared");
            if (explicitlyDisabled) {
                values.put("data.clients.jdbc.0.health.enabled", "false");
            }
            var manager = ServiceRegistryManager.create();
            GlobalServiceRegistry.registry(manager.registry());
            try {
                Services.set(Config.class, Config.just(ConfigSources.create(values)));
                Services.setNamed(DataSource.class, source, "shared");
                List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
                assertThat(checks.size(), is(1));
                assertThat(checks.getFirst().name(), is("shared"));
                assertThat(((DataSourceHealthCheck) checks.getFirst()).prototype().dataSource(), sameInstance(source));
                JdbcClient managed = manager.registry().getNamed(JdbcClient.class, "pokemon");
                assertThat(managed.prototype().health().enabled(), is(false));
                JdbcClient.builder().name("unregistered").dataSource(source)
                        .health(health -> health.enabled(true)).build();
                assertThat(manager.registry().all(HealthCheck.class).size(), is(1));
                var provider = manager.registry().get(ObserveFeatureProvider.class);
                List<ObserveFeature> features = List.of(provider.create(Config.empty(), "observe", manager.registry()),
                        ObserveFeature.just(HealthObserver.builder().useSystemServices(true).build()));
                for (ObserveFeature feature : features) {
                    int before = acquisitionCount.get();
                    WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                            .addFeature(feature).build().start();
                    try {
                        Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                        assertStatus(http, "/observe/health/ready/shared", 204);
                        assertStatus(http, "/observe/health/check/shared", 204);
                        assertStatus(http, "/observe/health/check/pokemon", 404);
                        assertStatus(http, "/observe/health/check/unregistered", 404);
                        assertStatus(http, "/observe/health/ready", 204);
                        assertThat(acquisitionCount.get(), is(before + 3));
                    } finally {
                        server.stop();
                    }
                }
            } finally {
                manager.shutdown();
            }
        }
        for (Connection acquisition : acquisitions) {
            verify(acquisition).prepareStatement(query().replace("1", "3"));
            verify(acquisition, never()).setAutoCommit(anyBoolean());
            verify(acquisition, never()).commit();
            verify(acquisition, never()).rollback();
        }
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        assertThat(pool.isClosed(), is(false));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * Preserves every colliding check in aggregates while each named route selects its first applicable check.
     */
    @Test
    protected void collidingResourceNamesPreserveGeneralHealthRouting() throws Exception {
        Map<String, String> values = namedHealthConfiguration();
        for (int index = 0; index < 2; index++) {
            String prefix = "data.clients.jdbc." + index;
            values.put(prefix + ".name", index == 0 ? "shared" : "other");
            values.put(prefix + ".data-source", "shared");
            values.put(prefix + ".health.enabled", "true");
        }
        values.put("data.clients.jdbc.0.health.query", "SELECT * FROM JDBC_HEALTH_TABLE_MISSING");
        values.put("data.clients.jdbc.1.health.name", "shared");
        values.put("data.clients.jdbc.1.health.type", "liveness");
        values.put("data.clients.jdbc.1.health.query", query().replace("1", "2"));
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.just(ConfigSources.create(values)));
            Services.setNamed(DataSource.class, source, "shared");
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks.size(), is(3));
            assertThat(checks.stream().allMatch(check -> check.name().equals("shared")), is(true));
            HealthCheck sourceCheck = checks.stream().filter(check -> check instanceof DataSourceHealthCheck)
                    .findFirst().orElseThrow();
            HealthCheck ready = checks.stream().filter(check -> check instanceof JdbcClientHealthCheck
                    && check.type() == HealthCheckType.READINESS).findFirst().orElseThrow();
            HealthCheck live = checks.stream().filter(check -> check.type() == HealthCheckType.LIVENESS)
                    .findFirst().orElseThrow();
            assertThat(acquisitionCount.get(), is(0));
            var observer = HealthObserver.builder().useSystemServices(false)
                    .addCheck(live).addCheck(sourceCheck).addCheck(ready).build();
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.just(observer)).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/check/shared", 204);
                verify(acquisitions.getLast()).prepareStatement(query().replace("1", "2"));
                assertStatus(http, "/observe/health/ready/shared", 204);
                verify(acquisitions.getLast()).prepareStatement(query().replace("1", "3"));
                assertStatus(http, "/observe/health/live/shared", 204);
                assertThat(acquisitionCount.get(), is(3));
                assertStatus(http, "/observe/health/ready", 503);
                assertThat(acquisitionCount.get(), is(5));
                assertStatus(http, "/observe/health/", 503);
                assertThat(acquisitionCount.get(), is(8));
                assertStatus(http, "/observe/health/live", 204);
                assertThat(acquisitionCount.get(), is(9));
            } finally {
                server.stop();
            }
            for (Connection acquisition : acquisitions) {
                verify(acquisition, never()).setAutoCommit(anyBoolean());
                verify(acquisition, never()).commit();
                verify(acquisition, never()).rollback();
            }
            assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
            assertThat(pool.isClosed(), is(false));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Preserves existing DbClient queries and health contracts beside the new source and client checks.
     */
    @Test
    protected void existingDbClientHealthAndQueriesCoexist() {
        try (DbClient legacy = JdbcClientBuilder.create().connectionPool(() -> {
            try {
                return pool.getConnection();
            } catch (SQLException failure) {
                throw new IllegalStateException("The legacy test client could not acquire a connection.", failure);
            }
        }).build()) {
            var legacyCheck = DbClientHealthCheck.builder(legacy).name("legacy-db").statement(query()).build();
            var sourceCheck = DataSourceHealthCheck.builder().dataSource(source).name("source-db").build();
            var clientCheck = JdbcClientHealthCheck.builder().client(client).name("client-db").build();
            assertThat(legacyCheck.type(), is(HealthCheckType.LIVENESS));
            assertThat(legacyCheck.call().status(), is(HealthCheckResponse.Status.UP));
            assertThat(sourceCheck.call().status(), is(HealthCheckResponse.Status.UP));
            assertThat(clientCheck.call().status(), is(HealthCheckResponse.Status.UP));
            HealthCheckResponse failure = DbClientHealthCheck.builder(legacy)
                    .statement("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build().call();
            assertThat(failure.status(), is(HealthCheckResponse.Status.DOWN));
            assertThat(failure.details(), is(Map.of()));
            assertThat(probe(source, "SELECT * FROM JDBC_HEALTH_TABLE_MISSING", Duration.ofSeconds(2))
                               .getAsBoolean(), is(false));
            assertThat(legacyCheck.call().status(), is(HealthCheckResponse.Status.UP));
            try (var rows = legacy.execute().query(query())) {
                assertThat(rows.map(row -> row.column(1).get(Number.class).intValue()).toList(), is(List.of(1)));
            }
            assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.just(HealthObserver.builder().useSystemServices(false)
                            .addCheck(sourceCheck).addCheck(clientCheck).addCheck(legacyCheck).build())).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready", 204);
                assertStatus(http, "/observe/health/ready/legacy-db", 404);
                assertStatus(http, "/observe/health/live/legacy-db", 204);
                assertStatus(http, "/observe/health/check/legacy-db", 204);
            } finally {
                server.stop();
            }
        }
        assertThat(pool.isClosed(), is(false));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    private static void finishWorkers(CountDownLatch release,
                                      List<Future<Boolean>> workers,
                                      ExecutorService executor,
                                      Throwable primary) throws InterruptedException {
        try {
            release.countDown();
            workers.forEach(worker -> worker.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException | RuntimeException | Error failure) {
            if (primary == null) {
                throw failure;
            }
            if (primary != failure) {
                primary.addSuppressed(failure);
            }
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static BooleanSupplier probe(DataSource source, String query, Duration timeout) {
        DataSourceHealthCheck check = DataSourceHealthCheck.builder()
                .dataSource(source).query(query).queryTimeout(timeout).build();
        return () -> check.call().status() == HealthCheckResponse.Status.UP;
    }

    private static BooleanSupplier probe(JdbcClient client, String query, Duration timeout) {
        JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client).query(query).queryTimeout(timeout).build();
        return () -> check.call().status() == HealthCheckResponse.Status.UP;
    }

    private static void assertStatus(Http1Client http, String path, int status) {
        try (Http1ClientResponse response = http.get(path).request()) {
            assertThat(path, response.status().code(), is(status));
        }
    }

    private Map<String, String> namedHealthConfiguration() {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        Map<String, String> values = new HashMap<>();
        String prefix = "data.sources.sql.0";
        values.put(prefix + ".name", "shared");
        values.put(prefix + ".health.enabled", "true");
        values.put(prefix + ".health.query", query().replace("1", "3"));
        for (String name : List.of("url", "username", "password")) {
            values.put(prefix + ".provider.hikari." + name, connection.get(name).asString().orElseThrow());
        }
        values.put(prefix + ".provider.hikari.maximum-pool-size", "2");
        values.put(prefix + ".provider.hikari.minimum-idle", "0");
        values.put(prefix + ".provider.hikari.connection-timeout", "500");
        return values;
    }

    private void verifyConcurrentProbesReleaseEveryAcquisition(boolean clientCheck) throws Exception {
        CountDownLatch entered = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        DataSource coordinated = mock(DataSource.class);
        when(coordinated.getConnection()).thenAnswer(_ -> {
            Connection connection = source.getConnection();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The coordination wait expired.");
                }
                return connection;
            } catch (Throwable failure) {
                try {
                    connection.close();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        });
        var probe = clientCheck
                ? probe(JdbcClient.builder().dataSource(coordinated).build(), query(), Duration.ofSeconds(2))
                : probe(coordinated, query(), Duration.ofSeconds(2));
        var executor = Executors.newFixedThreadPool(3);
        List<Future<Boolean>> workers = new ArrayList<>();
        Throwable primary = null;
        try {
            for (int index = 0; index < 3; index++) {
                workers.add(executor.submit(probe::getAsBoolean));
            }
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            for (Future<Boolean> worker : workers) {
                assertThat(worker.get(5, TimeUnit.SECONDS), is(true));
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            finishWorkers(release, workers, executor, primary);
        }
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    private void verifyFailedConcurrentProbeDoesNotStrandItsPeers(boolean clientCheck) throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        DataSource coordinated = mock(DataSource.class);
        when(coordinated.getConnection()).thenAnswer(_ -> {
            if (attempts.getAndIncrement() == 0) {
                throw new SQLException("Secret acquisition failure.");
            }
            Connection connection = source.getConnection();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new SQLException("The coordination wait expired.");
                }
                return connection;
            } catch (Throwable failure) {
                try {
                    connection.close();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        });
        var probe = clientCheck
                ? probe(JdbcClient.builder().dataSource(coordinated).build(), query(), Duration.ofSeconds(2))
                : probe(coordinated, query(), Duration.ofSeconds(2));
        var executor = Executors.newFixedThreadPool(3);
        var completed = new ExecutorCompletionService<Boolean>(executor);
        List<Future<Boolean>> workers = new ArrayList<>();
        Throwable primary = null;
        try {
            for (int index = 0; index < 3; index++) {
                workers.add(completed.submit(probe::getAsBoolean));
            }
            Future<Boolean> failure = completed.poll(5, TimeUnit.SECONDS);
            assertThat(failure, notNullValue());
            assertThat(failure.get(5, TimeUnit.SECONDS), is(false));
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            for (int index = 0; index < 2; index++) {
                Future<Boolean> success = completed.poll(5, TimeUnit.SECONDS);
                assertThat(success, notNullValue());
                assertThat(success.get(5, TimeUnit.SECONDS), is(true));
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            finishWorkers(release, workers, executor, primary);
        }
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(probe.getAsBoolean(), is(true));
        for (Connection connection : acquisitions) {
            verify(connection, never()).setAutoCommit(anyBoolean());
            verify(connection, never()).commit();
            verify(connection, never()).rollback();
        }
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
    }

    private void verifyUnsafeProbeFailures(boolean clientCheck) throws Exception {
        for (String fault : List.of("mode", "timeout", "traversal", "cleanup")) {
            try (Connection physical = pool.getConnection()) {
                if (fault.equals("mode")) {
                    physical.setAutoCommit(false);
                }
                Connection connection = recordingConnection(physical);
                DataSource failingSource = mock(DataSource.class);
                when(failingSource.getConnection()).thenReturn(connection);
                if (!fault.equals("mode")) {
                    PreparedStatement delegate = physical.prepareStatement(query());
                    PreparedStatement statement = mock(PreparedStatement.class, delegatesTo(delegate));
                    doReturn(statement).when(connection).prepareStatement(query());
                    switch (fault) {
                        case "timeout" -> doThrow(new SQLException("Secret timeout diagnostic."))
                                .when(statement).setQueryTimeout(2);
                        case "traversal" -> {
                            ResultSet rows = mock(ResultSet.class);
                            doReturn(rows).when(statement).getResultSet();
                            when(rows.next()).thenThrow(new SQLException("Secret traversal diagnostic."));
                        }
                        case "cleanup" -> doAnswer(_ -> {
                            delegate.close();
                            throw new SQLException("Secret cleanup diagnostic.");
                        }).when(statement).close();
                        default -> throw new AssertionError(fault);
                    }
                }
                var failed = clientCheck
                        ? probe(JdbcClient.builder().dataSource(failingSource).build(), query(), Duration.ofSeconds(2))
                        : probe(failingSource, query(), Duration.ofSeconds(2));
                assertThat(fault, failed.getAsBoolean(), is(false));
                verify(connection, never()).setAutoCommit(anyBoolean());
                verify(connection, never()).commit();
                verify(connection, never()).rollback();
            }
            assertThat(probe(client, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
        }
    }

    private Connection recordingConnection(Connection physical) throws Exception {
        Connection recording = mock(Connection.class, delegatesTo(physical));
        doAnswer(invocation -> {
            // The source must evict its pooled entry when abort invalidates the physical connection.
            try {
                physical.abort((Executor) invocation.getArguments()[0]);
            } finally {
                pool.evictConnection(physical);
            }
            return null;
        }).when(recording).abort(any());
        return recording;
    }


}
