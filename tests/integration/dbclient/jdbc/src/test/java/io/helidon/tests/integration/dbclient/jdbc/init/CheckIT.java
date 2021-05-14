/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.jdbc.init;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.tests.integration.dbclient.common.ConfigIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */

public class CheckIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(CheckIT.class.getName());

    // Test configuration
    public static final Config CONFIG = ConfigJDBC.CONFIG;

    private static Connection conn = null;

    /**
     * Setup database for tests.
     * Wait for database to start. Returns after ping query completed successfully or timeout passed.
     */
    @BeforeAll
    @SuppressWarnings("UseSpecificCatch")
    public static void setup() {
        try {
            final String dbUser = ConfigJDBC.getCheckUser();
            final String dbPassword = ConfigJDBC.getCheckPassword();
            try {
                ConfigJDBC.ConnectionBuilder builder = new ConfigJDBC.ConnectionBuilder();
                builder.accept(dbUser, dbPassword, ConfigJDBC.DB_URL);
                conn = builder.createConnection();
                final String dbName = conn.getMetaData().getDatabaseProductName();
                ConfigIT.initDbType(dbName);
                LOGGER.fine(() -> String.format("Database %s started", dbName));
            } catch (SQLException ex) {
                LOGGER.warning(() -> String.format("Could not connect to database: %s", ex.getMessage()));
            }
            switch (ConfigIT.dbType) {
                case ORACLE:
                    initOraDB(ConfigJDBC.DB_USER, ConfigJDBC.DB_PASSWORD);
                    break;
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("Database setup failed: %s", t.getMessage()));
            throw t;
        }
    }

    /**
     * Simple test to verify that DML query execution works.
     * Used before running database schema initialization.
     *
     * @throws SQLException when database query failed
     */
    @Test
    public void testDmlStatementExecution() throws SQLException {
        String ping = CONFIG.get("db.health-check.statement").asString().get();
        String typeStr = CONFIG.get("db.health-check.type").asString().get();
        boolean pingDml = typeStr != null && "dml".equals(typeStr.toLowerCase());
        if (pingDml) {
            int result = conn.createStatement().executeUpdate(ping);
            assertThat(result, equalTo(0));
            LOGGER.finest(() -> String.format("Command ping result: %d", result));
        } else {
            ResultSet rs = conn.createStatement().executeQuery(ping);
            rs.next();
            int result = rs.getInt(1);
            assertThat(result, equalTo(0));
            LOGGER.finest(() -> String.format("Command ping result: %d", result));
        }
    }

    /**
     * Initialize Oracle database.
     * Database name is retrieved from connection URL.
     *
     * @param dbUser MsSQL database connection user name
     * @param dbPassword MsSQL database connection user password
     */
    private static void initOraDB(final String dbUser, final String dbPassword) {
        LOGGER.fine(() -> "Oracle database initialization");
        final String crUsr = String.format("CREATE USER %s IDENTIFIED BY %s", dbUser, dbPassword);
        try {
            final Statement stmt = conn.createStatement();
            final int dbCount = stmt.executeUpdate(crUsr);
            LOGGER.finer(() -> String.format("Executed EXEC statement. %d records modified.", dbCount));
        } catch (SQLException ex) {
            LOGGER.warning(() -> String.format("Failed statement: %s", crUsr));
            LOGGER.log(Level.WARNING, "Could not create database user:", ex);
        }
        execStatement(String.format("GRANT CONNECT %s", dbUser));
        execStatement(String.format("GRANT RESOURCE TO %s", dbUser));
        execStatement(String.format("GRANT UNLIMITED TABLESPACE TO %s", dbUser));
    }

    private static void execStatement(String stmtStr) {
        try {
            Statement stmt = conn.createStatement();
            final int dbCount = stmt.executeUpdate(stmtStr);
            LOGGER.finer(() -> String.format("Executed statement. %d records modified.", dbCount));
        } catch (SQLException ex) {
            LOGGER.warning(() -> String.format("Failed statement: %s", stmtStr));
            LOGGER.log(Level.WARNING, "Could not execute statement: ", ex);
        }
    }


}
