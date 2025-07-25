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

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * This is an internal object needed to reconfigure the environment once a container is started.
 */
public class TestContainerHandler {

    private final Supplier<? extends ConfigSource> configSource;
    private final String username;
    private final String password;
    private final String url;
    private final GenericContainer<?> container;
    private final int originalPort;

    TestContainerHandler(Supplier<? extends ConfigSource> configSource,
                         String username,
                         String password,
                         String url,
                         GenericContainer<?> container) {
        this.configSource = configSource;
        this.username = username;
        this.password = password;
        this.url = url;
        this.container = container;
        this.originalPort = ConfigUtils.portFromDbUrl(url);
    }

    /**
     * Start the container, and return the mapped port.
     *
     * @return container port that was originally configured in the URI, which must be the default port of that database
     */
    public int startContainer() {
        container.start();
        return container.getMappedPort(originalPort);
    }

    /**
     * Set the new configuration with mapped port.
     * Uses the original configuration that was used to configure the container.
     *
     * @return the new config instance
     */
    public Config setConfig() {
        Config newConfig = config();
        TestConfigFactory.config(newConfig);
        return newConfig;
    }

    /**
     * Get the current correct config.
     * If the container is started, the config will contain updated port for the database URL.
     *
     * @return updated config
     */
    public Config config() {
        if (container.isRunning()) {
            String newUrl = ConfigUtils.replacePortInUrl(url, container.getMappedPort(originalPort));
            ConfigSource mySource = ConfigSources.create(Map.of("test.database.url", newUrl))
                    .name("test-containers-updated-values")
                    .build();
            return Config.just(mySource, this.configSource);
        } else {
            return Config.just(this.configSource);
        }
    }

    /**
     * The port as configured in the database URL before the container is started.
     * This must be the default port of the database that is used, as it is used to obtain the mapped port.
     *
     * @return original DB port
     */
    public int originalPort() {
        return this.originalPort;
    }

    /**
     * Stop the container.
     */
    public void stopContainer() {
        container.stop();
    }

    void configureContainer() {
        if (container instanceof JdbcDatabaseContainer<?> dbContainer) {
            dbContainer.withUsername(username)
                    .withPassword(password);

            Optional.of(url)
                    .map(ConfigUtils::uriFromDbUrl)
                    .map(ConfigUtils::dbNameFromUri)
                    .ifPresent(dbContainer::withDatabaseName);
        }

    }
}
