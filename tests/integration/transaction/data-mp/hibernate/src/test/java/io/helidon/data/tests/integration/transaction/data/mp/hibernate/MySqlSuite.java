/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.tests.integration.transaction.data.mp.hibernate;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.testing.ConfigUtils;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.microprofile.testing.AddConfigSource;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.Suite;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;
import io.helidon.tests.integration.transaction.data.mp.test.Data;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.0");
    private static final String CONFIG_FILE = "application.yaml";
    private static final String PROVIDER_NODE = "data-source.0.provider.hikari";
    private static final String CDI_URL_NODE="javax.sql.DataSource.cditest.dataSource.url";
    private static final int DB_PORT = 3306;

    private static String dbUrl = null;

    private static String urlNode(int i) {
        return "data-source." + i + ".provider.hikari.url";
    }

    @Container
    private final MySQLContainer<?> container;
    private Config config;

    public MySqlSuite() {
        this.config = Config.just(ConfigSources.classpath(CONFIG_FILE));
        this.container = new MySQLContainer<>(IMAGE);
        // Container setup is defined in data-source node
        Config poolConfig = config.get(PROVIDER_NODE);
        SqlTestContainerConfig.configureContainer(ConnectionConfig.create(poolConfig),
                                                  container);
    }

    @BeforeSuite
    public void beforeSuite() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running MySqlSuite.beforeSuite()");
        // Database container
        container.start();
        dbUrl = container.getJdbcUrl();
        String oldUrl = config.get(urlNode(0)).as(String.class).get();
        String url = ConfigUtils.replacePortInUrl(oldUrl, container.getMappedPort(DB_PORT));
        System.setProperty(urlNode(0), url);
    }

    @AfterSuite
    public void afterSuite() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running MySqlSuite.afterSuite()");
        container.stop();
    }

    // Build MP config with updated CDI persistence unit DataSource URL
    static Map<String, String> cdiUrlConfig() {
        return Map.of(CDI_URL_NODE, dbUrl);
    }

    @HelidonTest
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTransaction extends io.helidon.tests.integration.transaction.data.mp.test.TestTransaction {

        // Hibernate deletes data on cdi-test persistence unit initialization
        // so DB init was moved past this
        @BeforeAll
        static void setup() {
            PokemonRepository pokemonRepository = Services.get(PokemonRepository.class);
            pokemonRepository.run(Data::init);
        }

        @AddConfigSource
        static ConfigSource config() {
            return MpConfigSources.create(MySqlSuite.cdiUrlConfig());
        }

    }

}
