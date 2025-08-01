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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.sql.testing.TestContainerHandler;
import io.helidon.microprofile.testing.Configuration;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.Suite;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;
import io.helidon.tests.integration.transaction.data.mp.test.Data;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider {

    private final TestContainerHandler containerHandler;

    public MySqlSuite() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
        this.containerHandler = SqlTestContainerConfig.configureContainer(container,
                                                                          ConfigSources.classpath("application.yaml"));
    }

    @BeforeSuite
    public void beforeSuite() {
        this.containerHandler.startContainer();
        Config config = this.containerHandler.setConfig();

        ConfigProviderResolver providerResolver = ConfigProviderResolver.instance();
        org.eclipse.microprofile.config.Config mpConfig = providerResolver.getBuilder()
                .withSources(MpConfigSources.create(config))
                .build();

        providerResolver
                .registerConfig(mpConfig, null);

        PokemonRepository pokemonRepository = Services.get(PokemonRepository.class);
        pokemonRepository.run(Data::init);
    }

    @AfterSuite
    public void afterSuite() {
        containerHandler.stopContainer();
    }

    @HelidonTest
    @Configuration(useExisting = true)
    @Suite(MySqlSuite.class)
    @Testcontainers(disabledWithoutDocker = true)
    public static class TestTransaction extends io.helidon.tests.integration.transaction.data.mp.test.TestTransaction {
    }

}
