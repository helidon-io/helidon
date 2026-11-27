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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.Data;
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
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
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
 * The MySQL, Oracle, and PostgreSQL suites inherit these cases and supply their own database fixtures.
 */
@SuppressWarnings("helidon:api:internal")
public abstract class AbstractJdbcHealthContract {
    private static final String CONCURRENT_PROBES = "jdbc-health-concurrency";
    private static final long CONCURRENT_POOL_TIMEOUT_MILLIS = 10_000;

    private final List<Connection> acquisitions = new CopyOnWriteArrayList<>();

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
     * Supplies the SELECT that waits until the fixture releases its lock.
     *
     * @return The SELECT waits for the lock even on a connection with auto-commit enabled.
     */
    protected abstract String timeoutQuery();

    /**
     * Keeps the timeout query blocked until the returned resource closes.
     *
     * @param blocker The fixture connection owns the lock.
     * @return The resource releases the lock when closed.
     * @throws SQLException The fixture could not acquire the lock.
     */
    protected AutoCloseable holdTimeoutLock(Connection blocker) throws SQLException {
        blocker.setAutoCommit(false);
        try (PreparedStatement statement = blocker.prepareStatement(timeoutQuery())) {
            statement.setQueryTimeout(10);
            try (ResultSet result = statement.executeQuery()) {
                assertThat("The fixture row must exist.", result.next(), is(true));
                assertThat("The fixture must lock exactly one row.", result.next(), is(false));
            }
        }
        // Closing the result set leaves the transaction and its row lock active.
        return blocker::rollback;
    }

    /**
     * Supplies driver properties through the existing pool configuration.
     *
     * @return The database fixture supplies any required connection properties.
     */
    protected Map<String, String> poolProperties() {
        return Map.of();
    }

    @BeforeEach
    protected void setUpHealth(TestInfo testInfo) throws Exception {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connection.get("url").asString().orElseThrow());
        config.setUsername(connection.get("username").asString().orElseThrow());
        config.setPassword(connection.get("password").asString().orElseThrow());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        // Hikari captures the acquisition timeout at startup. Warm-up needs a longer budget than exhaustion checks.
        config.setConnectionTimeout(testInfo.getTags().contains(CONCURRENT_PROBES) ? CONCURRENT_POOL_TIMEOUT_MILLIS : 500);
        poolProperties().forEach(config::addDataSourceProperty);
        pool = new HikariDataSource(config);
        source = mock(DataSource.class);
        // Each recording connection delegates provider calls while the real pool and driver own physical resources.
        when(source.getConnection()).thenAnswer(_ -> {
            Connection leased = recordingConnection(pool.getConnection());
            acquisitions.add(leased);
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
        assertThat(acquisitions.size(), is(0));
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(acquisitions.size(), is(2));
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
     * Verifies that cancelled validation SQL produces DOWN with empty details for client and data source checks.
     * The checks must make no transaction control calls and leave the pool usable for later checks and queries.
     */
    @Test
    protected void driverQueryTimeoutLeavesThePoolUsable() throws Exception {
        String timeoutQuery = timeoutQuery();
        AtomicReference<SQLException> executionFailure = new AtomicReference<>();
        // Record real execution failures so an unrelated acquisition or cleanup failure
        // cannot satisfy the timeout assertions.
        DataSource observedSource = mock(DataSource.class);
        when(observedSource.getConnection()).thenAnswer(_ -> {
            Connection delegate = source.getConnection();
            Connection recording = mock(Connection.class, delegatesTo(delegate));
            doAnswer(_ -> {
                PreparedStatement statement = delegate.prepareStatement(timeoutQuery);
                PreparedStatement observed = mock(PreparedStatement.class, delegatesTo(statement));
                doAnswer(_ -> {
                    try {
                        return statement.execute();
                    } catch (SQLException failure) {
                        executionFailure.set(failure);
                        throw failure;
                    }
                }).when(observed).execute();
                return observed;
            }).when(recording).prepareStatement(timeoutQuery);
            return recording;
        });
        JdbcClient checkedClient = JdbcClient.builder().dataSource(observedSource).build();
        var executor = Executors.newSingleThreadExecutor();
        List<Future<?>> workers = new ArrayList<>();
        Throwable primary = null;
        try (Connection blocker = pool.getConnection();
             AutoCloseable _ = holdTimeoutLock(blocker)) {
            Future<SQLException> driver = executor.submit(() -> {
                try (Connection connection = pool.getConnection();
                     PreparedStatement statement = connection.prepareStatement(timeoutQuery)) {
                    assertThat("The cancellation precondition uses auto-commit.", connection.getAutoCommit(), is(true));
                    statement.setQueryTimeout(1);
                    return assertThrows(SQLException.class, statement::execute);
                }
            });
            workers.add(driver);
            // The direct probe confirms that this driver cancels the locked query in auto-commit mode.
            // The future deadline only bounds the test wait.
            assertQueryCancellation(driver.get(20, TimeUnit.SECONDS));
            List<HealthCheck> checks = List.of(
                    JdbcClientHealthCheck.builder().client(checkedClient)
                            .query(timeoutQuery).queryTimeout(Duration.ofSeconds(1)).build(),
                    DataSourceHealthCheck.builder().dataSource(observedSource)
                            .query(timeoutQuery).queryTimeout(Duration.ofSeconds(1)).build());
            for (HealthCheck check : checks) {
                executionFailure.set(null);
                Future<HealthCheckResponse> worker = executor.submit(check::call);
                workers.add(worker);
                HealthCheckResponse response = worker.get(20, TimeUnit.SECONDS);
                assertThat("The timeout check is " + check.name() + ".", response.status(),
                           is(HealthCheckResponse.Status.DOWN));
                assertThat("The timeout check is " + check.name() + ".", response.details(), anEmptyMap());
                assertThat("Execution failure for " + check.name(), executionFailure.get(), notNullValue());
                assertQueryCancellation(executionFailure.get());
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            finishWorkers(workers, executor, primary);
        }
        // The same SELECT must succeed after the fixture releases its lock and the workers return their leases.
        assertThat("Client check after releasing the lock", probe(checkedClient, timeoutQuery, Duration.ofSeconds(2))
                .getAsBoolean(), is(true));
        assertThat("Data source check after releasing the lock", probe(observedSource, timeoutQuery, Duration.ofSeconds(2))
                .getAsBoolean(), is(true));
        for (Connection connection : acquisitions) {
            verify(connection, never()).setAutoCommit(anyBoolean());
            verify(connection, never()).commit();
            verify(connection, never()).rollback();
        }
        assertThat(checkedClient.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * Verifies sanitized SQL failures and subsequent client and data source reuse.
     */
    @Test
    protected void sqlFailureLeavesThePoolUsable() throws Exception {
        var sourceFailure = DataSourceHealthCheck.builder().dataSource(source)
                .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build().call();
        assertThat(sourceFailure.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(sourceFailure.details(), anEmptyMap());
        JdbcClientHealthCheck failed = JdbcClientHealthCheck.builder().client(client)
                .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build();
        HealthCheckResponse clientFailure = failed.call();
        assertThat(clientFailure.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(clientFailure.details(), anEmptyMap());
        assertThat(probe(client, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
        assertThat(probe(source, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
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
                int before = acquisitions.size();
                assertThat(healthy.getAsBoolean(), is(true));
                assertThat(broken.getAsBoolean(), is(false));
                assertThat(healthySource.getAsBoolean(), is(true));
                assertThat(brokenSource.getAsBoolean(), is(false));
                assertThat(acquisitions.size(), is(before + 4));
                Connection caller = acquisitions.get(before - 1);
                try (Connection _ = pool.getConnection(); Connection _ = pool.getConnection()) {
                    assertThat(healthy.getAsBoolean(), is(false));
                    assertThat(broken.getAsBoolean(), is(false));
                    assertThat(healthySource.getAsBoolean(), is(false));
                    assertThat(brokenSource.getAsBoolean(), is(false));
                    assertThat(acquisitions.size(), is(before + 4));
                    assertThat(caller.getAutoCommit(), is(false));
                    verify(caller, never()).commit();
                    verify(caller, never()).rollback();
                }
                // The observer uses another connection. Only the caller should see the uncommitted insert.
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
            assertThat(acquisitions.size(), is(0));
            assertThat(probe.getAsBoolean(), is(true));
            assertThat(acquisitions.size(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Coordinates simultaneous acquisitions with bounded waits and releases workers even after a failure.
     */
    @Test
    @Tag(CONCURRENT_PROBES)
    protected void concurrentProbesReleaseEveryAcquisition() throws Exception {
        verifyConcurrentProbes(true, false);
        verifyConcurrentProbes(false, false);
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
            assertThat(acquisitions.size(), is(2));
            assertStatus(http, "/observe/health/ready", 503);
            assertThat(acquisitions.size(), is(3));
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
                assertThat(body, not(containsString("JDBC_HEALTH_TABLE_MISSING")));
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
            assertThat(acquisitions.size(), is(0));
            assertThat(application.check("health-client-0").status(), is(HealthCheckResponse.Status.UP));
            assertThat(application.check("health-client-1").status(), is(HealthCheckResponse.Status.UP));
            HealthCheckResponse failed = application.check("health-client-2");
            assertThat(failed.status(), is(HealthCheckResponse.Status.DOWN));
            assertThat(failed.details(), anEmptyMap());
            assertThat(acquisitions.size(), is(3));
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(feature).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/health-client-0", 204);
                assertStatus(http, "/observe/health/check/health-client-0", 204);
                assertThat(acquisitions.size(), is(5));
                assertStatus(http, "/observe/health/ready", 503);
                assertThat(acquisitions.size(), is(7));
                assertStatus(http, "/observe/health/ready/health-client-1", 404);
                assertStatus(http, "/observe/health/live/health-client-1", 204);
                assertStatus(http, "/observe/health/check/health-client-1", 204);
                assertStatus(http, "/observe/health/check/health-client-2", 503);
                assertStatus(http, "/observe/health/check", 404);
                assertThat(acquisitions.size(), is(10));
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
     * Observes a failure while both peers hold connections and verifies subsequent pooled reuse.
     */
    @Test
    @Tag(CONCURRENT_PROBES)
    protected void failedConcurrentProbeDoesNotStrandItsPeers() throws Exception {
        verifyConcurrentProbes(true, true);
        verifyConcurrentProbes(false, true);
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
            assertThat(checks, hasSize(1));
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
        }
        values.put("data.clients.jdbc.1.health.query", query().replace("1", "2"));
        values.put("data.clients.jdbc.1.health.type", "liveness");
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.just(ConfigSources.create(values)));
            Services.setNamed(DataSource.class, source, "shared");
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks, hasSize(3));
            assertThat(checks.stream().filter(check -> check instanceof DataSourceHealthCheck).toList(), hasSize(1));
            var sourceCheck = (DataSourceHealthCheck) checks.stream().filter(check -> check.name().equals("shared"))
                    .findFirst().orElseThrow();
            var clientCheck = (JdbcClientHealthCheck) checks.stream().filter(check -> check.name().equals("client-0"))
                    .findFirst().orElseThrow();
            assertThat(sourceCheck.prototype().dataSource(), sameInstance(source));
            assertThat(sourceCheck.prototype().query(), is(query().replace("1", "3")));
            assertThat(clientCheck.prototype().query(), is("SELECT 1"));
            assertThat(clientCheck.prototype().queryTimeout(), is(Duration.ofSeconds(2)));
            assertThat(acquisitions.size(), is(0));
            var provider = manager.registry().get(ObserveFeatureProvider.class);
            ObserveFeature feature = provider.create(Config.empty(), "observe", manager.registry());
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(feature).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/shared", 204);
                assertStatus(http, "/observe/health/check/shared", 204);
                assertThat(acquisitions.size(), is(2));
                for (Connection acquisition : acquisitions) {
                    verify(acquisition).prepareStatement(query().replace("1", "3"));
                }
                assertStatus(http, "/observe/health/ready", 204);
                assertThat(acquisitions.size(), is(4));
                assertStatus(http, "/observe/health/", 204);
                assertThat(acquisitions.size(), is(7));
                assertStatus(http, "/observe/health/ready/client-1", 404);
                assertStatus(http, "/observe/health/live/client-1", 204);
                assertStatus(http, "/observe/health/check/client-1", 204);
                assertThat(acquisitions.size(), is(9));
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
     * Replaces the complete client policy while retaining independent datasource health and the selected client's source.
     */
    @Test
    protected void replacementClientOwnsItsHealthPolicy() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            Map<String, String> values = namedHealthConfiguration();
            values.put("data.clients.jdbc.0.name", "orders");
            values.put("data.clients.jdbc.0.data-source", "shared");
            values.put("data.clients.jdbc.0.health.enabled", Boolean.toString(!enabled));
            values.put("data.clients.jdbc.0.health.query", "SELECT * FROM JDBC_HEALTH_TABLE_MISSING");
            JdbcClient replacement = JdbcClient.builder().name("replacement").dataSource(source)
                    .health(health -> health.enabled(enabled).query(query().replace("1", "2"))
                            .queryTimeout(Duration.ofSeconds(3)).type(HealthCheckType.LIVENESS)).build();
            var manager = ServiceRegistryManager.create();
            GlobalServiceRegistry.registry(manager.registry());
            try {
                Services.set(Config.class, Config.just(ConfigSources.create(values)));
                Services.setNamed(DataSource.class, source, "shared");
                Qualifier provider = Qualifier.create(Data.ProviderType.TYPE, "jdbc");
                Qualifier name = Qualifier.createNamed("orders");
                Services.setQualified(JdbcClient.class, replacement, name, provider);
                int before = acquisitions.size();
                List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
                assertThat("The replacement health enabled setting is " + enabled + ".", checks, hasSize(enabled ? 2 : 1));
                assertThat(manager.registry().get(JdbcClient.class, name, provider), sameInstance(replacement));
                assertThat(acquisitions.size(), is(before));
                if (enabled) {
                    var check = (JdbcClientHealthCheck) checks.stream()
                            .filter(candidate -> candidate instanceof JdbcClientHealthCheck).findFirst().orElseThrow();
                    assertThat(check.prototype().client(), sameInstance(replacement));
                    assertThat(check.prototype().query(), is(query().replace("1", "2")));
                    assertThat(check.prototype().queryTimeout(), is(Duration.ofSeconds(3)));
                    assertThat(check.type(), is(HealthCheckType.LIVENESS));
                }
                var observer = HealthObserver.builder().useSystemServices(true).build();
                WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                        .addFeature(ObserveFeature.just(observer)).build().start();
                try {
                    Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                    assertStatus(http, "/observe/health/ready/orders", 404);
                    assertStatus(http, "/observe/health/ready/replacement", 404);
                    assertStatus(http, "/observe/health/check/orders", enabled ? 204 : 404);
                    assertStatus(http, "/observe/health/live/orders", enabled ? 204 : 404);
                    assertStatus(http, "/observe/health/check/replacement-db", 404);
                    assertStatus(http, "/observe/health/ready/shared", 204);
                    assertStatus(http, "/observe/health/", 204);
                    assertThat(acquisitions.size(), is(before + (enabled ? 5 : 2)));
                } finally {
                    server.stop();
                }
                List<Connection> records = List.copyOf(acquisitions);
                for (Connection acquisition : records.subList(before, records.size())) {
                    verify(acquisition, never()).setAutoCommit(anyBoolean());
                    verify(acquisition, never()).commit();
                    verify(acquisition, never()).rollback();
                }
                assertThat(replacement.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
                assertThat(pool.isClosed(), is(false));
                assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            } finally {
                manager.shutdown();
            }
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
                assertThat("The explicit client disablement flag is " + explicitlyDisabled + ".", checks, hasSize(1));
                assertThat(checks.getFirst().name(), is("shared"));
                assertThat(((DataSourceHealthCheck) checks.getFirst()).prototype().dataSource(), sameInstance(source));
                JdbcClient managed = manager.registry().getNamed(JdbcClient.class, "pokemon");
                assertThat(managed.prototype().health().enabled(), is(false));
                JdbcClient.builder().name("unregistered").dataSource(source)
                        .health(health -> health.enabled(true)).build();
                assertThat("The explicit client disablement flag is " + explicitlyDisabled + ".",
                           manager.registry().all(HealthCheck.class), hasSize(1));
                var provider = manager.registry().get(ObserveFeatureProvider.class);
                List<ObserveFeature> features = List.of(provider.create(Config.empty(), "observe", manager.registry()),
                        ObserveFeature.just(HealthObserver.builder().useSystemServices(true).build()));
                for (ObserveFeature feature : features) {
                    int before = acquisitions.size();
                    WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                            .addFeature(feature).build().start();
                    try {
                        Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                        assertStatus(http, "/observe/health/ready/shared", 204);
                        assertStatus(http, "/observe/health/check/shared", 204);
                        assertStatus(http, "/observe/health/check/pokemon", 404);
                        assertStatus(http, "/observe/health/check/unregistered", 404);
                        assertStatus(http, "/observe/health/ready", 204);
                        assertThat(acquisitions.size(), is(before + 3));
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
     * Starts the observer with one enabled source and another pool that fails if selected.
     */
    @Test
    protected void datasourceDiscoveryStartsOnlyTheEnabledPool() {
        Config connection = databaseConfig().get("data.clients.jdbc").asNodeList().orElseThrow().getFirst().get("connection");
        Map<String, String> values = new HashMap<>();
        values.put("data.sources.sql.0.name", "unused");
        values.put("data.sources.sql.0.provider.hikari.url", connection.get("url").asString().orElseThrow());
        // This setting binds successfully and fails when Hikari applies it.
        values.put("data.sources.sql.0.provider.hikari.maximum-pool-size", "0");
        String prefix = "data.sources.sql.1";
        values.put(prefix + ".name", "orders");
        values.put(prefix + ".health.enabled", "true");
        values.put(prefix + ".health.query", query());
        values.put(prefix + ".provider.hikari.maximum-pool-size", "1");
        values.put(prefix + ".provider.hikari.minimum-idle", "0");
        values.put(prefix + ".provider.hikari.connection-timeout", "2000");
        for (String key : List.of("url", "username", "password")) {
            values.put(prefix + ".provider.hikari." + key, connection.get(key).asString().orElseThrow());
        }
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.just(ConfigSources.create(values)));
            var observer = HealthObserver.builder().useSystemServices(true).build();
            var registry = manager.registry();
            List<HealthCheck> checks = registry.all(HealthCheck.class);
            try (var selected = (HikariDataSource) registry.getNamed(DataSource.class, "orders")) {
                assertThat(checks, hasSize(1));
                var check = (DataSourceHealthCheck) checks.getFirst();
                assertThat(check.name(), is("orders"));
                assertThat(check.prototype().dataSource(), sameInstance(selected));
                JdbcClient ordinary = JdbcClient.builder().dataSource(selected).build();
                WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                        .addFeature(ObserveFeature.just(observer)).build().start();
                try {
                    Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                    String path = "/observe/health/ready/orders";
                    assertStatus(http, path, 204);
                    assertStatus(http, "/observe/health/check/unused", 404);
                    assertThat("Query before requesting the unused pool", ordinary.create(query())
                            .map(row -> row.get(1, Integer.class)).one(), is(1));
                    var failure = assertThrows(IllegalArgumentException.class,
                                               () -> registry.getNamed(DataSource.class, "unused"));
                    assertThat(failure.getMessage(), is("maxPoolSize cannot be less than 1"));
                    assertStatus(http, path, 204);
                    assertThat("Query after requesting the unused pool", ordinary.create(query())
                            .map(row -> row.get(1, Integer.class)).one(), is(1));
                    assertThat(selected.getHikariPoolMXBean().getActiveConnections(), is(0));
                } finally {
                    server.stop();
                }
            }
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Rejects executable comments before acquisition and executes ordinary comments through both check types.
     */
    @Test
    protected void executableCommentsFailBeforeAcquisitionAndOrdinaryCommentsRemainUsable() throws Exception {
        String executable = "/*!50700 EXPLAIN */ " + query();
        assertThrows(IllegalArgumentException.class,
                     () -> DataSourceHealthCheck.builder().dataSource(source).query(executable).build());
        assertThrows(IllegalArgumentException.class,
                     () -> JdbcClientHealthCheck.builder().client(client).query(executable).build());
        assertThat(acquisitions.size(), is(0));
        String ordinary = "/* ordinary */ " + query() + " /* ordinary */";
        assertThat(probe(source, ordinary, Duration.ofSeconds(2)).getAsBoolean(), is(true));
        assertThat(probe(client, ordinary, Duration.ofSeconds(2)).getAsBoolean(), is(true));
        assertThat(acquisitions.size(), is(2));
        for (Connection acquisition : acquisitions) {
            verify(acquisition).prepareStatement(ordinary);
            verify(acquisition, never()).setAutoCommit(anyBoolean());
            verify(acquisition, never()).commit();
            verify(acquisition, never()).rollback();
        }
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        assertThat(pool.isClosed(), is(false));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * Publishes the winners of overlapping client lookups once each without merging independent registrations.
     */
    @Test
    protected void overlappingClientQualifiersPreserveSelectionAndAggregateCounts() throws Exception {
        String namedSql = query().replace("1", "2");
        String qualifiedSql = query().replace("1", "3");
        JdbcClient named = JdbcClient.builder().name("named").dataSource(source)
                .health(health -> health.enabled(true).query(namedSql).type(HealthCheckType.LIVENESS)).build();
        JdbcClient qualified = JdbcClient.builder().name("qualified").dataSource(source)
                .health(health -> health.enabled(true).query(qualifiedSql)).build();
        Qualifier name = Qualifier.createNamed("orders");
        Qualifier provider = Qualifier.create(Data.ProviderType.TYPE, "jdbc");
        var namedRegistration = Service.QualifiedInstance.create(named, name);
        var qualifiedRegistration = Service.QualifiedInstance.create(qualified, name, provider);
        for (boolean preferQualified : new boolean[] {true, false}) {
            String phase = preferQualified ? "The provider-qualified registration is preferred"
                    : "The name-only registration is preferred";
            // One factory contributes both instances. Their order establishes preference on the overlapping lookup.
            var registrations = preferQualified ? List.of(qualifiedRegistration, namedRegistration)
                    : List.of(namedRegistration, qualifiedRegistration);
            var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .putServiceInstance(AbstractJdbcHealthContract_OverlappingClients__ServiceDescriptor.INSTANCE,
                                        new OverlappingClients(registrations)).build());
            GlobalServiceRegistry.registry(manager.registry());
            try {
                Services.set(Config.class, Config.empty());
                var registry = manager.registry();
                assertThat(phase, registry.all(JdbcClient.class, name), containsInAnyOrder(named, qualified));
                assertThat(phase, registry.get(JdbcClient.class, name), sameInstance(preferQualified ? qualified : named));
                assertThat(phase, registry.get(JdbcClient.class, name, provider), sameInstance(qualified));
                int beforeDiscovery = acquisitions.size();
                List<HealthCheck> checks = registry.all(HealthCheck.class);
                List<JdbcClient> expected = preferQualified ? List.of(qualified) : List.of(named, qualified);
                assertThat(phase, checks.stream().map(check -> ((JdbcClientHealthCheck) check).prototype().client()).toList(),
                           is(expected));
                assertThat(phase, checks.stream().map(HealthCheck::name).toList(), everyItem(is("orders")));
                var providerFeature = registry.get(ObserveFeatureProvider.class);
                var configured = providerFeature.create(Config.empty(), "observe", registry);
                var explicit = ObserveFeature.just(HealthObserver.builder().useSystemServices(true).build());
                assertThat(phase + " during observer construction", acquisitions.size(), is(beforeDiscovery));
                for (ObserveFeature feature : List.of(configured, explicit)) {
                    String path = feature == configured ? " through configured discovery" : " through explicit discovery";
                    WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                            .addFeature(feature).build().start();
                    try {
                        Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                        assertNamedQuery(http, "/observe/health/ready/orders", qualifiedSql);
                        assertNamedQuery(http, "/observe/health/check/orders", preferQualified ? qualifiedSql : namedSql);
                        if (preferQualified) {
                            assertStatus(http, "/observe/health/live/orders", 404);
                        } else {
                            assertNamedQuery(http, "/observe/health/live/orders", namedSql);
                        }
                        int beforeAggregate = acquisitions.size();
                        assertStatus(http, "/observe/health/ready", 204);
                        assertThat(phase + path + " in aggregate readiness", acquisitions.size(), is(beforeAggregate + 1));
                        assertStatus(http, "/observe/health/", 204);
                        assertThat(phase + path + " in aggregate health", acquisitions.size(),
                                   is(beforeAggregate + 1 + expected.size()));
                        assertStatus(http, "/observe/health/check", 404);
                    } finally {
                        server.stop();
                    }
                }
            } finally {
                manager.shutdown();
            }
        }
        for (Connection acquisition : acquisitions) {
            verify(acquisition, never()).setAutoCommit(anyBoolean());
            verify(acquisition, never()).commit();
            verify(acquisition, never()).rollback();
        }
        assertThat(qualified.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
        assertThat(pool.isClosed(), is(false));
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    /**
     * Addresses two unregistered source instances separately and retains application ownership and later reuse.
     */
    @Test
    protected void unregisteredSourcesRetainExplicitNames() throws Exception {
        DataSource other = mock(DataSource.class, delegatesTo(source));
        var orders = DataSourceHealthCheck.builder().dataSource(source).name("orders-db").query(query()).build();
        var billing = DataSourceHealthCheck.builder().dataSource(other).name("billing-db").query(query()).build();
        assertThat(orders.prototype().dataSource(), sameInstance(source));
        assertThat(billing.prototype().dataSource(), sameInstance(other));
        assertThat(acquisitions.size(), is(0));
        var observer = HealthObserver.builder().useSystemServices(false).addCheck(orders).addCheck(billing).build();
        WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(observer)).build().start();
        try {
            Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
            assertStatus(http, "/observe/health/ready/orders-db", 204);
            assertStatus(http, "/observe/health/check/orders-db", 204);
            assertStatus(http, "/observe/health/ready/billing-db", 204);
            assertStatus(http, "/observe/health/check/billing-db", 204);
            assertStatus(http, "/observe/health/ready", 204);
            assertThat(acquisitions.size(), is(6));
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
    }

    /**
     * Preserves every colliding check in aggregates while each named route selects its first applicable check.
     */
    @Test
    protected void collidingResourceNamesPreserveGeneralHealthRouting() throws Exception {
        Map<String, String> values = namedHealthConfiguration();
        values.put("data.clients.jdbc.0.name", "shared");
        values.put("data.clients.jdbc.0.data-source", "shared");
        values.put("data.clients.jdbc.0.health.enabled", "true");
        values.put("data.clients.jdbc.0.health.query", "SELECT * FROM JDBC_HEALTH_TABLE_MISSING");
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.just(ConfigSources.create(values)));
            Services.setNamed(DataSource.class, source, "shared");
            List<HealthCheck> checks = manager.registry().all(HealthCheck.class);
            assertThat(checks, hasSize(2));
            assertThat(checks.stream().map(HealthCheck::name).toList(), everyItem(is("shared")));
            HealthCheck sourceCheck = checks.stream().filter(check -> check instanceof DataSourceHealthCheck)
                    .findFirst().orElseThrow();
            HealthCheck ready = checks.stream().filter(check -> check instanceof JdbcClientHealthCheck
                    && check.type() == HealthCheckType.READINESS).findFirst().orElseThrow();
            // An explicit third check creates the collision without restoring a discovery naming override.
            HealthCheck live = JdbcClientHealthCheck.builder().client(client).name("shared")
                    .query(query().replace("1", "2")).type(HealthCheckType.LIVENESS).build();
            assertThat(acquisitions.size(), is(0));
            var observer = HealthObserver.builder().useSystemServices(false)
                    .addCheck(live).addCheck(sourceCheck).addCheck(ready).build();
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.just(observer)).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertNamedQuery(http, "/observe/health/check/shared", query().replace("1", "2"));
                assertNamedQuery(http, "/observe/health/ready/shared", query().replace("1", "3"));
                assertNamedQuery(http, "/observe/health/live/shared", query().replace("1", "2"));
                assertThat(acquisitions.size(), is(3));
                assertStatus(http, "/observe/health/ready", 503);
                assertThat(acquisitions.size(), is(5));
                assertStatus(http, "/observe/health/", 503);
                assertThat(acquisitions.size(), is(8));
                assertStatus(http, "/observe/health/live", 204);
                assertThat(acquisitions.size(), is(9));
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

    private static void assertQueryCancellation(SQLException failure) {
        if (!(failure instanceof SQLTimeoutException)) {
            assertThat("The driver must report query cancellation. It reported " + failure, failure.getSQLState(), is("57014"));
        }
    }

    private static void finishWorkers(List<? extends Future<?>> workers,
                                      ExecutorService executor,
                                      Throwable primary) throws InterruptedException {
        try {
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

    private void assertNamedQuery(Http1Client http, String path, String sql) throws SQLException {
        int before = acquisitions.size();
        assertStatus(http, path, 204);
        // Tie the route to its own acquisition. A healthy response alone cannot identify the SQL that ran.
        List<Connection> records = List.copyOf(acquisitions);
        assertThat("Acquisition records after " + path, records, hasSize(before + 1));
        verify(records.get(before)).prepareStatement(sql);
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

    private void verifyConcurrentProbes(boolean clientCheck, boolean failOne) throws Exception {
        assertThat("Concurrency pool acquisition timeout in milliseconds", pool.getConnectionTimeout(),
                   is(CONCURRENT_POOL_TIMEOUT_MILLIS));
        // Establish all three physical connections before starting the workers.
        try (Connection _ = pool.getConnection(); Connection _ = pool.getConnection(); Connection _ = pool.getConnection()) {
            assertThat("Connections available to the workers", pool.getHikariPoolMXBean().getTotalConnections(), is(3));
        }
        assertThat("Idle connections available before launching workers", pool.getHikariPoolMXBean().getIdleConnections(), is(3));
        int peerCount = failOne ? 2 : 3;
        CountDownLatch entered = new CountDownLatch(peerCount);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean failNext = new AtomicBoolean();
        DataSource coordinated = mock(DataSource.class);
        String sql = query();
        when(coordinated.getConnection()).thenAnswer(_ -> {
            if (failNext.compareAndSet(true, false)) {
                throw new SQLException("Secret acquisition failure.");
            }
            Connection connection = source.getConnection();
            try {
                Connection leased = mock(Connection.class, delegatesTo(connection));
                doAnswer(_ -> {
                    // The probe has acquired and validated its lease by the time it prepares the statement.
                    entered.countDown();
                    try {
                        if (!release.await(15, TimeUnit.SECONDS)) {
                            throw new SQLException("The coordination wait expired.");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("The coordination wait was interrupted.", failure);
                    }
                    return connection.prepareStatement(sql);
                }).when(leased).prepareStatement(sql);
                return leased;
            } catch (Throwable failure) {
                try {
                    connection.close();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        });
        HealthCheck check = clientCheck
                ? JdbcClientHealthCheck.builder().client(JdbcClient.builder().dataSource(coordinated).build())
                        .query(sql).build()
                : DataSourceHealthCheck.builder().dataSource(coordinated).query(sql).build();
        String phase = check.name() + " with " + peerCount + " successful probes";
        var executor = Executors.newFixedThreadPool(3);
        List<Future<HealthCheckResponse>> peers = new ArrayList<>();
        List<Future<?>> workers = new ArrayList<>();
        Throwable primary = null;
        try {
            for (int index = 0; index < peerCount; index++) {
                Future<HealthCheckResponse> worker = executor.submit(check::call);
                peers.add(worker);
                workers.add(worker);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!entered.await(50, TimeUnit.MILLISECONDS)) {
                for (Future<HealthCheckResponse> peer : peers) {
                    if (peer.isDone()) {
                        throw new AssertionError(phase + ": A probe completed before checkout of all peer connections: "
                                                         + peer.get(0, TimeUnit.SECONDS).status());
                    }
                }
                if (System.nanoTime() >= deadline) {
                    assertThat(phase + ": Remaining peer acquisitions", entered.getCount(), is(0L));
                }
            }
            assertThat(phase + ": Active leases before release", pool.getHikariPoolMXBean().getActiveConnections(),
                       is(peerCount));
            if (failOne) {
                // Arm the failure after both peers hold leases so the next acquisition is the only one affected.
                failNext.set(true);
                Future<HealthCheckResponse> failed = executor.submit(check::call);
                workers.add(failed);
                var response = failed.get(5, TimeUnit.SECONDS);
                assertThat(phase + ": Injected failure", response.status(), is(HealthCheckResponse.Status.DOWN));
                assertThat(phase + ": Injected failure details", response.details(), anEmptyMap());
                assertThat(phase + ": Failure injection still armed", failNext.get(), is(false));
                assertThat(phase + ": Peer leases at failure", pool.getHikariPoolMXBean().getActiveConnections(), is(2));
            }
            release.countDown();
            for (int index = 0; index < peers.size(); index++) {
                assertThat(phase + ": Peer " + index, peers.get(index).get(5, TimeUnit.SECONDS).status(),
                           is(HealthCheckResponse.Status.UP));
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            release.countDown();
            finishWorkers(workers, executor, primary);
        }
        assertThat(phase + ": Active leases after completion", pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(phase + ": Probe after completion", check.call().status(), is(HealthCheckResponse.Status.UP));
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
            // The fixture explicitly evicts the original Hikari handle. Recovery assertions cover that eviction path.
            try {
                physical.abort((Executor) invocation.getArguments()[0]);
            } finally {
                pool.evictConnection(physical);
            }
            return null;
        }).when(recording).abort(any());
        return recording;
    }

    @Service.Singleton
    static class OverlappingClients implements Service.ServicesFactory<JdbcClient> {
        private final List<Service.QualifiedInstance<JdbcClient>> clients;

        @Service.Inject
        OverlappingClients() {
            this(List.of());
        }

        OverlappingClients(List<Service.QualifiedInstance<JdbcClient>> clients) {
            this.clients = List.copyOf(clients);
        }

        @Override
        public List<Service.QualifiedInstance<JdbcClient>> services() {
            return clients;
        }
    }

    /**
     * Invokes the health checks injected by the application registry.
     */
    @Service.Singleton
    static class JdbcHealthApplication {
        private final List<HealthCheck> checks;

        @Service.Inject
        JdbcHealthApplication(List<HealthCheck> checks) {
            this.checks = List.copyOf(checks);
        }

        HealthCheckResponse check(String name) {
            Objects.requireNonNull(name, "The health check name must not be null.");
            return checks.stream()
                    .filter(check -> check.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The requested health check is not registered."))
                    .call();
        }
    }
}
