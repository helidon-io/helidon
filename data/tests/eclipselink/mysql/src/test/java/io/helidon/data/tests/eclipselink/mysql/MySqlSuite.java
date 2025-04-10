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
package io.helidon.data.tests.eclipselink.mysql;

import java.lang.System.Logger.Level;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataRegistry;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.tests.common.InitialData;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.Suite;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());
    @Container
    private final MySQLContainer<?> container;
    private final Config config;
    private DataRegistry data;
    private PokemonRepository pokemonRepository;

    public MySqlSuite() {
        this.config = Config.just(ConfigSources.classpath("application.yaml"));
        this.container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
        SqlTestContainerConfig.configureContainer(config, container);
    }

    @BeforeSuite
    public void beforeSuite() {
        container.start();
        // Update URL in config with exposed port
        System.setProperty("sql.mysql.port", String.valueOf(container.getMappedPort(3306)));
        try {
            data = Services.get(DataRegistry.class);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        pokemonRepository = data.repository(PokemonRepository.class);
        // Initialize database content
        pokemonRepository.run(InitialData::init);
    }

    @AfterSuite
    public void afterSuite() {
        try {
            data.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, () -> String.format("Could not close Helidon Data: %s", e.getMessage()));
        }
        container.stop();
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestApplication extends io.helidon.data.tests.common.TestApplication {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestBasicRepository extends io.helidon.data.tests.common.TestBasicRepository {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestBasicRepositoryDelete extends io.helidon.data.tests.common.TestBasicRepositoryDelete {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestCrudRepository extends io.helidon.data.tests.common.TestCrudRepository {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnProjection extends io.helidon.data.tests.common.TestQbmnProjection {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnCriteria extends io.helidon.data.tests.common.TestQbmnCriteria {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnCriteriaExtended extends io.helidon.data.tests.common.TestQbmnCriteriaExtended {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnDml extends io.helidon.data.tests.common.TestQbmnDml {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnOrder extends io.helidon.data.tests.common.TestQbmnOrder {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQueryByAnnotation extends io.helidon.data.tests.common.TestQueryByAnnotation {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTxMethods extends io.helidon.data.tests.common.TestTxMethods {
    }

    @Testing.Test
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTxAnnotations extends io.helidon.data.tests.common.TestTxAnnotations {
    }

}
