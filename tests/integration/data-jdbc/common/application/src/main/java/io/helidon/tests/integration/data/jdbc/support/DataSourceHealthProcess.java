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
package io.helidon.tests.integration.data.jdbc.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.sql.datasource.DataSourceHealthCheck;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Exercises real data source health in a process whose classpath excludes the JDBC client runtime.
 */
public final class DataSourceHealthProcess {
    private DataSourceHealthProcess() {
    }

    /**
     * Reads temporary fixture settings from standard input and verifies data source health and reuse.
     *
     * @param args The command line arguments are not used.
     * @throws NullPointerException The arguments array is null.
     * @throws Exception Fixture initialization or verification failed.
     */
    public static void main(String... args) throws Exception {
        Objects.requireNonNull(args, "The process arguments must not be null.");
        Properties settings = new Properties();
        settings.load(System.in);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.getProperty("url"));
        config.setUsername(settings.getProperty("username"));
        config.setPassword(settings.getProperty("password"));
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1000);
        settings.stringPropertyNames().stream()
                .filter(name -> name.startsWith("property."))
                .forEach(name -> config.addDataSourceProperty(name.substring("property.".length()), settings.getProperty(name)));
        String query = settings.getProperty("query");
        try (HikariDataSource source = new HikariDataSource(config)) {
            DataSourceHealthCheck defaults = DataSourceHealthCheck.builder().dataSource(source).build();
            if (!defaults.prototype().query().equals("SELECT 1")) {
                throw new AssertionError("The default SQL must remain SELECT 1.");
            }
            if (defaults.call().status() != HealthCheckResponse.Status.UP) {
                throw new AssertionError("The default datasource probe must succeed on this fixture.");
            }
            DataSourceHealthCheck check = DataSourceHealthCheck.builder().dataSource(source).query(query).build();
            if (check.call().status() != HealthCheckResponse.Status.UP
                    || check.call().status() != HealthCheckResponse.Status.UP) {
                throw new AssertionError("Each datasource invocation must succeed independently.");
            }
            HealthCheckResponse failed = DataSourceHealthCheck.builder().dataSource(source)
                    .query("SELECT * FROM JDBC_HEALTH_TABLE_MISSING").build().call();
            if (failed.status() != HealthCheckResponse.Status.DOWN || !failed.details().isEmpty()) {
                throw new AssertionError("Explicit failing SQL must return status-only DOWN without fallback.");
            }
            if (check.call().status() != HealthCheckResponse.Status.UP || source.isClosed()
                    || source.getHikariPoolMXBean().getActiveConnections() != 0) {
                throw new AssertionError("The datasource must remain available after probe cleanup.");
            }
            try (Connection connection = source.getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setQueryTimeout(2);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new AssertionError("Ordinary datasource reuse must return the fixture row.");
                    }
                }
            }
            var descriptor = ServiceDiscovery.create(ServiceRegistryConfig.builder()
                            .discoverServicesFromServiceLoader(false).build())
                    .allMetadata().stream()
                    .filter(metadata -> metadata.descriptorType().fqName().equals(
                            "io.helidon.data.sql.datasource.DataSourceHealthCheckFactory__ServiceDescriptor"))
                    .findFirst().orElseThrow().descriptor();
            var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .addServiceDescriptor(descriptor)
                    .build());
            GlobalServiceRegistry.registry(manager.registry());
            try {
                Services.set(Config.class, Config.just(ConfigSources.create(Map.of("data.sources.sql.0.name", "standalone",
                        "data.sources.sql.0.health-check.enabled", "true", "data.sources.sql.0.health-check.query", query))));
                Services.setNamed(DataSource.class, source, "standalone");
                var checks = manager.registry().all(HealthCheck.class);
                if (checks.size() != 1 || checks.getFirst().call().status() != HealthCheckResponse.Status.UP) {
                    throw new AssertionError("Datasource-only registry discovery must produce a usable independent check.");
                }
            } finally {
                manager.shutdown();
            }
            if (source.isClosed()) {
                throw new AssertionError("The health registry must not close the application datasource.");
            }
        }
    }
}
