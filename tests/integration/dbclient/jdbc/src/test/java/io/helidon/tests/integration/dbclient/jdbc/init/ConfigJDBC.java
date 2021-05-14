/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.logging.Logger;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tests.integration.dbclient.common.ConfigIT;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

// This class is being initialized and called before anythong else in jUnit tests.
// It shall not depend on / or call any code that requires initialized database or DcClient.
/**
 * Configure JDBC tests.
 */
public class ConfigJDBC implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger LOGGER = Logger.getLogger(ConfigJDBC.class.getName());

    private static final String STORE_KEY = ConfigJDBC.class.getName();

    // Test configuration
    public static final Config CONFIG = Config.create(ConfigSources.classpath(ConfigIT.configFile()));

    private static final String DBA_USER;
    private static final String DBA_PASSWORD;
    public static final String DB_USER;
    public static final String DB_PASSWORD;
    public static final String DB_URL;

    static {
        DBA_USER = System.getProperty("dba.user");
        DBA_PASSWORD = System.getProperty("dba.password");
        Config connConfig = CONFIG.get("db.connection");
        if (connConfig.exists()) {
            DB_USER = connConfig.get("username").asString().get();
            DB_PASSWORD = connConfig.get("password").asString().get();
            DB_URL = connConfig.get("url").asString().get();
        } else {
            DB_USER = null;
            DB_PASSWORD = null;
            DB_URL = null;
        }
    }

    public static final String getCheckUser() {
        return DBA_USER != null && DBA_USER.length() > 0 ? DBA_USER : DB_USER;
    }

    public static final String getCheckPassword() {
        return DBA_PASSWORD != null && DBA_PASSWORD.length() > 0 ? DBA_PASSWORD : DB_PASSWORD;
    }

    private final String dbUser;
    private final String dbPassword;

    public ConfigJDBC() {
        if (DB_USER == null || DB_USER.isEmpty()) {
            throw new IllegalStateException("Missing db.connection.username in test configuration file.");
        }
        if (DB_PASSWORD == null) {
            throw new IllegalStateException("Missing db.connection.password in test configuration file.");
        }
        if (DB_URL == null || DB_URL.isEmpty()) {
            throw new IllegalStateException("Missing db.connection.url in test configuration file.");
        }
        dbUser = getCheckUser();
        dbPassword = getCheckPassword();
    }

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
            LOGGER.fine("Running beforeAll lifecycle method for the first time, invoking setup()");
            ec.getRoot().getStore(GLOBAL).put(STORE_KEY, this);
            waitForStart(dbUser, dbPassword, DB_URL);
            setup(dbUser, dbPassword, DB_URL);
        } else {
            LOGGER.finer("Running beforeAll lifecycle method next time, skipping setup()");
        }
    }

    public static void setup(final String username, final String password, final String url) {
        LOGGER.fine(() -> "Running test application setup()");
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, username, password);
            final String dbName = conn.getMetaData().getDatabaseProductName();
            ConfigIT.initDbType(dbName);
        } catch (SQLException ex) {
            LOGGER.warning(() -> String.format("Could not set database type: %s", ex.getMessage()));
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    LOGGER.warning(() -> String.format("Could not close database connection: %s", ex.getMessage()));
                }
            }
        }
    }

    @Override
    public void close() throws Throwable {
    }

    /**
     * Wait for database server to start.
     */
    private static void waitForStart(final String username, final String password, final String url) {
        LOGGER.info(() -> String.format("Waiting for database %s startup as user %s and password %s", url, username, password));
        ConnectionCheck check = new ConnectionCheck(username, password, url);
        check.check();
        if (!check.connected()) {
            fail("Database startup failed!");
        }
    }

    // Timeout in seconds to wait for database to come up.
    private static final int TIMEOUT = 1200;

    /**
     * Wait until database starts up when its configuration node is available.
     */
    private static final class ConnectionCheck {

        private boolean connected;
        private final String username;
        private final String password;
        private final String url;

        private ConnectionCheck(final String username, final String password, final String url) {
            this.username = username;
            this.password = password;
            this.url = url;
            LOGGER.finest(() -> String.format("ConnectionCheck database user: %s", username));
            connected = false;
        }

        public void check() {
            long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
            while (true) {
                try {
                    DriverManager.getConnection(url, username, password);
                    connected = true;
                    return;
                } catch (SQLException ex) {
                    LOGGER.info(() -> String.format("Connection check: %s", ex.getMessage()));
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

    private static interface DbaConnection {
        public void accept(final String dbaUser, final String dbaPassword, final String dbaUrl);

    }

    /**
     * Store database connection configuration and build {@link Connection} instance.
     */
    static final class ConnectionBuilder implements Consumer<Config>, DbaConnection {

        private boolean hasConfig;
        private String url;
        private String username;
        private String password;

        ConnectionBuilder() {
            hasConfig = false;
        }

        @Override
        public void accept(final Config config) {
            url = config.get("url").asString().get();
            username = config.get("username").asString().get();
            password = config.get("password").asString().get();
            hasConfig = true;
        }

        @Override
        public void accept(final String dbaUser, final String dbaPassword, final String dbaUrl) {
            url = dbaUrl;
            username = dbaUser;
            password = dbaPassword;
            hasConfig = true;
        }

        Connection createConnection() throws SQLException {
            if (!hasConfig) {
                fail("No db.connection configuration node was found.");
            }
            return DriverManager.getConnection(url, username, password);
        }

    }

}
