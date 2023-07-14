/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.tests.integration.harness.AfterSuite;
import io.helidon.tests.integration.harness.BeforeSuite;
import io.helidon.tests.integration.harness.HelidonProcessRunner;
import io.helidon.tests.integration.harness.HelidonProcessRunner.ExecType;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("io.helidon.tests.integration.dbclient.app.tests")
@IncludeClassNamePatterns(".*IT")
class SuiteIT {

    private static final System.Logger LOGGER = System.getLogger(SuiteIT.class.getName());
    private static final int SLEEP_MILLIS = 250;
    private static final int TIMEOUT = 60;

    @BeforeSuite
    static Map<String, Object> setup() {
        LogConfig.configureRuntime();
        LOGGER.log(System.Logger.Level.DEBUG, "Running test suite setup");

        String appConfigProperty = System.getProperty("app.config");
        ExecType execType = ExecType.of(System.getProperty("app.execType", "classpath"));
        WebServer[] server = new WebServer[1];
        HelidonProcessRunner processRunner = HelidonProcessRunner.builder()
                .execType(execType)
                .moduleName("io.helidon.tests.integration.dbclient.app")
                .mainClass(Main.class.getName())
                .finalName("helidon-tests-integration-dbclient-app")
                .args(new String[]{appConfigProperty})
                .inMemoryStartCommand(() -> server[0] = Main.startServer(appConfigProperty))
                .inMemoryStopCommand(() -> server[0].stop())
                .build()
                .startApplication();

        int serverPort = processRunner.port();

        waitForDatabase();

        // Call schema initialization services
        if ("h2.yaml".equals(appConfigProperty)) {
            DbInit dbInit = new DbInit(serverPort);
            dbInit.dropSchema();
            dbInit.initSchema();
            dbInit.initTypes();
            dbInit.initPokemons();
            dbInit.initPokemonTypes();
        }

        return Map.of("processRunner", processRunner, "serverPort", serverPort);
    }

    @AfterSuite
    static void tearDown(int serverPort) {
        LOGGER.log(System.Logger.Level.DEBUG, "Running test application close()");
        Http1Client testClient = Http1Client.builder()
                .baseUri(String.format("http://localhost:%d", serverPort))
                .build();
        try (Http1ClientResponse response = testClient.get()
                .path("/Exit")
                .request()) {
            LOGGER.log(System.Logger.Level.INFO, () -> String.format(
                    "Status: %s",
                    response.status()));
            LOGGER.log(System.Logger.Level.INFO, () -> String.format(
                    "Response: %s",
                    response.entity().as(String.class)));
        }
    }

    @SuppressWarnings({"SleepWhileInLoop", "BusyWait"})
    private static void waitForDatabase() {
        String user = System.getProperty("db.user");
        String password = System.getProperty("db.password");
        String url = System.getProperty("db.url");
        if (user == null) {
            throw new IllegalStateException("Database user name was not set!");
        }
        if (password == null) {
            throw new IllegalStateException("Database user password was not set!");
        }
        if (url == null) {
            throw new IllegalStateException("Database URL was not set!");
        }
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        while (true) {
            try {
                LOGGER.log(System.Logger.Level.DEBUG, () -> String.format(
                        "Connection check: user=%s password=%s url=%s",
                        user, password, url));
                Connection conn = DriverManager.getConnection(url, user, password);
                closeConnection(conn);
                return;
            } catch (SQLException ex) {
                LOGGER.log(System.Logger.Level.DEBUG, () -> String.format(
                        "Connection check: %s", ex.getMessage()));
                if (System.currentTimeMillis() > endTm) {
                    throw new IllegalStateException(String.format(
                            "Database is not ready within %d seconds", TIMEOUT));
                }
                try {
                    Thread.sleep(SLEEP_MILLIS);
                } catch (InterruptedException ie) {
                    LOGGER.log(System.Logger.Level.WARNING, () -> String.format(
                            "Thread was interrupted: %s", ie.getMessage()), ie);
                }
            }
        }
    }

    private static void closeConnection(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ex) {
            LOGGER.log(System.Logger.Level.WARNING, () -> String.format(
                    "Could not close database connection: %s", ex.getMessage()));
        }
    }
}
