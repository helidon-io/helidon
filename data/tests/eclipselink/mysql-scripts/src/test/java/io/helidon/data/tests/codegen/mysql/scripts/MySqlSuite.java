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
package io.helidon.data.tests.codegen.mysql.scripts;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataConfig;
import io.helidon.data.sql.testing.ConfigUtils;
import io.helidon.data.sql.testing.SqlTestContainerConfig;
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

    private static final String URL_NODE = "data.provider.jakarta.connection.url";
    private static final int DB_PORT = 3306;
    @Container
    private final MySQLContainer<?> container;
    private Config config;
    private DataConfig dataConfig;

    public MySqlSuite() {
        this.config = Config.just(ConfigSources.classpath("application.yaml"));
        this.container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
        this.dataConfig = null;
        SqlTestContainerConfig.configureContainer(config, container);
    }

    @BeforeSuite
    public void beforeSuite() {
        container.start();
        // Update URL in config with exposed port
        String url = ConfigUtils.replacePortInUrl(config.get(URL_NODE)
                .as(String.class).get(), container.getMappedPort(DB_PORT));
        Map<String, String> updatedNodes = new HashMap<>(1);
        updatedNodes.put(URL_NODE, url);
        config = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        dataConfig = DataConfig.create(config.get("data"));
    }

    @AfterSuite
    public void afterSuite() {
        container.stop();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return (type instanceof Class<?> cls)
                && (Config.class.isAssignableFrom(cls)
                        || DataConfig.class.isAssignableFrom(cls));
    }

    @Override
    public Object resolveParameter(Type type) {
        if (type instanceof Class<?> cls) {
            if (Config.class.isAssignableFrom(cls)) {
                return config;
            } else if (DataConfig.class.isAssignableFrom(cls)) {
                return dataConfig;
            }
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

}
