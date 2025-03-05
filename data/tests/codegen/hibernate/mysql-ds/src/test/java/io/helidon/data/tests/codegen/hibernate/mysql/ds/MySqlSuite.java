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
package io.helidon.data.tests.codegen.hibernate.mysql.ds;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.api.DataConfig;
import io.helidon.data.api.DataRegistry;
import io.helidon.data.sql.common.SqlConfig;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.sql.testing.TestConfigFactory;
import io.helidon.data.tests.codegen.common.InitialData;
import io.helidon.data.tests.codegen.repository.PokemonRepository;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.integration.junit5.suite.AfterSuite;
import io.helidon.testing.integration.junit5.suite.BeforeSuite;
import io.helidon.testing.integration.junit5.suite.Suite;
import io.helidon.testing.integration.junit5.suite.SuiteResolver;
import io.helidon.testing.integration.junit5.suite.spi.SuiteProvider;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static org.junit.Assume.assumeTrue;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider, SuiteResolver {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.0");
    private static final String CONFIG_FILE = "application.yaml";
    private static final String PROVIDER_NODE = "data-source.0.provider.hikari";
    private static final String URL_NODE = PROVIDER_NODE + ".connection-string";
    private static final int DB_PORT = 3306;

    @Container
    private final MySQLContainer<?> container;
    private Config config;
    private DataRegistry data;
    private PokemonRepository pokemonRepository;

    public MySqlSuite() {
        this.config = Config.just(ConfigSources.classpath(CONFIG_FILE));
        this.container = new MySQLContainer<>(IMAGE);
        // Container setup is defined in data-source node
        Config poolConfig = config.get(PROVIDER_NODE);
        SqlTestContainerConfig.configureContainer(SqlConfig.builder()
                                                          .config(poolConfig)
                                                          .build(),
                                                  container);
    }

    @BeforeSuite
    public void beforeSuite() {
        container.start();
        // Config must be updated to contain proper database URL and this change must be propagated
        // to Config service factory
        String oldUrl = config.get(URL_NODE).as(String.class).get();
        String url = SqlTestContainerConfig.replacePortInUrl(oldUrl, container.getMappedPort(DB_PORT));
        Map<String, String> updatedNodes = new HashMap<>(1);
        updatedNodes.put(URL_NODE, url);
        Config newConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        Supplier<TestConfigFactory> configProvider = Registry.REGISTRY.supply(TestConfigFactory.class);
        List<Service.QualifiedInstance<io.helidon.common.config.Config>> services = configProvider.get().services();
        if (services.isEmpty()) {
            throw new IllegalStateException("TestConfigFactory service is not available");
        }
        ((TestConfigFactory.ConfigDelegate) services.getFirst().get()).config(newConfig);
        config = newConfig;
        data = DataRegistry.create(config.get("data"));
        pokemonRepository = data.repository(PokemonRepository.class);
        // Initialize database content
        pokemonRepository.run(InitialData::init);
    }

    @AfterSuite
    public void afterSuite() {
        try {
            data.close();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, () -> String.format("Could not close Helidon Data: %s", e.getMessage()));
        }
        container.stop();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return (type instanceof Class<?> cls)
                && (
                Config.class.isAssignableFrom(cls)
                        || DataConfig.class.isAssignableFrom(cls)
                        || DataRegistry.class.isAssignableFrom(cls)
                        || PokemonRepository.class.isAssignableFrom(cls));
    }

    @Override
    public Object resolveParameter(Type type) {
        if (type instanceof Class<?> cls) {
            if (Config.class.isAssignableFrom(cls)) {
                return config;
            } else if (DataConfig.class.isAssignableFrom(cls)) {
                return data.dataConfig();
            } else if (DataRegistry.class.isAssignableFrom(cls)) {
                return data;
            } else if (PokemonRepository.class.isAssignableFrom(cls)) {
                return pokemonRepository;
            }
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    // Lazy initialized static instances of ServiceRegistry
    private static final class Registry {
        private static final ServiceRegistryManager REGISTRY_MANAGER = ServiceRegistryManager.create();
        private static final ServiceRegistry REGISTRY = REGISTRY_MANAGER.registry();
        ;
    }

    @Suite(MySqlSuite.class)
    public static class TestApplication extends io.helidon.data.tests.codegen.common.TestApplication {
    }

    @Suite(MySqlSuite.class)
    public static class TestBasicRepository extends io.helidon.data.tests.codegen.common.TestBasicRepository {

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

    @Suite(MySqlSuite.class)
    public static class TestBasicRepositoryDelete extends io.helidon.data.tests.codegen.common.TestBasicRepositoryDelete {
    }

    @Suite(MySqlSuite.class)
    public static class TestCrudRepository extends io.helidon.data.tests.codegen.common.TestCrudRepository {
    }

    @Suite(MySqlSuite.class)
    public static class TestTransaction extends io.helidon.data.tests.codegen.common.TestTransaction {

        // 2nd level transaction support seems to be broken with Hibernate

        @Test
        @Override
        public void testAutomaticMandatory2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testAutomaticMandatory2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testAutomaticNever2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testAutomaticNever2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testAutomaticNew2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testAutomaticNew2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testAutomaticRequired2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testAutomaticRequired2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testAutomaticSupported2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testAutomaticSupported2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testAutomaticUnsupported2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testAutomaticUnsupported2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testManualMandatory2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testManualMandatory2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testManualNever2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testManualNever2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testManualNew2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testManualNew2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testManualRequired2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testManualRequired2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testManualSupported2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testManualSupported2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testManualUnsupported2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUnsupported2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testUserMandatory2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUserMandatory2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testUserNever2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUserNever2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testUserNew2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUserNew2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testUserRequired2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUserRequired2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testUserSupported2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUserSupported2ndLevel");
            assumeTrue(false);
        }

        @Test
        @Override
        public void testUserUnsupported2ndLevel() {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipped testUserUnsupported2ndLevel");
            assumeTrue(false);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TestQbmnProjection extends io.helidon.data.tests.codegen.common.TestQbmnProjection {
    }

    @Suite(MySqlSuite.class)
    public static class TestQbmnCriteria extends io.helidon.data.tests.codegen.common.TestQbmnCriteria {
    }

    @Suite(MySqlSuite.class)
    public static class TestQbmnCriteriaExtended extends io.helidon.data.tests.codegen.common.TestQbmnCriteriaExtended {
    }

    @Suite(MySqlSuite.class)
    public static class TestQbmnDml extends io.helidon.data.tests.codegen.common.TestQbmnDml {
    }

    @Suite(MySqlSuite.class)
    public static class TestQbmnOrder extends io.helidon.data.tests.codegen.common.TestQbmnOrder {
    }

    @Suite(MySqlSuite.class)
    public static class TestQueryByAnnotation extends io.helidon.data.tests.codegen.common.TestQueryByAnnotation {
    }

}
