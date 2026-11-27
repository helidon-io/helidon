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
package io.helidon.tests.integration.data.jdbc.datasource;

import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.data.sql.datasource.DataSourceHealthCheck;
import io.helidon.data.sql.datasource.DataSourceHealthConfig;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.tests.integration.data.jdbc.database.mysql.MySqlDatabase;
import io.helidon.tests.integration.data.jdbc.database.oracle.OracleDatabase;
import io.helidon.tests.integration.data.jdbc.database.pgsql.PostgreSqlDatabase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasSize;

class DataSourceHealthIT {
    @BeforeAll
    static void verifyIsolation() {
        boolean modular = Boolean.getBoolean("datasource.test.modular");
        assertThat("Test module launch mode", DataSourceHealthIT.class.getModule().isNamed(), is(modular));
        assertThat("Datasource module launch mode", DataSourceHealthCheck.class.getModule().isNamed(), is(modular));
        assertThat("JDBC runtime in the boot layer", ModuleLayer.boot().findModule("io.helidon.data.jdbc"), is(Optional.empty()));
        assertThat("JDBC runtime class on the test classpath",
                   DataSourceHealthIT.class.getClassLoader().getResource("io/helidon/data/jdbc/JdbcClient.class"), nullValue());
    }

    @Test
    void mysql() throws Exception {
        verifyDatabase(MySqlDatabase.container());
    }

    @Test
    void postgresql() throws Exception {
        verifyDatabase(PostgreSqlDatabase.container());
    }

    @Test
    void oracle() throws Exception {
        try (var container = OracleDatabase.container()) {
            container.start();
            Config connection = OracleDatabase.administratorConnection(container);
            verifyHealth(connection.get("url").asString().orElseThrow(),
                         connection.get("username").asString().orElseThrow(),
                         connection.get("password").asString().orElseThrow(),
                         "SELECT 1 FROM DUAL");
        }
    }

    private static void verifyDatabase(JdbcDatabaseContainer<?> container) throws Exception {
        try (container) {
            container.start();
            verifyHealth(container.getJdbcUrl(), container.getUsername(), container.getPassword(), "SELECT 1");
        }
    }

    private static void verifyHealth(String url, String username, String password, String query) throws Exception {
        String sourceName = "isolated-source";
        String prefix = "data.sources.sql.0";
        String provider = prefix + ".provider.hikari";
        Config root = Config.just(ConfigSources.create(Map.of(
                prefix + ".name", sourceName,
                prefix + ".health.enabled", "true",
                prefix + ".health.query", query,
                provider + ".url", url,
                provider + ".username", username,
                provider + ".password", password,
                provider + ".maximum-pool-size", "1",
                provider + ".minimum-idle", "0",
                provider + ".connection-timeout", "2000")));
        var manager = ServiceRegistryManager.create();
        var registry = manager.registry();
        try (AutoCloseable _ = manager::shutdown) {
            GlobalServiceRegistry.registry(registry);
            Services.set(Config.class, root);
            Config definition = root.get(prefix);
            DataSourceHealthConfig policy = DataSourceHealthConfig.create(definition.get("health"));
            DataSourceConfig owner = DataSourceConfig.builder()
                    .config(definition)
                    .health(builder -> builder.from(policy))
                    .build();
            assertThat(owner.health(), is(policy));
            assertThat(policy.enabled(), is(true));
            assertThat(policy.query(), is(query));

            DataSource source = registry.getNamed(DataSource.class, sourceName);
            try (AutoCloseable _ = (AutoCloseable) source) {
                var checks = registry.all(HealthCheck.class);
                assertThat(checks, hasSize(1));
                var discovered = (DataSourceHealthCheck) checks.getFirst();
                assertThat(discovered.name(), is(sourceName));
                assertThat(discovered.prototype().dataSource(), sameInstance(source));
                assertThat(discovered.call().status(), is(HealthCheckResponse.Status.UP));

                var explicit = DataSourceHealthCheck.create(DataSourceHealthCheck.builder()
                        .dataSource(source).query(query).buildPrototype());
                assertThat(explicit.call().status(), is(HealthCheckResponse.Status.UP));

                var failed = DataSourceHealthCheck.create(builder -> builder.dataSource(source)
                        .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING"));
                var down = failed.call();
                assertThat(down.status(), is(HealthCheckResponse.Status.DOWN));
                assertThat(down.details(), anEmptyMap());

                // With one slot, a new check and an ordinary query require the failed check to return its lease.
                assertThat(discovered.call().status(), is(HealthCheckResponse.Status.UP));
                try (var connection = source.getConnection();
                     var statement = connection.prepareStatement(query)) {
                    statement.setQueryTimeout(2);
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next(), is(true));
                        assertThat(rows.getInt(1), is(1));
                    }
                }
            }
        }
    }
}
