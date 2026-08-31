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

import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.config.Config;
import io.helidon.data.jdbc.tests.declarative.repository.DefaultClientRepository;
import io.helidon.data.jdbc.tests.declarative.repository.InventoryClientRepository;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * Verifies a repository without {@link io.helidon.data.jdbc.Jdbc.Client}
     * injects only the default registry-managed JDBC client and does not use
     * the removed optional-client fallback shape.
     *
     * @throws Exception when the generated source cannot be inspected
     */
    @Test
    void generatesDefaultClientInjectionWhenNoSelectorIsPresent() throws Exception {
        String source = generatedSource(DefaultClientRepository.class);

        assertThat(source, containsString("@Service.Named(\"@default\") @Data.ProviderType(\"jdbc\") "
                                                  + "JdbcClient jdbcClient"));
        assertThat(source, not(containsString("@Service.Named(\"inventory\")")));
        assertThat(source, not(containsString("Optional<JdbcClient>")));
        assertThat(source, not(containsString("Supplier<JdbcClient>")));
    }

    /**
     * Verifies {@link io.helidon.data.jdbc.Jdbc.Client} emits a required
     * constructor dependency for exactly the selected client and never
     * generates a default-client fallback.
     *
     * @throws Exception when the generated source cannot be inspected
     */
    @Test
    void generatesNamedClientInjectionWhenSelectorIsPresent() throws Exception {
        String source = generatedSource(InventoryClientRepository.class);

        assertThat(source, containsString("@Service.Named(\"inventory\") @Data.ProviderType(\"jdbc\") "
                                                  + "JdbcClient jdbcClient"));
        assertThat(source, not(containsString("@Service.Named(\"@default\")")));
        assertThat(source, not(containsString("Optional<JdbcClient>")));
        assertThat(source, not(containsString("Supplier<JdbcClient>")));
    }

    /**
     * Verifies a generated repository requesting an unpublished named client
     * fails activation even while the default JDBC client is present and
     * usable.
     */
    @Test
    void rejectsMissingNamedClientWithoutFallingBackToDefaultClient() {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:h2:mem:jdbc_missing_inventory_client;DB_CLOSE_DELAY=-1");
        poolConfig.setMaximumPoolSize(2);
        poolConfig.setConnectionTimeout(1_000);
        try (HikariDataSource dataSource = new HikariDataSource(poolConfig)) {
            JdbcClient setup = JdbcClient.builder().dataSource(dataSource).build();
            createSchema(setup);
            JdbcClientConfig defaultConfig = JdbcClient.builder()
                    .dataSource(dataSource)
                    .buildPrototype();
            ServiceRegistryManager manager = ServiceRegistryManager.create();
            GlobalServiceRegistry.registry(manager.registry());
            try {
                // Publish only the default client so the existing inventory repository
                // exercises a genuinely missing required named dependency.
                Services.set(JdbcClientConfig.class, defaultConfig);
                DefaultClientRepository defaultRepository = Services.get(DefaultClientRepository.class);
                assertThat(defaultRepository.insert(99, "default-still-present"), is(1L));

                ServiceRegistryException failure = assertThrows(ServiceRegistryException.class,
                                                                () -> Services.get(InventoryClientRepository.class));

                assertThat(failure.getMessage(), containsString("inventory"));
                assertThat(defaultRepository.find(99), is("default-still-present"));
            } finally {
                manager.shutdown();
            }
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
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

    private static String generatedSource(Class<?> repositoryType) throws Exception {
        Path testClasses = Path.of(JdbcGeneratedRepositoryClientConfigTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        Path generatedSource = testClasses.getParent()
                .resolve("generated-test-sources/test-annotations")
                .resolve(repositoryType.getName().replace('.', '/') + "__Jdbc.java");
        return Files.readString(generatedSource);
    }
}
