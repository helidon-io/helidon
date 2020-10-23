/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tests.integration.dbclient.common.ConfigIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */
public class CheckIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(CheckIT.class.getName());

    /** Test configuration. */
    public static final Config CONFIG = Config.create(ConfigSources.classpath(ConfigIT.configFile()));

    /** Timeout in seconds to wait for database to come up. */
    private static final int TIMEOUT = 60;

    /* Thread sleep time in miliseconds while waiting for database or appserver to come up. */
    private static final int SLEEP_MILIS = 250;

    /**
     * Wait until database starts up when its configuration node is available.
     */
    private static final class ConnectionCheck implements Consumer<Config> {

        private boolean connected;

        private ConnectionCheck() {
            connected = false;
        }

        @Override
        @SuppressWarnings("SleepWhileInLoop")
        public void accept(Config config) {
            String url = config.get("url").asString().get();
            String username = config.get("username").asString().get();
            String password = config.get("password").asString().get();
            long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
            while (true) {
                try {
                    DriverManager.getConnection(url, username, password);
                    connected = true;
                    return;
                } catch (SQLException ex) {
                    LOGGER.fine(() -> String.format("Connection check: %s", ex.getMessage()));
                    if (System.currentTimeMillis() > endTm) {
                        return;
                    }
                    try {
                        Thread.sleep(SLEEP_MILIS);
                    } catch (InterruptedException ie) {
                        LOGGER.log(Level.WARNING, ie, () -> String.format("Thread was interrupted: %s", ie.getMessage()));
                    }
                }
            }
        }

        private boolean connected() {
            return connected;
        }

    }

    /**
     * Store database connection configuration and build {@link Connection} instance.
     */
    static final class ConnectionBuilder implements Consumer<Config> {

        private boolean hasConfig;
        private String url;
        private String username;
        private String password;

        ConnectionBuilder() {
            hasConfig = false;
        }

        @Override
        public void accept(Config config) {
            url = config.get("url").asString().get();
            username = config.get("username").asString().get();
            password = config.get("password").asString().get();
            hasConfig = true;
        }

        Connection createConnection() throws SQLException {
            if (!hasConfig) {
                fail("No db.connection configuration node was found.");
            }
            return DriverManager.getConnection(url, username, password);
        }

    }

    /**
     * Wait for database server to start.
     */
    private static void waitForStart() {
        ConnectionCheck check = new ConnectionCheck();
        CONFIG.get("db.connection").ifExists(check);
        if (!check.connected()) {
            fail("Database startup failed!");
        }
    }

    /**
     * Setup database for tests.
     * Wait for database to start. Returns after ping query completed successfully or timeout passed.
     */
    @BeforeAll
    public static void setup() {
        waitForStart();
    }

    /**
     * Simple test to verify that DML query execution works.
     * Used before running database schema initialization.
     *
     * @throws SQLException when database query failed
     */
    @Test
    public void testDmlStatementExecution() throws SQLException {
        ConnectionBuilder builder = new ConnectionBuilder();
        String ping = CONFIG.get("db.statements.ping").asString().get();
        Config cfgPingDml = CONFIG.get("test.ping-dml");
        boolean pingDml = cfgPingDml.exists() ? cfgPingDml.asBoolean().get() : true;
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
