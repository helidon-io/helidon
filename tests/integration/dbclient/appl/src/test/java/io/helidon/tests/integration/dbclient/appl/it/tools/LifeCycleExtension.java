/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl.it.tools;

import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientResponse;
import io.helidon.tests.integration.dbclient.appl.ApplMain;
import io.helidon.tests.integration.dbclient.appl.it.ApplInitIT;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner.ExecType;
import io.helidon.tests.integration.tools.client.TestsLifeCycleExtension;

/**
 * jUnit test life cycle extensions.
 */
public class LifeCycleExtension extends TestsLifeCycleExtension {

    private static final System.Logger LOGGER = System.getLogger(LifeCycleExtension.class.getName());

    /* Thread sleep time in miliseconds while waiting for database or appserver to come up. */
    private static final int SLEEP_MILIS = 250;

    /* Startup timeout in seconds for both database and web server. */
    private static final int TIMEOUT = 60;

    // Application config file retrieved from "app.config" property
    private final String appConfigProperty;
    // Whether application is build as native image ("native.image" property).
    private final Boolean nativeImage;

    public LifeCycleExtension() {
        appConfigProperty = System.getProperty("app.config");
        nativeImage = Boolean.valueOf(System.getProperty("native.image", "false"));
    }

    @Override
    public void check() {
        LOGGER.log(Level.DEBUG, "Running test application check()");
        waitForDatabase();
    }

    /**
     * Setup application tests.
     */
    @Override
    public void setup() {
        LOGGER.log(Level.DEBUG, "Running test application setup()");
        // Call schema initialization services
        if ("h2.yaml".equals(appConfigProperty)) {
            ApplInitIT init = new ApplInitIT();
            init.testDropSchema();
            init.testInitSchema();
            init.testInitTypes();
            init.testInitPokemons();
            init.testInitPokemonTypes();
        }
    }

    /**
     * Cleanup JPA application tests.
     * @throws java.lang.Throwable
     */
    @Override
    public void close() throws Throwable {
        LOGGER.log(Level.DEBUG, "Running test application close()");
        shutdown();
    }

    @Override
    protected HelidonProcessRunner.ExecType processRunnerExecType() {
        return nativeImage ? ExecType.NATIVE : ExecType.CLASS_PATH;
    }

    @Override
    protected String processRunnerModuleName() {
        return "io.helidon.tests.integration.dbclient.appl";
    }

    @Override
    protected String processRunnerMainClass() {
        return ApplMain.class.getName();
    }

    @Override
    protected String processRunnerFinalName() {
        return "helidon-tests-integration-dbclient-appl";
    }

    @Override
    protected String[] processRunnerArgs() {
        return new String[] {appConfigProperty};
    }

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
                LOGGER.log(Level.DEBUG, () -> String.format("Connection check: user=%s password=%s url=%s", dbUser, dbPassword, dbUrl));
                Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                closeConnection(conn);
                return;
            } catch (SQLException ex) {
                LOGGER.log(Level.DEBUG, () -> String.format("Connection check: %s", ex.getMessage()));
                if (System.currentTimeMillis() > endTm) {
                    throw new IllegalStateException(String.format("Database is not ready within %d seconds", TIMEOUT));
                }
                try {
                    Thread.sleep(SLEEP_MILIS);
                } catch (InterruptedException ie) {
                    LOGGER.log(Level.WARNING, () -> String.format("Thread was interrupted: %s", ie.getMessage()), ie);
                }
            }
        }
    }
    /**
     * Shutdown test application
     */
    public void shutdown() {
        WebClient testClient = WebClient
                .builder()
                .baseUri(String.format("http://localhost:%d", HelidonProcessRunner.HTTP_PORT))
                .build();
        WebClientResponse response = testClient
                .get()
                .path("/Exit")
                .submit()
                .await(1, TimeUnit.MINUTES);
        LOGGER.log(Level.INFO, () -> String.format(
                "Status: %s",
                response.status()));
        LOGGER.log(Level.INFO, () -> String.format(
                "Response: %s",
                response
                        .content()
                        .as(String.class)
                        .await(1, TimeUnit.MINUTES)));
    }

    // Close database connection.
    private static void closeConnection(final Connection connection) {
        try {
            connection.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, () -> String.format("Could not close database connection: %s", ex.getMessage()));
        }
    }

}
