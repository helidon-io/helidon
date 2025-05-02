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
package io.helidon.tests.integration.transaction.data.mp;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataConfig;
import io.helidon.data.DataRegistry;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.sql.testing.TestConfigFactory;
import io.helidon.microprofile.cdi.HelidonContainer;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.SuiteResolver;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider/*, SuiteResolver*/ {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.0");
    static final String CONFIG_FILE = "application.yaml";
    private static final String PROVIDER_NODE = "data-source.0.provider.hikari";
    private static final String URL_NODE = PROVIDER_NODE + ".url";
    private static final int DB_PORT = 3306;


    @Container
    private final MySQLContainer<?> container;
    //private static HelidonContainer helidonContainer;
    private Config config;
    private DataRegistry data;
    private PokemonRepository pokemonRepository;

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
        LOGGER.log(System.Logger.Level.INFO, "Running MySqlSuite.beforeSuite()");
        // Database container
        container.start();
/*
        // Helidon Data config
        // Config must be updated to contain proper database URL and this change must be propagated
        // to Config service factory
        String oldUrl = config.get(URL_NODE).as(String.class).get();
        String url = SqlTestContainerConfig.replacePortInUrl(oldUrl, container.getMappedPort(DB_PORT));
        dbUrl = url;
        Map<String, String> updatedNodes = new HashMap<>(1);
        updatedNodes.put(URL_NODE, url);
        Config newConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        TestConfigFactory configProvider = Services.get(TestConfigFactory.class);
        List<Service.QualifiedInstance<io.helidon.common.config.Config>> services = configProvider.services();
        if (services.isEmpty()) {
            throw new IllegalStateException("TestConfigFactory service is not available");
        }
        ((TestConfigFactory.ConfigDelegate) services.getFirst().get()).config(newConfig);
        config = newConfig;
        // Helidon DataRegistry initialization
        data = DataRegistry.create(config.get("data"));
        pokemonRepository = data.repository(PokemonRepository.class);
        // Initialize database content
        pokemonRepository.run(Data::init);
*/
        // JPA CDI
        //helidonContainer = HelidonContainer.instance();
        //helidonContainer.start();
    }

    @AfterSuite
    public void afterSuite() {
        //helidonContainer.shutdown();
        //helidonContainer = null;
        LOGGER.log(System.Logger.Level.INFO, "Running MySqlSuite.afterSuite()");
        /*
        try {
            data.close();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, () -> String.format("Could not close Helidon Data: %s", e.getMessage()));
        }
        */
        container.stop();
    }

/*
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
*/

}
