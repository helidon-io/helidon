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
import java.net.URI;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tests.integration.junit5.AfterSuite;
import io.helidon.tests.integration.junit5.BeforeSuite;
import io.helidon.tests.integration.junit5.ConfigUpdate;
import io.helidon.tests.integration.junit5.ConfigUtils;
import io.helidon.tests.integration.junit5.ContainerConfig;
import io.helidon.tests.integration.junit5.ContainerInfo;
import io.helidon.tests.integration.junit5.ContainerTest;
import io.helidon.tests.integration.junit5.DatabaseContainer;
import io.helidon.tests.integration.junit5.MySqlContainer;
import io.helidon.tests.integration.junit5.SetUpContainer;
import io.helidon.tests.integration.junit5.SuiteResolver;
import io.helidon.tests.integration.junit5.TestConfig;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

@TestConfig
@ContainerTest(provider = MySqlContainer.class, image = "mysql:8")
public class MySqlSuite implements SuiteProvider, SuiteResolver {

    static final String DB_NODE = "data";
    private static final int STARTUP_TIMEOUT = 60;
    private static final int CONNECTION_CHECK_TIMEOUT = 1;

    @SetUpContainer
    public void setupContainer(Config config, ContainerConfig.Builder builder) {
        // Setup MySQL container using configured values
        Config dbConfig = config.get(DB_NODE);
        Map<String, String> environment = new HashMap<>(3);
        dbConfig.get("username").as(String.class).ifPresent(value -> environment.put("MYSQL_USER", value));
        dbConfig.get("password").as(String.class).ifPresent(value -> environment.put("MYSQL_PASSWORD", value));
        dbConfig.get("connection-string").as(String.class).ifPresent(value -> {
            URI uri = ConfigUtils.uriFromDbUrl(value);
            environment.put("MYSQL_DATABASE", ConfigUtils.dbNameFromUri(uri));
        });
        builder.addEnvironment(environment);
    }

    @BeforeSuite
    public void beforeSuite(Config config, ConfigUpdate update, ContainerInfo info) {
        // Update Config with MySQL container port mapping
        Map<String, String> updatedNodes = new HashMap<>(1);
        config.get(DB_NODE + ".connection-string").as(String.class).ifPresent(value -> updatedNodes.put(
                DB_NODE + ".connection-string",
                DatabaseContainer.replacePortInUrl(
                        value,
                        info.portMappings().get(info.config().exposedPorts()[0]))));
        Config updatedConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        update.config(updatedConfig);
        // Wait for the database to start up
        InitUtils.waitForStart(
                () -> DriverManager.getConnection(updatedConfig.get(DB_NODE + ".connection-string").asString().get(),
                                                  updatedConfig.get(DB_NODE + ".username").asString().get(),
                                                  updatedConfig.get(DB_NODE + ".password").asString().get()),
                STARTUP_TIMEOUT,
                CONNECTION_CHECK_TIMEOUT);
    }

    @AfterSuite
    public void afterSuite() throws Exception {
    }

    @Override
    public boolean supportsParameter(Type type) {
        return false;
    }

    @Override
    public Object resolveParameter(Type type) {
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

}
