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
package io.helidon.data.sql.datasource.hikari;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.common.Weight;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HikariDataSourceProviderServiceTest {
    @Test
    void registryStartsOnlySelectedPoolsWithTraceEnabled() throws Exception {
        String ordersUrl = "jdbc:helidon-test:hikari-orders";
        Driver driver = mock(Driver.class);
        when(driver.acceptsURL(ordersUrl)).thenReturn(true);
        when(driver.connect(eq(ordersUrl), any(Properties.class))).thenAnswer(_ -> {
            Connection connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.isValid(anyInt())).thenReturn(true);
            return connection;
        });
        var manager = ServiceRegistryManager.create(registryConfig(List.of(
                definition("unused", "jdbc:helidon-test:hikari-unused", 0),
                definition("orders", ordersUrl, 1))).build());
        Logger trace = Logger.getLogger("io.helidon.service.registry.LookupTrace");
        Level previousLevel = trace.getLevel();
        Set<HikariDataSource> resolved = ConcurrentHashMap.newKeySet();
        var executor = Executors.newFixedThreadPool(2);
        try (AutoCloseable _ = manager::shutdown) {
            DriverManager.registerDriver(driver);
            try (AutoCloseable _ = () -> DriverManager.deregisterDriver(driver);
                 AutoCloseable _ = () -> trace.setLevel(previousLevel);
                 AutoCloseable _ = () -> resolved.forEach(HikariDataSource::close);
                 AutoCloseable _ = () -> {
                     executor.shutdownNow();
                     assertThat("Worker termination", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
                 }) {
                trace.setLevel(Level.ALL);
                assertThat("Registry TRACE must be enabled for this test.",
                           System.getLogger(trace.getName()).isLoggable(System.Logger.Level.TRACE), is(true));
                var registry = manager.registry();
                // Enumerating registrations with TRACE enabled must leave both pools unstarted.
                List<ServiceInstance<DataSource>> registrations = registry.lookupInstances(Lookup.create(DataSource.class));
                assertThat(registrations.stream().map(ServiceInstance::qualifiers).toList(),
                           containsInAnyOrder(Set.of(Qualifier.createNamed("orders")), Set.of(Qualifier.createNamed("unused"))));
                verify(driver, never()).connect(anyString(), any(Properties.class));

                CountDownLatch ready = new CountDownLatch(2);
                Callable<HikariDataSource> resolve = () -> {
                    ready.countDown();
                    assertThat("Both named lookups are ready.", ready.await(5, TimeUnit.SECONDS), is(true));
                    var pool = (HikariDataSource) registry.getNamed(DataSource.class, "orders");
                    resolved.add(pool);
                    return pool;
                };
                var first = executor.submit(resolve);
                var second = executor.submit(resolve);
                HikariDataSource orders = first.get(10, TimeUnit.SECONDS);
                assertThat(second.get(10, TimeUnit.SECONDS), sameInstance(orders));
                assertThat(registry.getNamed(DataSource.class, "orders"), sameInstance(orders));
                verify(driver, atLeastOnce()).connect(eq(ordersUrl), any(Properties.class));

                var failure = assertThrows(IllegalArgumentException.class, () -> registry.getNamed(DataSource.class, "unused"));
                assertThat(failure.getMessage(), is("maxPoolSize cannot be less than 1"));
                try (Connection connection = orders.getConnection()) {
                    assertThat(connection.getAutoCommit(), is(true));
                }
                assertThat(orders.getHikariPoolMXBean().getActiveConnections(), is(0));
            }
        }
    }

    @Test
    void preferredApplicationDataSourceLeavesConfiguredPoolUninitialized() {
        DataSource replacement = mock(DataSource.class);
        // The shadowed pool has an invalid size, so any attempt to start it would fail the lookup.
        var manager = ServiceRegistryManager.create(registryConfig(List.of(
                definition("orders", "jdbc:helidon-test:hikari-shadowed", 0)))
                .putServiceInstance(HikariDataSourceProviderServiceTest_ApplicationDataSources__ServiceDescriptor.INSTANCE,
                                    new ApplicationDataSources(replacement))
                .build());
        try {
            var registry = manager.registry();
            List<ServiceInstance<DataSource>> registrations = registry.lookupInstances(
                    Lookup.builder().addContract(DataSource.class).addQualifier(Qualifier.createNamed("orders")).build());
            assertThat("Both registrations are present.", registrations, hasSize(2));
            assertThat(registry.getNamed(DataSource.class, "orders"), sameInstance(replacement));
        } finally {
            manager.shutdown();
        }
    }

    private static ServiceRegistryConfig.Builder registryConfig(List<DataSourceConfig> definitions) {
        return ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putServiceInstance(HikariDataSourceProviderService__ServiceDescriptor.INSTANCE,
                                    new HikariDataSourceProviderService(() -> definitions));
    }

    private static DataSourceConfig definition(String name, String url, int maximumPoolSize) {
        return DataSourceConfig.builder()
                .name(name)
                .provider(HikariDataSourceConfig.builder()
                                  .name(name)
                                  .url(url)
                                  .minimumIdle(0)
                                  .maximumPoolSize(maximumPoolSize)
                                  .build())
                .build();
    }

    @Service.Singleton
    @Weight(200)
    static class ApplicationDataSources implements Service.ServicesFactory<DataSource> {
        private final DataSource replacement;

        @Service.Inject
        ApplicationDataSources(DataSource replacement) {
            this.replacement = replacement;
        }

        @Override
        public List<Service.QualifiedInstance<DataSource>> services() {
            return List.of(Service.QualifiedInstance.create(replacement, Qualifier.createNamed("orders")));
        }
    }
}
