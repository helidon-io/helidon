/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.data.sql.datasource.DataSourceConfig;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.suite.Suite;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Suite(MySqlSuite.class)
class TestHikari {

    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    public static void initRegistry() {
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    public static void tearDownRegistry() {
        registryManager.shutdown();
    }

    @Test
    void testDataSourceConfig(Config config) {
        assertThat(config.exists(), is(true));
        DataSourceConfig dataSourceConfig = DataSourceConfig.create(config.get("data-source.0"));
        HikariDataSourceConfig hikariDataSourceConfig = (HikariDataSourceConfig) dataSourceConfig.provider();
        assertThat(hikariDataSourceConfig.username().isPresent(), is(true));
        assertThat(hikariDataSourceConfig.username().get(),
                   is(config.get("data-source.0.provider.hikari.username").as(String.class).get()));
        assertThat(hikariDataSourceConfig.password().isPresent(), is(true));
        assertThat(new String(hikariDataSourceConfig.password().get()),
                   is(config.get("data-source.0.provider.hikari.password").as(String.class).get()));
        assertThat(hikariDataSourceConfig.connectionString().isPresent(), is(true));
        assertThat(hikariDataSourceConfig.connectionString().get(),
                   is(config.get("data-source.0.provider.hikari.connection-string").as(String.class).get()));
    }

    @Test
    void testDataSourceRegistry() {
        Supplier<HikariDataSourceProviderService> provider = registry.supply(HikariDataSourceProviderService.class);
        assertThat(provider, notNullValue());
        List<Service.QualifiedInstance<DataSource>> services = provider.get().services();
        assertThat(services.size(), is(1));
    }

}
