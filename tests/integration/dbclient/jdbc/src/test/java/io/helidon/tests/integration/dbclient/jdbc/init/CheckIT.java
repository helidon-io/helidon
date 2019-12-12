/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */
public class CheckIT {

    /** Local logger instance. */
    private static final Logger LOG = Logger.getLogger(CheckIT.class.getName());

    /** Test configuration. */
    public static final Config CONFIG = Config.create(ConfigSources.classpath("test.yaml"));

    /** Timeout in seconds to wait for database to come up. */
    private static final int TIMEOUT = 60;

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
                    if (System.currentTimeMillis() > endTm) {
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
     * Store database connection configuration and build {@link Connection} instance.
     */
    private static final class ConnectionBuilder implements Consumer<Config> {

        private boolean hasConfig;
        private String url;
        private String username;
        private String password;

        private ConnectionBuilder() {
            hasConfig = false;
        }

        @Override
        public void accept(Config config) {
            url = config.get("url").asString().get();
            username = config.get("username").asString().get();
            password = config.get("password").asString().get();
            hasConfig = true;
        }

        private Connection createConnection() throws SQLException {
            if (!hasConfig) {
                fail("No db.connection configuration node was found.");
            }
            return DriverManager.getConnection(url, username, password);
        }

    }

    /**
     * Wait for database server to start.
     *
     * @param dbClient Helidon database client
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
        LOG.log(Level.INFO, "Initializing Integration Tests");
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
        CONFIG.get("db.connection").ifExists(builder);
        Connection conn = builder.createConnection();
        int result = conn.createStatement().executeUpdate(ping);
        assertThat(result, equalTo(0));
    }

}
