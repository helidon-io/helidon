/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.tools.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;
import io.helidon.tests.integration.tools.client.TestsLifeCycleExtension;

// Implements global setup and close actions. This class must be registered as SPI service.
/**
 * jUnit test life cycle extensions.
 */
public class ServerLifeCycleExtension extends TestsLifeCycleExtension {

    private static final Logger LOGGER = Logger.getLogger(ServerLifeCycleExtension.class.getName());

    // Application config file retrieved from "app.config" property
    private final String appConfigProperty;
    // Whether application is build as native image ("native.image" property).
    private final Boolean nativeImage;
    // HTTP client will be initialized after web server startup.
    private TestServiceClient lifeCycleClient = null;

    public ServerLifeCycleExtension() {
        appConfigProperty = System.getProperty("app.config");
        nativeImage = Boolean.valueOf(System.getProperty("native.image", "false"));
    }


    @Override
    public void check() {
        LOGGER.info("Running initial test check()");
        waitForDatabase();
    }

    /**
     * Setup application tests.
     */
    @Override
    public void setup() {
        lifeCycleClient = TestClient.builder()
                    .port(HelidonProcessRunner.HTTP_PORT)
                    .service("LifeCycle")
                    .build();
        LOGGER.info("Running global test setup setup()");
        lifeCycleClient.callServiceAndGetData("init");
    }

    /**
     * Cleanup JPA application tests.
     * @throws java.lang.Throwable
     */
    @Override
    public void close() throws Throwable {
        LOGGER.fine("Running global test close()");
        runner.stopApplication();
    }

    @Override
    protected HelidonProcessRunner.ExecType processRunnerExecType() {
        return nativeImage ? HelidonProcessRunner.ExecType.NATIVE : HelidonProcessRunner.ExecType.CLASS_PATH;
    }

    @Override
    protected String processRunnerModuleName() {
        return "io.helidon.tests.integration.tools.example";
    }

    @Override
    protected String processRunnerMainClass() {
        return ServerMain.class.getName();
    }

    @Override
    protected String processRunnerFinalName() {
        return "helidon-tests-integration-tools-example";
    }

    @Override
    protected String[] processRunnerArgs() {
        return new String[] {appConfigProperty};
    }

    @Override
    protected Runnable processRunnerStopCommand() {
        return () -> {
            String response = lifeCycleClient.callServiceAndGetString("exit");
            LOGGER.fine(() -> String.format("Response: %s", response));
        };
    }

    // Thread sleep time in miliseconds while waiting for database or appserver to come up.
    private static final int SLEEP_MILIS = 250;

    // Startup timeout in seconds for database server.
    private static final int TIMEOUT = 60;

    @SuppressWarnings("SleepWhileInLoop")
    public static void waitForDatabase() {
        final String dbUser = System.getProperty("db.user");
        final String dbPassword = System.getProperty("db.password");
        final String dbUrl = System.getProperty("db.url");
        if (dbUser == null) {
            throw new IllegalStateException("Database user name was not set!");
        }
        if (dbPassword == null) {
            throw new IllegalStateException("Database user password was not set!");
        }
        if (dbUrl == null) {
            throw new IllegalStateException("Database URL was not set!");
        }
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        while (true) {
            try {
                LOGGER.finest(() -> String.format("Connection check: user=%s password=%s url=%s", dbUser, dbPassword, dbUrl));
                Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                closeConnection(conn);
                return;
            } catch (SQLException ex) {
                LOGGER.finest(() -> String.format("Connection check: %s", ex.getMessage()));
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

    // Close database connection.
    private static void closeConnection(final Connection connection) {
        try {
            connection.close();
        } catch (SQLException ex) {
            LOGGER.warning(() -> String.format("Could not close database connection: %s", ex.getMessage()));
        }
    }

}
