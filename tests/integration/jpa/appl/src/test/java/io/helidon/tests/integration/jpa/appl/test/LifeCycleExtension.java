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
package io.helidon.tests.integration.jpa.appl.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.stream.JsonParsingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.LogConfig;
import io.helidon.tests.integration.jpa.appl.Utils;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * Test Life Cycle.
 * Contains setup and cleanup methods for the tests.
 */
public class LifeCycleExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

   private static enum DbType {

        DEFAULT,
        MSSQL;

        /**
         * Get database type based on provided URL.
         *
         * @param dbUrl database URL to check
         * @return database type retrieved from URL
         */
        private static DbType get(final String dbUrl) {
            if (dbUrl == null) {
                throw new IllegalStateException("Database URL is null!");
            }
            if (dbUrl.startsWith("jdbc:sqlserver")) {
                return MSSQL;
            }
            return DEFAULT;
        }

    }

    private static final Logger LOGGER = Logger.getLogger(LifeCycleExtension.class.getName());

    private static final String STORE_KEY = LifeCycleExtension.class.getName();

    /* Startup timeout in seconds for both database and web server. */
    private static final int TIMEOUT = 60;

    /* Thread sleep time in miliseconds while waiting for database or appserver to come up. */
    private static final int SLEEP_MILIS = 250;

    private static final Client CLIENT = ClientBuilder.newClient();

    private static final WebTarget TARGET = CLIENT.target("http://localhost:7001/test");

    private static DbType DB_TYPE = DbType.DEFAULT;

    /**
     * Test setup.
     *
     * @param ec current extension context
     * @throws Exception when test setup fails
     */
    @Override
    public void beforeAll(ExtensionContext ec) throws Exception {
        final Object resource = ec.getRoot().getStore(GLOBAL).get(STORE_KEY);
        if (resource == null) {
            LogConfig.configureRuntime();
            LOGGER.finest("Running beforeAll lifecycle method for the first time, invoking setup()");
            ec.getRoot().getStore(GLOBAL).put(STORE_KEY, this);
            setup();
        } else {
            LOGGER.finest("Running beforeAll lifecycle method next time, skipping setup()");
        }
    }

    /**
     * Setup JPA application tests.
     */
    private void setup() {
        LOGGER.fine("Running JPA application test setup()");
       final String dbUrl = System.getProperty("db.url");
        String dbUser;
        String dbPassword;
        DB_TYPE = DbType.get(dbUrl);
        switch (DB_TYPE) {
            case MSSQL:
                dbPassword = System.getProperty("db.sa.password");
                dbUser = "sa";
                waitForDatabase(saUrlOfMsSQL(dbUrl), dbUser, dbPassword);
                break;
            default:
                dbUser = System.getProperty("db.user");
                dbPassword = System.getProperty("db.password");
                waitForDatabase(dbUrl, dbUser, dbPassword);
        }
        waitForServer();
        switch (DB_TYPE) {
            case MSSQL:
                final String dbSaPassword = dbPassword;
                dbUser = System.getProperty("db.user");
                dbPassword = System.getProperty("db.password");
                initMsSQL(dbUrl, dbUser, dbPassword, dbSaPassword);
                break;
        }
        ClientUtils.callJdbcTest("/setup");
        ClientUtils.callJdbcTest("/test/JdbcApiIT.ping");
        init();
        testBeans();
    }

    /**
     * Cleanup JPA application tests.
     */
    @Override
    public void close() throws Throwable {
        LOGGER.fine("Running JPA application test close()");
        shutdown();
    }

    @SuppressWarnings("SleepWhileInLoop")
    public static void waitForDatabase(final String dbUrl, final String dbUser, final String dbPassword) {
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL was not set!");
        }
        if (dbUser == null) {
            throw new IllegalStateException("Database user name was not set!");
        }
        if (dbPassword == null) {
            throw new IllegalStateException("Database user password was not set!");
        }
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        while (true) {
            try {
                Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                Utils.closeConnection(conn);
                return;
            } catch (SQLException ex) {
                LOGGER.info(() -> String.format("Connection check: %s", ex.getMessage()));
                if (System.currentTimeMillis() > endTm) {
                    throw new IllegalStateException(String.format("Database is not ready within %d seconds", TIMEOUT));
                }
                try {
                    Thread.sleep(SLEEP_MILIS);
                } catch (InterruptedException ie) {
                    LOGGER.log(Level.WARNING, ie, () -> String.format("Thread was interrupted: %s", ie.getMessage()));
                }
            }
        }
    }

    @SuppressWarnings("SleepWhileInLoop")
    public static void waitForServer() {
        WebTarget status = TARGET.path("/status");

        long tmEnd = System.currentTimeMillis() + (TIMEOUT * 1000);
        boolean retry = true;
        while (retry) {
            try {
                Response response = status.request().get();
                retry = false;
            } catch (Exception ex) {
                LOGGER.fine(() -> String.format("Connection check: %s", ex.getMessage()));
                if (System.currentTimeMillis() > tmEnd) {
                    throw new IllegalStateException(String.format("Appserver is not ready within %d seconds", TIMEOUT));
                }
                try {
                    Thread.sleep(SLEEP_MILIS);
                } catch (InterruptedException ie) {
                    LOGGER.log(Level.WARNING, ie, () -> String.format("Thread was interrupted: %s", ie.getMessage()));
                }
            }
        }
    }

    public void init() {
        WebTarget status = TARGET.path("/init");
        Response response = status.request().get();
        String responseStr = response.readEntity(String.class);
        try {
            Validate.check(responseStr);
        } catch (JsonParsingException t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("Response is not JSON: %s, message: %s", t.getMessage(), responseStr));
        }
    }

    public void testBeans() {
        WebTarget status = TARGET.path("/beans");
        Response response = status.request().get();
        String responseStr = response.readEntity(String.class);
        try {
            Validate.check(responseStr);
        } catch (JsonParsingException t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("Response is not JSON: %s, message: %s", t.getMessage(), responseStr));
        }
    }

    public void shutdown() {
        WebTarget exit = TARGET.path("/exit");
        Response response = exit.request().get();
        LOGGER.info(() -> String.format("Status: %s", response.readEntity(String.class)));
    }

    // Specific database initialization code

    /**
     * Strip query parameters from MsSQL URL to get SA connection URL.
     *
     * @param dbUrl database URL
     * @return database URL without query parameters
     */
    private static String saUrlOfMsSQL(final String dbUrl) {
        final int semiColonPos = dbUrl.indexOf(';');
        return semiColonPos > 0 ? dbUrl.substring(0, semiColonPos) : dbUrl;
    }


    /**
     * Execute SQL statement.
     *
     * @param conn database connection
     * @param sql SQL statement
     * @param errMsg error message to log when statement execution failed
     */
    private static void executeStatement(final Connection conn, final String sql, final String errMsg) {
        try {
            Statement stmt = conn.createStatement();
            final int dbCount = stmt.executeUpdate(sql);
            LOGGER.log(Level.INFO, () -> String.format("Executed EXEC statement. %d records modified.", dbCount));
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, errMsg, ex);
        }
    }

    /**
     * Initialize MsSQL database.
     * Database name is retrieved from connection URL.
     *
     * @param dbUrl MsSQL database connection URL with database name
     * @param dbUser MsSQL database connection user name
     * @param dbPassword MsSQL database connection user password
     */
    private static void initMsSQL(final String dbUrl, final String dbUser, final String dbPassword, final String dbSaPassword) {
        String database = null;
        final int semiColonPos = dbUrl.indexOf(';');
        if (semiColonPos < 0) {
            throw new IllegalArgumentException("MsSQL URL Query does not contain query parameters");
        }
        final String urlQuery = dbUrl.substring(semiColonPos + 1);
        LOGGER.fine(() -> String.format("URL %s has query part %s", dbUrl, urlQuery));
        final int pos = urlQuery.indexOf("databaseName=");
        if (pos < 0) {
            throw new IllegalArgumentException("MsSQL URL Query does not contain databaseName parameter");
        }
        if (urlQuery.length() < (pos + 14)) {
            throw new IllegalArgumentException("MsSQL URL Query dose not contain databaseName parameter value");
        }
        final int end = urlQuery.indexOf(dbUser, pos + 13);
        database = end > 0 ? urlQuery.substring(pos + 13, end) : urlQuery.substring(pos + 13);
        if (database == null) {
            throw new IllegalStateException("Missing database name!");
        }
        try (Connection conn = DriverManager.getConnection(saUrlOfMsSQL(dbUrl), "sa", dbSaPassword)) {
            executeStatement(conn,
                    String.format("EXEC sp_configure 'CONTAINED DATABASE AUTHENTICATION', 1", database),
                    "Could not configure database:");
            executeStatement(conn,
                    "RECONFIGURE", "Could not reconfigure database:");
            executeStatement(conn,
                    String.format("CREATE DATABASE %s CONTAINMENT = PARTIAL", database),
                    "Could not create database:");
            executeStatement(conn,
                    String.format("USE %s", database), "Could not use database:");
            executeStatement(conn,
                    String.format("CREATE USER %s WITH PASSWORD = '%s'", dbUser, dbPassword),
                    "Could not create database user:");
            executeStatement(conn,
                    String.format("GRANT ALL TO %s", dbUser),
                    "Could not grant database privilegs to user:");
            executeStatement(conn,
                    String.format("GRANT CONTROL ON SCHEMA::dbo TO %s", dbUser),
                    "Could not grant database privilegs to user:");
        } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Could not open database connection:", ex);
        }
    }

}
