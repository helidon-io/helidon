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
     * Wait for database server to start.
     *
     * @param dbClient Helidon database client
     */
    private static void waitForStart() {
        String url = CONFIG.get("db.url").asString().get();
        String username = CONFIG.get("db.username").asString().get();
        String password = CONFIG.get("db.password").asString().get();
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        boolean retry = true;
        while (retry) {
            try {
                DriverManager.getConnection(url, username, password);
                retry = false;
            } catch (SQLException ex) {
                if (System.currentTimeMillis() > endTm) {
                    fail("Database startup failed!", ex);
                }
            }
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
        String url = CONFIG.get("db.url").asString().get();
        String username = CONFIG.get("db.username").asString().get();
        String password = CONFIG.get("db.password").asString().get();
        String ping = CONFIG.get("db.statements.ping").asString().get();
        Connection conn = DriverManager.getConnection(url, username, password);
        int result = conn.createStatement().executeUpdate(ping);
        assertThat(result, equalTo(0));
    }

}
