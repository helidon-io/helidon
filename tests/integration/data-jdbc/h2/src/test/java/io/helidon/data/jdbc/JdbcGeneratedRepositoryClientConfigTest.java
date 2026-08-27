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

import io.helidon.config.Config;
import io.helidon.data.jdbc.tests.declarative.repository.DefaultClientRepository;
import io.helidon.data.jdbc.tests.declarative.repository.InventoryClientRepository;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcGeneratedRepositoryClientConfigTest {

    private static final String YAML_DEFAULT_URL =
            "jdbc:h2:mem:jdbc_generated_repository_default;DB_CLOSE_DELAY=-1";
    private static final String YAML_INVENTORY_URL =
            "jdbc:h2:mem:jdbc_generated_repository_inventory;DB_CLOSE_DELAY=-1";

    /**
     * Restores the shared configuration service after each registry test.
     */
    @AfterEach
    void resetConfig() {
        TestConfigFactory.reset();
    }

    /**
     * Verifies two programmatically registered client configurations sharing
     * one data source inject the default and named generated repositories.
     */
    @Test
    void executesGeneratedRepositoriesFromProgrammaticConfigurations() {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:h2:mem:jdbc_programmatic_repositories;DB_CLOSE_DELAY=-1");
        poolConfig.setMaximumPoolSize(2);
        poolConfig.setConnectionTimeout(1_000);
        try (HikariDataSource dataSource = new HikariDataSource(poolConfig)) {
            JdbcClient setup = JdbcClient.builder().dataSource(dataSource).build();
            createSchema(setup);
            JdbcClientConfig defaultConfig = JdbcClient.builder()
                    .dataSource(dataSource)
                    .buildPrototype();
            JdbcClientConfig inventoryConfig = JdbcClient.builder()
                    .name("inventory")
                    .dataSource(dataSource)
                    .buildPrototype();
            ServiceRegistryManager manager = ServiceRegistryManager.create();
            GlobalServiceRegistry.registry(manager.registry());
            try {
                Services.set(JdbcClientConfig.class, defaultConfig, inventoryConfig);
                DefaultClientRepository defaultRepository = Services.get(DefaultClientRepository.class);
                InventoryClientRepository inventoryRepository = Services.get(InventoryClientRepository.class);

                assertThat(defaultRepository.insert(1, "default-programmatic"), is(1L));
                assertThat(inventoryRepository.insert(2, "inventory-programmatic"), is(1L));
                assertThat(inventoryRepository.find(1), is("default-programmatic"));
                assertThat(defaultRepository.find(2), is("inventory-programmatic"));
            } finally {
                manager.shutdown();
            }
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    /**
     * Verifies YAML client definitions inject repositories backed by distinct
     * data sources and keep their writes isolated by client name.
     */
    @Test
    void executesGeneratedRepositoriesFromYamlConfigurations() {
        createSchema(directClient(YAML_DEFAULT_URL));
        createSchema(directClient(YAML_INVENTORY_URL));
        TestConfigFactory.config(Config.create());
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            DefaultClientRepository defaultRepository = manager.registry().get(DefaultClientRepository.class);
            InventoryClientRepository inventoryRepository = manager.registry().get(InventoryClientRepository.class);

            assertThat(defaultRepository.insert(1, "default-yaml"), is(1L));
            assertThat(inventoryRepository.insert(1, "inventory-yaml"), is(1L));
            assertThat(defaultRepository.find(1), is("default-yaml"));
            assertThat(inventoryRepository.find(1), is("inventory-yaml"));
        } finally {
            manager.shutdown();
        }
    }

    private static JdbcClient directClient(String url) {
        return JdbcClient.builder()
                .connection(connection -> connection
                        .url(url)
                        .jdbcDriverClassName("org.h2.Driver"))
                .build();
    }

    private static void createSchema(JdbcClient client) {
        client.create("DROP TABLE IF EXISTS CLIENT_VALUE").execute();
        client.create("CREATE TABLE CLIENT_VALUE (ID INT PRIMARY KEY, NAME VARCHAR(80) NOT NULL)").execute();
    }
}
