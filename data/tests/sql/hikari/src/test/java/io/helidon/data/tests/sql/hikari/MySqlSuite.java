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
package io.helidon.data.tests.sql.hikari;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
import io.helidon.data.sql.testing.TestConfigFactory;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.suite.AfterSuite;
import io.helidon.testing.junit5.suite.BeforeSuite;
import io.helidon.testing.junit5.suite.SuiteResolver;
import io.helidon.testing.junit5.suite.spi.SuiteProvider;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL suite.
 */
public class MySqlSuite implements SuiteProvider, SuiteResolver {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());
    @Container
    private final MySQLContainer<?> container;
    private Config config;

    public MySqlSuite() {
        config = Config.just(ConfigSources.classpath("application.yaml"));
        container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
        Config dsConfig = config.get("data-source");
        if (dsConfig.exists()) {
            Config dsConfig1 = dsConfig.get("0");
            if (dsConfig1.exists()) {
                Config poolConfig = dsConfig1.get("provider.hikari");
                SqlTestContainerConfig.configureContainer(ConnectionConfig.create(poolConfig),
                                                          container);
            }
        }
    }

    @BeforeSuite
    public void beforeSuite() {
        container.start();
        String oldUrl = config.get("data-source.0.provider.hikari.url").as(String.class).get();
        String url = SqlTestContainerConfig.replacePortInUrl(oldUrl, container.getMappedPort(3306));
        Map<String, String> updatedNodes = new HashMap<>(1);
        updatedNodes.put("data-source.0.provider.hikari.url", url);
        Config newConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        Supplier<TestConfigFactory> configProvider = Registry.REGISTRY.supply(TestConfigFactory.class);
        List<Service.QualifiedInstance<io.helidon.common.config.Config>> services = configProvider.get().services();
        if (services.isEmpty()) {
            throw new IllegalStateException("TestConfigFactory service is not available");
        }
        ((TestConfigFactory.ConfigDelegate) services.getFirst().get()).config(newConfig);
        config = newConfig;
    }

    @AfterSuite
    public void afterSuite() {
        container.stop();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return (type instanceof Class<?>)
                && Config.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (Config.class.isAssignableFrom((Class<?>) type)) {
            return config;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    // Lazy initialized static instances of ServiceRegistry
    private static final class Registry {
        private static final ServiceRegistryManager REGISTRY_MANAGER = ServiceRegistryManager.create();
        private static final ServiceRegistry REGISTRY = REGISTRY_MANAGER.registry();
    }

}
