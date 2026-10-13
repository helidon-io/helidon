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
package io.helidon.data.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Shares a suite-owned MySQL container while keeping each regression's schema isolated.
 */
final class MySqlTestDatabase implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MySqlTestDatabase.class);
    private static final String PASSWORD = "jdbc-test-password";
    private static volatile MySQLContainer<?> container;

    static DataSource dataSource(String databaseName) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(jdbcUrl(databaseName));
        dataSource.setUser(username());
        dataSource.setPassword(password());
        return dataSource;
    }

    static String jdbcUrl(String databaseName) {
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

    static String username() {
        return "root";
    }

    static String password() {
        return PASSWORD;
    }

    /**
     * Reads server auto-commit state for tests that change it using SQL, which Connector/J's cached flag does not reflect.
     */
    static Connection serverAutoCommit(Connection delegate) throws SQLException {
        Connection connection = mock(Connection.class, delegatesTo(delegate));
        doAnswer(_ -> {
            try (var statement = delegate.createStatement();
                 var resultSet = statement.executeQuery("SELECT @@autocommit")) {
                if (!resultSet.next()) {
                    throw new SQLException("The server did not report its auto-commit state.");
                }
                return resultSet.getBoolean(1);
            }
        }).when(connection).getAutoCommit();
        return connection;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
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
