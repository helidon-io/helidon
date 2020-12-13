/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.jpa.appl;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

/**
 * Test JDBC API without JPA.
 */
@ApplicationScoped
public class JdbcApiIT {

    private static final Logger LOGGER = Logger.getLogger(JdbcApiIT.class.getName());

    /* Database connection. */
    private static Connection conn = null;

    /**
     * Initialize database connection.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult setup(TestResult result) {
        Properties config = new Properties();
        try (InputStream is = JdbcApiIT.class.getResourceAsStream("/META-INF/microprofile-config.properties")) {
            config.load(is);
        } catch (IOException ex) {
            LOGGER.severe(() -> String.format("Could not load configuration properties: %s", ex.getMessage()));
        }
        final String dbUser = config.getProperty("javax.sql.DataSource.test.dataSource.user");
        final String dbPassword = config.getProperty("javax.sql.DataSource.test.dataSource.password");
        final String dbUrl = config.getProperty("javax.sql.DataSource.test.dataSource.url");
        if (dbUser == null) {
            throw new IllegalStateException("Database user name was not set!");
        }
        if (dbPassword == null) {
            throw new IllegalStateException("Database user password was not set!");
        }
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL was not set!");
        }
        try {
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (SQLException ex) {
            LOGGER.info(() -> String.format("Could not open database connection: %s", ex.getMessage()));
            conn = null;
        }
        return result;
    }

    /**
     * Close database connection.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult destroy(TestResult result) {
        if (conn != null) {
            Utils.closeConnection(conn);
        }
        return result;
    }


    /**
     * Test simple ping query.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult ping(TestResult result) throws SQLException {
        if (conn == null) {
            return result.fail("No database connection is available!");
        }
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                result.assertTrue(rs.next(), "Is 1st result available?");
                Integer value = rs.getInt(1);
                result.assertNotNull(value);
                result.assertEquals(1, value);
            }
        } catch (SQLException e) {
            LOGGER.warning(() -> String.format("Simple ping query failed: ", e.getMessage()));
            result.throwed(e);
        }
        return result;
    }

}
