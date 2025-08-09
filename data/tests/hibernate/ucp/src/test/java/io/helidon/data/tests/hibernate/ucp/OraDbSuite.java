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
package io.helidon.data.tests.hibernate.ucp;

import java.time.Duration;

import io.helidon.config.ConfigSources;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.sql.testing.TestContainerHandler;
import io.helidon.data.tests.common.InitialData;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.suite.TestSuite;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Oracle DB suite.
 */
public class OraDbSuite implements SuiteProvider {
    private static final System.Logger LOGGER = System.getLogger(OraDbSuite.class.getName());
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "container-registry.oracle.com/database/express");

    private final TestContainerHandler containerHandler;

    public OraDbSuite() {
        GenericContainer<?> container = new GenericContainer<>(IMAGE);
        this.containerHandler = SqlTestContainerConfig.configureContainer(container,
                                                                          ConfigSources.classpath("application.yaml"));

        this.containerHandler.config()
                .get("test.database.password")
                .asString()
                .ifPresent(password -> container.withEnv("ORACLE_PWD", password));

        container.withExposedPorts(this.containerHandler.originalPort())
                        .waitingFor(Wait.forHealthcheck()
                                            .withStartupTimeout(Duration.ofMinutes(5)));
            }

    @TestSuite.BeforeSuite
    public void beforeSuite() {
        this.containerHandler.startContainer();
        this.containerHandler.setConfig();

        PokemonRepository pokemonRepository = Services.get(PokemonRepository.class);
        // Initialize database content
        pokemonRepository.run(InitialData::init);
    }

    @TestSuite.AfterSuite
    public void afterSuite() {
        containerHandler.stopContainer();
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestApplication extends io.helidon.data.tests.common.TestApplication {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestBasicRepository extends io.helidon.data.tests.common.TestBasicRepository {

        @Test
        @Override
        public void testSave() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testSave");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testSaveAll() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testSaveAll");
            assumeTrue(false);
        }

    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestBasicRepositoryDelete extends io.helidon.data.tests.common.TestBasicRepositoryDelete {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestCrudRepository extends io.helidon.data.tests.common.TestCrudRepository {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnProjection extends io.helidon.data.tests.common.TestQbmnProjection {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnCriteria extends io.helidon.data.tests.common.TestQbmnCriteria {

        // Tests broken on Oracle DB

        @Test
        @Override
        public void testDynamicFindByNameLike() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testDynamicFindByNameLike");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testDynamicFindByNameNotLike() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testDynamicFindByNameNotLike");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testFindByNameLike() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testFindByNameLike");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testFindByNameNotLike() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testFindByNameNotLike");
            assumeTrue(false);
        }

    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnCriteriaExtended extends io.helidon.data.tests.common.TestQbmnCriteriaExtended {

        // Tests broken on Oracle DB

        @Test
        @Override
        public void testFindByTrainerFalse() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testFindByTrainerFalse");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testFindByTrainerNotTrue() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testFindByTrainerNotTrue");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testFindByTrainerNull() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testFindByTrainerNull");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testFindByTypeEmpty() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testFindByTypeEmpty");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testDynamicFindByTrainerFalse() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testDynamicFindByTrainerFalse");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testDynamicFindByTrainerNotTrue() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testDynamicFindByTrainerNotTrue");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testDynamicFindByTrainerNull() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testDynamicFindByTrainerNull");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testDynamicFindByTypeEmpty() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testDynamicFindByTypeEmpty");
            assumeTrue(false);
        }

    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnDml extends io.helidon.data.tests.common.TestQbmnDml {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQbmnOrder extends io.helidon.data.tests.common.TestQbmnOrder {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestQueryByAnnotation extends io.helidon.data.tests.common.TestQueryByAnnotation {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTxMethods extends io.helidon.data.tests.common.TestTxMethods {
    }

    @TestSuite.Suite(OraDbSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTxAnnotations extends io.helidon.data.tests.common.TestTxAnnotations {
    }

}
