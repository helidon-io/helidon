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
package io.helidon.data.tests.hibernate.mysql;

import java.lang.System.Logger.Level;
import java.lang.reflect.Type;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataConfig;
import io.helidon.data.DataRegistry;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.tests.common.InitialData;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.Suite;
import io.helidon.testing.junit5.suite.SuiteResolver;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.Assume.assumeTrue;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider, SuiteResolver {

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
        data = DataRegistry.create(SqlTestContainerConfig.configureDataRegistry(config, container, 3306));
        pokemonRepository = data.repository(PokemonRepository.class);
        // Initialize database content
        try {
            pokemonRepository.run(InitialData::init);
            pokemonRepository.run(InitialData::verify);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, () -> String.format("Data initialization failed: %s", e.getMessage()));
            throw e;
        }
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

    @Override
    public boolean supportsParameter(Type type) {
        if (!(type instanceof Class<?> clazz)) {
            return false;
        }
        return Config.class.isAssignableFrom(clazz)
                || DataConfig.class.isAssignableFrom(clazz)
                || DataRegistry.class.isAssignableFrom(clazz)
                || PokemonRepository.class.isAssignableFrom(clazz);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (!(type instanceof Class<?> clazz)) {
            return false;
        }

        if (Config.class.isAssignableFrom(clazz)) {
            return config;
        } else if (DataConfig.class.isAssignableFrom(clazz)) {
            return data.dataConfig();
        } else if (DataRegistry.class.isAssignableFrom(clazz)) {
            return data;
        } else if (PokemonRepository.class.isAssignableFrom(clazz)) {
            return pokemonRepository;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestApplication extends io.helidon.data.tests.common.TestApplication {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestBasicRepository extends io.helidon.data.tests.common.TestBasicRepository {

        @Test
        @Override
        public void testSave() {
            LOGGER.log(Level.DEBUG, "Skipped testSave");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testSaveAll() {
            LOGGER.log(Level.DEBUG, "Skipped testSaveAll");
            assumeTrue(false);
        }

    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestBasicRepositoryDelete extends io.helidon.data.tests.common.TestBasicRepositoryDelete {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestCrudRepository extends io.helidon.data.tests.common.TestCrudRepository {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnProjection extends io.helidon.data.tests.common.TestQbmnProjection {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnCriteria extends io.helidon.data.tests.common.TestQbmnCriteria {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnCriteriaExtended extends io.helidon.data.tests.common.TestQbmnCriteriaExtended {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnDml extends io.helidon.data.tests.common.TestQbmnDml {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnOrder extends io.helidon.data.tests.common.TestQbmnOrder {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQueryByAnnotation extends io.helidon.data.tests.common.TestQueryByAnnotation {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTxMethods extends io.helidon.data.tests.common.TestTxMethods {
    }

    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTxAnnotations extends io.helidon.data.tests.common.TestTxAnnotations {
    }

}
