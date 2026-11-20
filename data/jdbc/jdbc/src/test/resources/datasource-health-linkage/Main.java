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
package sample;

import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.data.sql.datasource.DataSourceHealthCheck;
import io.helidon.data.sql.datasource.DataSourceHealthCheckConfig;
import io.helidon.data.sql.datasource.DataSourceHealthConfig;
import io.helidon.data.sql.datasource.ProviderConfig;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

/**
 * Exercises datasource health without making the JDBC client runtime available to the compiler or JVM.
 */
public final class Main {
    private Main() {
    }

    /**
     * Verifies policy, owner configuration, independent execution, and registry discovery.
     *
     * @param args The launcher supplies unused arguments.
     * @throws NullPointerException The arguments array is null.
     */
    public static void main(String[] args) {
        Objects.requireNonNull(args);
        Source source = new Source();
        DataSourceHealthConfig policy = DataSourceHealthConfig.builder().enabled(true).query("SELECT 2").build();
        var owner = DataSourceConfig.builder().name("source").health(policy).provider(new ProviderConfig() {
            @Override
            public String name() {
                return "existing";
            }

            @Override
            public String type() {
                return "existing";
            }
        });
        if (!owner.health().equals(policy)) {
            throw new AssertionError("The owner must retain its configured health policy.");
        }
        owner.health(health -> health.enabled(false));
        if (owner.health().enabled()) {
            throw new AssertionError("The owner must accept policy builder updates.");
        }
        owner.health(policy);
        DataSourceHealthCheckConfig configuration = DataSourceHealthCheckConfig.builder().dataSource(source).buildPrototype();
        DataSourceHealthCheck check = DataSourceHealthCheck.create(configuration);
        if (source.acquisitions.get() != 0) {
            throw new AssertionError("Health construction must not acquire a connection.");
        }
        // Acquisition fails deliberately so execution can validate lexical access without a database.
        if (check.call().status() != HealthCheckResponse.Status.DOWN || source.acquisitions.get() != 1) {
            throw new AssertionError("Each explicit health invocation must acquire independently.");
        }
        var descriptor = ServiceDiscovery.create(ServiceRegistryConfig.builder()
                        .discoverServicesFromServiceLoader(false).build())
                .allMetadata().stream()
                .filter(metadata -> metadata.descriptorType().fqName().equals(
                        "io.helidon.data.sql.datasource.DataSourceHealthCheckFactory__ServiceDescriptor"))
                .findFirst().orElseThrow().descriptor();
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false).discoverServicesFromServiceLoader(false).addServiceDescriptor(descriptor).build());
        GlobalServiceRegistry.registry(manager.registry());
        try {
            Services.set(DataSourceConfig.class, owner.build());
            Services.setNamed(DataSource.class, source, "source");
            var checks = manager.registry().all(HealthCheck.class);
            if (checks.size() != 1 || !checks.getFirst().name().equals("source")) {
                throw new AssertionError("Datasource discovery must publish one check with its resource name.");
            }
            var discovered = (DataSourceHealthCheck) checks.getFirst();
            if (discovered.prototype().dataSource() != source || !discovered.prototype().query().equals("SELECT 2")) {
                throw new AssertionError("Discovery must retain the actual source and its configured policy.");
            }
            if (source.acquisitions.get() != 1) {
                throw new AssertionError("Registry discovery must not execute a health check.");
            }
            if (discovered.call().status() != HealthCheckResponse.Status.DOWN || source.acquisitions.get() != 2) {
                throw new AssertionError("A discovered check must acquire a fresh connection when called.");
            }
        } finally {
            manager.shutdown();
        }
    }

    private static final class Source implements DataSource {
        private final AtomicInteger acquisitions = new AtomicInteger();

        @Override
        public Connection getConnection() throws SQLException {
            acquisitions.incrementAndGet();
            throw new SQLException("The linkage fixture deliberately rejects acquisition.");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return new PrintWriter(Writer.nullWriter());
        }

        @Override
        public void setLogWriter(PrintWriter writer) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger("datasource-health-linkage");
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            Objects.requireNonNull(type);
            throw new SQLFeatureNotSupportedException("The linkage fixture does not support unwrapping.");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            Objects.requireNonNull(type);
            return false;
        }
    }
}
