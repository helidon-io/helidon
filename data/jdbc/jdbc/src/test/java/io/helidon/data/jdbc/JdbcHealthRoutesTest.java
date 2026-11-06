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
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import io.helidon.data.sql.datasource.DataSourceHealthCheck;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
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
     * Selects the first registered check for a duplicate path and includes both checks in aggregates.
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
            assertThat(first.name(), is(second.name()));
            WebServer server = WebServer.builder().host("127.0.0.1").port(0).featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.just(HealthObserver.builder().useSystemServices(false)
                            .addCheck(first).addCheck(second).build())).build().start();
            try {
                Http1Client http = Http1Client.builder().baseUri("http://127.0.0.1:" + server.port()).build();
                assertStatus(http, "/observe/health/ready/" + first.path(), 204);
                assertStatus(http, "/observe/health/check/" + first.path(), 204);
                assertThat(acquisitionCount.get(), is(2));
                assertStatus(http, "/observe/health/ready", 503);
                assertThat(acquisitionCount.get(), is(4));
            } finally {
                server.stop();
            }
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
                assertThat(body.contains("MISSING"), is(false));
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
}
