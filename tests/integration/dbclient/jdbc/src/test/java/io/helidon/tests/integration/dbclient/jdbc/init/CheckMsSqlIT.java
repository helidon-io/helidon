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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.jdbc.init.CheckIT.CONFIG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */
public class CheckMsSqlIT {
    
    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(CheckIT.class.getName());

    /** Timeout in seconds to wait for database to come up. */
    private static final int TIMEOUT = 60;

    /** Database connection. */
    private static Connection conn = null;

    /**
     * Wait until database starts up when its configuration node is available.
     */
    private static final class ConnectionCheck implements Consumer<Config> {

        private boolean connected;

        private ConnectionCheck() {
            connected = false;
        }

        @Override
        public void accept(Config config) {
            String url = config.get("sa-url").asString().get();
            String username = config.get("sa-user").asString().get();
            String password = config.get("sa-password").asString().get();
            long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
            while (true) {
                try {
                    conn = DriverManager.getConnection(url, username, password);
                    connected = true;
                    return;
                } catch (SQLException ex) {
                    LOGGER.info(() -> String.format("Connection check: %s", ex.getMessage()));
                    if (System.currentTimeMillis() > endTm) {
                        conn = null;
                        return;
                    }
                }
            }
        }

        private boolean connected() {
            return connected;
        }

    }

    /**
     * Create user and database and set permissions.
     */
    private static final class DbInit implements Consumer<Config> {

        @Override
        public void accept(Config config) {
            if (conn == null) {
                fail("Database connection is not available!");
            }
            String username = config.get("username").asString().get();
            String password = config.get("password").asString().get();
            String database = CheckIT.CONFIG.get("test.db-database").asString().get();
            try {
                Statement stmt = conn.createStatement();
                final int dbCount = stmt.executeUpdate(String.format("EXEC sp_configure 'CONTAINED DATABASE AUTHENTICATION', 1", database));
                LOGGER.log(Level.INFO, () -> String.format("Executed EXEC statement. %d records modified.", dbCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not configure database:", ex);
            } try {
                Statement stmt = conn.createStatement();
                final int dbCount = stmt.executeUpdate(String.format("RECONFIGURE", database));
                LOGGER.log(Level.INFO, () -> String.format("Executed RECONFIGURE statement. %d records modified.", dbCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not reconfigure database:", ex);
            } try {
                Statement stmt = conn.createStatement();
                final int dbCount = stmt.executeUpdate(String.format("CREATE DATABASE %s CONTAINMENT = PARTIAL", database));
                LOGGER.log(Level.INFO, () -> String.format("Executed CREATE DATABASE statement. %d records modified.", dbCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not create database:", ex);
            } try {
                Statement stmt = conn.createStatement();
                final int useCount = stmt.executeUpdate(String.format("USE %s", database));
                LOGGER.log(Level.INFO, () -> String.format("Executed USE statement. %d records modified.", useCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not use database:", ex);
            } try {
                Statement stmt = conn.createStatement();//"CREATE USER ? WITH PASSWORD = ?");
                final int userCount = stmt.executeUpdate(String.format("CREATE USER %s WITH PASSWORD = '%s'", username, password));
                LOGGER.log(Level.INFO, () -> String.format("Executed CREATE USER statement. %d records modified.", userCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not create database user:", ex);
            } try {
                Statement stmt = conn.createStatement();//"CREATE USER ? WITH PASSWORD = ?");
                final int userCount = stmt.executeUpdate(String.format("GRANT ALL TO %s", username));
                LOGGER.log(Level.INFO, () -> String.format("Executed GRANT statement. %d records modified.", userCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not grant database privilegs to user:", ex);
            } try {
                Statement stmt = conn.createStatement();//"CREATE USER ? WITH PASSWORD = ?");
                final int userCount = stmt.executeUpdate(String.format("GRANT CONTROL ON SCHEMA::dbo TO %s", username));
                LOGGER.log(Level.INFO, () -> String.format("Executed GRANT statement. %d records modified.", userCount));
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not grant database privilegs to user:", ex);
            }
        }

    }

    /**
     * Wait for database server to start.
     */
    private static void waitForStart() {
        ConnectionCheck check = new ConnectionCheck();
        CheckIT.CONFIG.get("test").ifExists(check);
        if (!check.connected()) {
            fail("Database startup failed!");
        }
    }

    /**
     * Initialize database user.
     */
    private static void initDb() {
        if (conn == null) {
            fail("Database connection is not available!");
        }
        DbInit init = new DbInit();
        CheckIT.CONFIG.get("db.connection").ifExists(init);
        
    }

    /**
     * Setup database for tests.
     * Wait for database to start and create user and database for tests.
     * Returns after ping query completed successfully or timeout passed.
     */
    @BeforeAll
    public static void setup() {
        waitForStart();
        initDb();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not close database connection:", ex);
            }
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
        CheckIT.ConnectionBuilder builder = new CheckIT.ConnectionBuilder();
        String ping = CONFIG.get("db.health-check.statement").asString().get();
        String typeStr = CONFIG.get("db.health-check.type").asString().get();
        boolean pingDml = typeStr != null && "dml".equals(typeStr.toLowerCase());
        CONFIG.get("db.connection").ifExists(builder);
        Connection conn = builder.createConnection();
        if (pingDml) {
            int result = conn.createStatement().executeUpdate(ping);
            assertThat(result, equalTo(0));
            LOGGER.info(() -> String.format("Command ping result: %d", result));
        } else {
            ResultSet rs = conn.createStatement().executeQuery(ping);
            rs.next();
            int result = rs.getInt(1);
            assertThat(result, equalTo(0));
            LOGGER.info(() -> String.format("Command ping result: %d", result));
        }
    }


}
