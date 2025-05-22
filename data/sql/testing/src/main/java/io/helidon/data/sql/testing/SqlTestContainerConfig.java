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

package io.helidon.data.sql.testing;

import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigSource;

import org.testcontainers.containers.GenericContainer;

/**
 * Support for testing SQL based Helidon Data with Test Containers.
 */
public final class SqlTestContainerConfig {
    private SqlTestContainerConfig() {
    }

    /**
     * Configure the test container from the config.
     * We expect node {@code data} to be present under the root, with a configuration, or with a list of configuration.
     * This method will use the first configuration to set up the username, password and database name if discovered in config.
     *
     * @param container    container to configure (before it is started)
     * @param configSource config source with database configuration (this must be the only config source in test)
     */
    public static TestContainerHandler configureContainer(GenericContainer<?> container,
                                                          Supplier<? extends ConfigSource> configSource) {
        Config testConfig = Config.just(configSource).get("test.database");

        String username = testConfig.get("username").asString().orElse("");
        String password = testConfig.get("password").asString().orElse("");
        String url = null;
        try {
            url = testConfig.get("url").asString().get();
        } catch (MissingValueException e) {
            throw new IllegalStateException("Configuration key test.database.url is required for test container based tests",
                                            e);
        }

        TestContainerHandler testConnectionInfo = new TestContainerHandler(configSource, username, password, url, container);

        testConnectionInfo.configureContainer();

        return testConnectionInfo;
    }

}
