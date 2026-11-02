/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.data.jdbc.database.mysql;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

import javax.sql.DataSource;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shares a suite-owned MySQL container while keeping each regression's schema isolated.
 */
public final class MySqlTestDatabase implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MySqlTestDatabase.class);
    private static final String PASSWORD = "jdbc-test-password";
    private static volatile MySQLContainer<?> container;

    /**
     * Creates a data source for an isolated regression schema.
     *
     * @param databaseName schema name
     * @return data source
     * @throws NullPointerException if databaseName is null
     */
    public static DataSource dataSource(String databaseName) {
        Objects.requireNonNull(databaseName, "The database name must not be null.");
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(jdbcUrl(databaseName));
        dataSource.setUser(username());
        dataSource.setPassword(password());
        return dataSource;
    }

    /**
     * Creates the regression schema and returns its JDBC URL.
     *
     * @param databaseName schema name
     * @return JDBC URL
     * @throws NullPointerException if databaseName is null
     */
    public static String jdbcUrl(String databaseName) {
        Objects.requireNonNull(databaseName, "The database name must not be null.");
        if (!databaseName.matches("[a-zA-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Invalid test database name: " + databaseName);
        }
        if (container == null) {
            throw new IllegalStateException("MySqlTestDatabase must be registered as a JUnit extension.");
        }
        String connectionUrl = "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(3306) + "/";
        String parameters = "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"
                + "&connectTimeout=10000&socketTimeout=10000";
        try (var connection = DriverManager.getConnection(connectionUrl + container.getDatabaseName() + parameters,
                                                          username(), password());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "`");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create test database " + databaseName, e);
        }
        return connectionUrl + databaseName + parameters;
    }

    /**
     * Returns the regression database username.
     *
     * @return username
     */
    public static String username() {
        return "root";
    }

    /**
     * Returns the regression database password.
     *
     * @return password
     */
    public static String password() {
        return PASSWORD;
    }

    /**
     * Starts the shared database before the regression suite.
     *
     * @param context test context
     * @throws NullPointerException if context is null
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        Objects.requireNonNull(context, "The test context must not be null.");
        context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(Database.class, _ -> new Database(), Database.class);
    }

    private static final class Database implements ExtensionContext.Store.CloseableResource {

        private final MySQLContainer<?> database;

        private Database() {
            database = new MySQLContainer<>(DockerImageName.parse("container-registry.oracle.com/mysql/community-server:9.7.3")
                                                   .asCompatibleSubstituteFor("mysql"))
                    .withUsername(username())
                    .withPassword(password())
                    // OCR MySQL restricts root to localhost unless remote access is explicitly enabled.
                    .withEnv("MYSQL_ROOT_HOST", "%")
                    .withStartupTimeout(Duration.ofMinutes(3));
            try {
                database.start();
            } catch (RuntimeException | Error failure) {
                try {
                    database.stop();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            container = database;
        }

        @Override
        public void close() {
            try {
                database.stop();
            } finally {
                container = null;
            }
        }
    }
}
