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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientConfig;
import io.helidon.data.jdbc.JdbcHealthSupport;
import io.helidon.data.jdbc.health.JdbcClientHealthCheck;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
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
     * Records real acquisitions and verifies that probes leave transaction control and datasource ownership untouched.
     */
    @Test
    protected void freshProbesPreserveOwnershipAndOrdinaryReuse() throws Exception {
        var probe = JdbcHealthSupport.createProbe(client, query(), Duration.ofSeconds(2));
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
        var probe = JdbcHealthSupport.createProbe(client, query(), Duration.ofSeconds(2));
        try (Connection a = pool.getConnection(); Connection _ = pool.getConnection(); Connection _ = pool.getConnection()) {
            a.setAutoCommit(false);
            assertThat(probe.getAsBoolean(), is(false));
            assertThat(a.getAutoCommit(), is(false));
            a.rollback();
        }
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(client.create(query()).map(row -> row.get(1, Integer.class)).one(), is(1));
    }

    /**
     * Executes valid slow SQL before testing cancellation and subsequent pooled reuse.
     */
    @Test
    protected void driverTimeoutAndSqlFailureLeaveThePoolUsable() throws Exception {
        // Successful uncancelled execution proves that the timeout case uses valid server SQL.
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
        assertThat(JdbcHealthSupport.createProbe(client, slowQuery(), Duration.ofSeconds(1)).getAsBoolean(), is(false));
        JdbcClientHealthCheck failed = JdbcClientHealthCheck.builder().client(client)
                .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build();
        assertThat(failed.call().status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(failed.call().details().isEmpty(), is(true));
        assertThat(JdbcHealthSupport.createProbe(client, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
    }

    /**
     * Observes pending writes from separate connections and verifies caller controlled commit and rollback.
     */
    @Test
    protected void callerTransactionRetainsPendingWorkAndCompletionOwnership() throws Exception {
        // The registry client factory supplies a transaction aware client over the same physical pool.
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder().build());
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(Config.class, Config.empty());
            Services.set(JdbcClientConfig.class, JdbcClientConfig.builder().dataSource(source).buildPrototype());
            JdbcClient managed = manager.registry().get(JdbcClient.class);
            TxSupport transactions = manager.registry().get(TxSupport.class);
            JdbcClient observer = JdbcClient.builder().dataSource(pool).build();
            observer.create("DELETE FROM TX_MATRIX").execute();
            var healthy = JdbcHealthSupport.createProbe(managed, query(), Duration.ofSeconds(2));
            var broken = JdbcHealthSupport.createProbe(managed, "SELECT * FROM JDBC_HEALTH_TABLE_MISSING", Duration.ofSeconds(2));
            transactions.transaction(Tx.Type.REQUIRED, () -> {
                managed.create("INSERT INTO TX_MATRIX (DATA_VALUE) VALUES ('health-pending')").execute();
                int before = acquisitionCount.get();
                assertThat(healthy.getAsBoolean(), is(true));
                assertThat(broken.getAsBoolean(), is(false));
                assertThat(acquisitionCount.get(), is(before + 2));
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
                var failed = JdbcHealthSupport.createProbe(JdbcClient.builder().dataSource(failingSource).build(),
                                                          query(), Duration.ofSeconds(2));
                assertThat(fault, failed.getAsBoolean(), is(false));
                verify(connection, never()).setAutoCommit(anyBoolean());
                verify(connection, never()).commit();
                verify(connection, never()).rollback();
            }
            assertThat(JdbcHealthSupport.createProbe(client, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
        }
    }

    /**
     * Exercises direct driver configuration and a named source through the same public probe bridge.
     */
    @Test
    protected void directAndNamedSourcesRemainBoundWithoutEagerAcquisition() {
        ConnectionConfig connection = ConnectionConfig.create(databaseConfig().get("data.clients.jdbc")
                .asNodeList().orElseThrow().getFirst().get("connection"));
        JdbcClient direct = JdbcClient.builder().connection(connection).build();
        assertThat(JdbcHealthSupport.createProbe(direct, query(), Duration.ofSeconds(2)).getAsBoolean(), is(true));
        var manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.setNamed(DataSource.class, source, "health-source");
            JdbcClient named = JdbcClient.builder().dataSourceName("health-source").build();
            var probe = JdbcHealthSupport.createProbe(named, query(), Duration.ofSeconds(2));
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
                connection.close();
                throw failure;
            }
        });
        var probe = JdbcHealthSupport.createProbe(JdbcClient.builder().dataSource(coordinated).build(),
                                                  query(), Duration.ofSeconds(2));
        var executor = Executors.newFixedThreadPool(3);
        List<Future<Boolean>> workers = new ArrayList<>();
        try {
            for (int index = 0; index < 3; index++) {
                workers.add(executor.submit(probe::getAsBoolean));
            }
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            for (Future<Boolean> worker : workers) {
                assertThat(worker.get(5, TimeUnit.SECONDS), is(true));
            }
        } finally {
            release.countDown();
            workers.forEach(worker -> worker.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
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
                assertThat(response.as(String.class).contains("Observer application failure."), is(true));
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
            values.put(prefix + ".health-check.enabled", "true");
            values.put(prefix + ".health-check.name", "database-" + index);
            values.put(prefix + ".health-check.type", index == 1 ? "liveness" : "readiness");
            values.put(prefix + ".health-check.query", index == 2 ? "SELECT * FROM JDBC_HEALTH_TABLE_MISSING" : query());
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
                connection.close();
                throw failure;
            }
        });
        var probe = JdbcHealthSupport.createProbe(JdbcClient.builder().dataSource(coordinated).build(),
                                                  query(), Duration.ofSeconds(2));
        var executor = Executors.newFixedThreadPool(3);
        var completed = new ExecutorCompletionService<Boolean>(executor);
        List<Future<Boolean>> workers = new ArrayList<>();
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
        } finally {
            release.countDown();
            workers.forEach(worker -> worker.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
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

    private static void assertStatus(Http1Client http, String path, int status) {
        try (Http1ClientResponse response = http.get(path).request()) {
            assertThat(path, response.status().code(), is(status));
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
