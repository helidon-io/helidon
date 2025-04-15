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

import java.lang.reflect.Method;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.data.DataConfig;
import io.helidon.data.ProviderConfig;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.common.SqlConfig;

import org.testcontainers.containers.JdbcDatabaseContainer;

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
     * @param config    config root node
     * @param container container to configure (before it is started)
     */
    public static void configureContainer(Config config, JdbcDatabaseContainer<?> container) {
        config.get("data")
                .map(SqlTestContainerConfig::createDataConfig)
                .map(DataConfig::provider)
                .filter(provider -> provider instanceof SqlConfig)
                .map(SqlConfig.class::cast)
                .flatMap(SqlConfig::connection)
                .ifPresent(connectionConfig -> configureContainer(connectionConfig, container));
    }

    /**
     * Configure the test container from the {@link SqlConfig}.
     *
     * @param connectionConfig SQL specific configuration
     * @param container        container to configure (before it is started)
     */
    public static void configureContainer(ConnectionConfig connectionConfig, JdbcDatabaseContainer<?> container) {

        connectionConfig.username().ifPresent(container::withUsername);
        connectionConfig.password()
                .map(String::new)
                .ifPresent(container::withPassword);
        Optional.of(connectionConfig.url())
                .map(ConfigUtils::uriFromDbUrl)
                .map(ConfigUtils::dbNameFromUri)
                .ifPresent(container::withDatabaseName);
    }

    /**
     * Create a data registry from the provided configuration (expecting same node as in
     * {@link #configureContainer(io.helidon.common.config.Config, org.testcontainers.containers.JdbcDatabaseContainer)}) and
     * started
     * container. The data registry will use the mapped port, the username and the password the container was started
     * with.
     *
     * @param config    configuration to use to set up the data registry
     * @param container container to read runtime data from (must be started)
     * @param port      port that we look for as a mapped port (different depending on database)
     * @return a data registry initialized with the actual values from the running container
     */
    public static DataConfig configureDataRegistry(Config config, JdbcDatabaseContainer<?> container, int port) {
        if (!container.isRunning()) {
            throw new IllegalStateException("The database container must be running");
        }

        int mappedPort = container.getMappedPort(port);
        String username = container.getUsername();
        String password = container.getPassword();

        DataConfig dataConfig = createDataConfig(config.get("data"));

        var targetConfigBuilder = DataConfig.builder()
                .name(dataConfig.name());
        SqlConfig originalConfig = (SqlConfig) dataConfig.provider();
        SqlConfig.BuilderBase<?, ?> targetSqlConfigBuilder = targetConfigBuilder(originalConfig);

        if (originalConfig.connection().isPresent()) {
            targetSqlConfigBuilder.connection(connection -> connection.username(username)
                    .password(password)
                    .update(it -> originalConfig.connection()
                            .map(ConnectionConfig::url)
                            .map(url -> replacePortInUrl(url, mappedPort))
                            .ifPresent(it::url)));

            targetConfigBuilder
                    .provider(invokeBuild(targetSqlConfigBuilder));
        }

        return targetConfigBuilder.build();
    }

    /**
     * Replace port value in provided URL.
     *
     * @param url  source URL
     * @param port new port value in returned URL
     * @return source url with port replaced by {@code port} value.
     */
    public static String replacePortInUrl(String url, int port) {
        int begin = indexOfHostSeparator(url);
        if (begin >= 0) {
            int end = url.indexOf('/', begin + 3);
            int portBeg = url.indexOf(':', begin + 3);
            // Found port position in URL
            if (end > 0 && portBeg < end) {
                String frontPart = url.substring(0, portBeg + 1);
                String endPart = url.substring(end);
                return frontPart + port + endPart;
            } else {
                throw new IllegalStateException(
                        String.format("URL %s does not contain host and port part \"://host:port/\"", url));
            }
        } else {
            throw new IllegalStateException(
                    String.format("Could not find host separator \"://\" in URL %s", url));
        }
    }

    // Find separator before host in database URL
    // Regular separator is "://", bur Oracle DB has an exception and uses ":@"
    private static int indexOfHostSeparator(String src) {
        // First check DB type
        int jdbcSep = src.indexOf(':');
        String scheme = src.substring(0, jdbcSep);
        if (!"jdbc".equals(scheme)) {
            throw new IllegalArgumentException(
                    String.format("Database JDBC url shall start with \"jdbc:\" prefix, but URC is %s", src));
        }
        if (src.length() > jdbcSep + 2) {
            int typeSep = src.indexOf(':', jdbcSep + 1);
            String dbType = src.substring(jdbcSep + 1, typeSep);
            // Keeping switch here to simplify future extension
            return "oracle".equals(dbType)
                    ? src.indexOf(":@")
                    : src.indexOf("://");
        } else {
            throw new IllegalArgumentException("Database JDBC url has nothing after \"jdbc:\" prefix");
        }
    }

    private static DataConfig createDataConfig(Config config) {
        if (config.isList()) {
            return DataConfig.create(config.get("0"));
        }
        return DataConfig.create(config);
    }

    private static ProviderConfig invokeBuild(SqlConfig.BuilderBase<?, ?> builder) {
        try {
            return (ProviderConfig) builder.getClass()
                    .getMethod("build")
                    .invoke(builder);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke build method on builder: " + builder, e);
        }
    }

    private static SqlConfig.BuilderBase<?, ?> targetConfigBuilder(SqlConfig originalConfig) {
        // we use reflection to create the correct instance; the config MUST be Helidon generated prototype (from blueprint)
        Class<?> implClass = originalConfig.getClass();
        try {
            // the first (and only) implemented interface should be the prototype
            Class<?> prototypeInterface = implClass.getInterfaces()[0];
            Method prototypeBuilderMethod = prototypeInterface.getMethod("builder");
            var builder = prototypeBuilderMethod.invoke(null);
            builder.getClass().getMethod("from", prototypeInterface)
                    .invoke(builder, originalConfig);
            return (SqlConfig.BuilderBase<?, ?>) builder;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot find prototype builder method for implementation: " + implClass.getName(), e);
        }
    }

}
