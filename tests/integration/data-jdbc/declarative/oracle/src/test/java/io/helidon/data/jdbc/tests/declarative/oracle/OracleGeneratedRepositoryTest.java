/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.declarative.oracle;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.tests.contract.AbstractGeneratedRepositoryContract;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executes generated repository behavior against Oracle Database.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleGeneratedRepositoryTest extends AbstractGeneratedRepositoryContract {
    @Container
    static final GenericContainer<?> ORACLE = OracleDeclarativeTestSupport.ORACLE;

    // Testcontainers starts before this field extension and stops after it.
    @RegisterExtension
    static final OracleGeneratedRepositoryDiagnostics DIAGNOSTICS = new OracleGeneratedRepositoryDiagnostics(ORACLE);

    static final Driver RETRY_DRIVER = new RetryingOracleDriver();

    // Includes the original connection attempt, followed by at most two retries.
    private static final int CONNECTION_ATTEMPTS = 3;

    @BeforeAll
    static void registerRetryDriver() throws SQLException {
        // Helidon resolves the configured driver class among DriverManager's registered instances.
        DriverManager.registerDriver(RETRY_DRIVER);
    }

    @AfterAll
    static void deregisterRetryDriver() throws SQLException {
        // Release the suite's adapter when the shared test JVM continues with other suites.
        DriverManager.deregisterDriver(RETRY_DRIVER);
    }

    static Connection connectWithRetry(Driver driver, OracleGeneratedRepositoryDiagnostics diagnostics,
                                       String url, Properties properties) throws SQLException {
        SQLException firstRefusal = null;
        // Retry only physical connection opening; SQL and transaction execution stay outside this loop.
        for (int attempt = 1; ; attempt++) {
            try {
                Connection connection = driver.connect(url, properties);
                if (attempt > 1 && connection != null) {
                    // Retain refusal evidence even when recovery allows the test to pass.
                    diagnostics.connectionAttempt(attempt, null);
                }
                return connection;
            } catch (SQLException failure) {
                if (failure.getErrorCode() != 12516) {
                    throw failure;
                }
                if (firstRefusal == null) {
                    // Preserve the stack of the original failed acquisition if every attempt is refused.
                    firstRefusal = failure;
                }
                diagnostics.connectionAttempt(attempt, failure);
                if (attempt == CONNECTION_ATTEMPTS) {
                    throw firstRefusal;
                }
                try {
                    // The listener can remain blocked beyond a subsecond retry window.
                    // Wait 1 s, then 2 s, to allow its handler state to refresh before the final attempt.
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException interrupted) {
                    // Stop retrying on cancellation and preserve the caller's interrupt status.
                    Thread.currentThread().interrupt();
                    firstRefusal.addSuppressed(interrupted);
                    throw firstRefusal;
                }
            }
        }
    }

    @Override
    protected void beforeStartApplication() {
        // Select the adapter only for this suite, retaining the shared fixture's connection settings.
        Map<String, String> values = new HashMap<>(OracleDeclarativeTestSupport.config().asMap().get());
        values.put("data.clients.jdbc.0.connection.jdbc-driver-class-name", RETRY_DRIVER.getClass().getName());
        TestConfigFactory.config(Config.just(ConfigSources.create(values)));
    }

    // OracleDriver deregisters other OracleDriver instances, so use delegation instead of inheritance.
    private static final class RetryingOracleDriver implements Driver {
        private final Driver delegate = new OracleDriver();

        @Override
        public Connection connect(String url, Properties properties) throws SQLException {
            return connectWithRetry(delegate, DIAGNOSTICS, url, properties);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) throws SQLException {
            return delegate.getPropertyInfo(url, properties);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}
