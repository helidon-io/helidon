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

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.common.SqlDriver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleGeneratedRepositoryRetryTest {
    @TempDir
    private Path output;

    @Test
    void returnsTheFirstSuccessfulConnectionWithoutRetrying() throws SQLException, IOException {
        AtomicInteger attempts = new AtomicInteger();
        Connection expected = connection();
        var diagnostics = diagnostics();
        diagnostics.start();
        try {
            assertThat(connect(driver(attempts, () -> expected), diagnostics), sameInstance(expected));
        } finally {
            diagnostics.stop();
        }
        assertThat(attempts.get(), is(1));
        assertThat(Files.exists(directory().resolve("container-connection-refused.log")), is(false));
    }

    @Test
    void recoversOnTheThirdAttemptAndRetainsTheRefusalEvidence() throws SQLException, IOException {
        AtomicInteger attempts = new AtomicInteger();
        Connection expected = connection();
        Driver driver = driver(attempts, () -> {
            if (attempts.get() < 3) {
                throw new SQLException("secret SQL/password", "66000", 12516);
            }
            return expected;
        });
        var diagnostics = diagnostics();
        diagnostics.start();
        try {
            assertThat(connect(driver, diagnostics), sameInstance(expected));
            diagnostics.finishTest("repository", null);
        } finally {
            diagnostics.stop();
        }
        assertThat(attempts.get(), is(3));
        String log = Files.readString(directory().resolve("diagnostics.log"));
        assertThat(log, containsString("connection-attempt=1"));
        assertThat(log, containsString("connection-attempt=2"));
        assertThat(log, containsString("connection-attempt=3"));
        assertThat(log, containsString("sqlState=66000 vendorCode=12516"));
        assertThat(log, containsString(" recovered"));
        assertThat(log, containsString("outcome=passed"));
        assertThat(log, not(containsString("secret")));
        assertThat(Files.readString(directory().resolve("container-connection-refused.log")),
                   containsString("listener evidence"));
    }

    @Test
    void failsAfterThreeRefusalsWithTheOriginalException() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        SQLException original = new SQLException("first refusal", "66000", 12516);
        Driver driver = driver(attempts, () -> {
            throw attempts.get() == 1 ? original : new SQLException("later refusal", "66000", 12516);
        });
        var diagnostics = diagnostics();
        diagnostics.start();
        try {
            assertThat(assertThrows(SQLException.class, () -> connect(driver, diagnostics)), sameInstance(original));
        } finally {
            diagnostics.stop();
        }
        assertThat(attempts.get(), is(3));
        String log = Files.readString(directory().resolve("diagnostics.log"));
        assertThat(log, containsString("connection-attempt=3"));
        assertThat(log, not(containsString(" recovered")));
    }

    @Test
    void doesNotRetryAnotherOracleErrorWithTheSameSqlState() {
        AtomicInteger attempts = new AtomicInteger();
        SQLException other = new SQLException("ORA-12516 in text is insufficient", "66000", 12514);
        Driver driver = driver(attempts, () -> {
            throw other;
        });
        var diagnostics = diagnostics();
        diagnostics.start();
        try {
            assertThat(assertThrows(SQLException.class, () -> connect(driver, diagnostics)), sameInstance(other));
        } finally {
            diagnostics.stop();
        }
        assertThat(attempts.get(), is(1));
    }

    @Test
    void stopsImmediatelyIfARetryEncountersAnotherError() {
        AtomicInteger attempts = new AtomicInteger();
        SQLException other = new SQLException("service unavailable", "66000", 12514);
        Driver driver = driver(attempts, () -> {
            throw attempts.get() == 1 ? new SQLException("refused", "66000", 12516) : other;
        });
        var diagnostics = diagnostics();
        diagnostics.start();
        try {
            assertThat(assertThrows(SQLException.class, () -> connect(driver, diagnostics)), sameInstance(other));
        } finally {
            diagnostics.stop();
        }
        assertThat(attempts.get(), is(2));
    }

    @Test
    void interruptionStopsRetriesAndPreservesTheInterruptStatus() {
        AtomicInteger attempts = new AtomicInteger();
        SQLException original = new SQLException("refused", "66000", 12516);
        Driver driver = driver(attempts, () -> {
            throw original;
        });
        var diagnostics = diagnostics();
        diagnostics.start();
        try {
            Thread.currentThread().interrupt();
            assertThat(assertThrows(SQLException.class, () -> connect(driver, diagnostics)), sameInstance(original));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
            assertThat(original.getSuppressed()[0], instanceOf(InterruptedException.class));
        } finally {
            Thread.interrupted();
            diagnostics.stop();
        }
        assertThat(attempts.get(), is(1));
    }

    @Test
    void resolvesAndDeregistersTheSuiteDriver() throws SQLException {
        OracleGeneratedRepositoryTest.registerRetryDriver();
        try {
            var config = ConnectionConfig.builder()
                    .url("jdbc:oracle:thin:@localhost:1521/FREEPDB1")
                    .jdbcDriverClassName(OracleGeneratedRepositoryTest.RETRY_DRIVER.getClass().getName())
                    .build();
            assertThat(SqlDriver.create(config).driver(), sameInstance(OracleGeneratedRepositoryTest.RETRY_DRIVER));
        } finally {
            OracleGeneratedRepositoryTest.deregisterRetryDriver();
        }
        assertThat(DriverManager.drivers().anyMatch(driver -> driver == OracleGeneratedRepositoryTest.RETRY_DRIVER), is(false));
    }

    private static Connection connect(Driver driver, OracleGeneratedRepositoryDiagnostics diagnostics) throws SQLException {
        return OracleGeneratedRepositoryTest.connectWithRetry(driver, diagnostics, "jdbc:oracle:test", new Properties());
    }

    private static Driver driver(AtomicInteger attempts, OracleGeneratedRepositoryDiagnostics.ConnectionSupplier connections) {
        return (Driver) Proxy.newProxyInstance(OracleGeneratedRepositoryRetryTest.class.getClassLoader(),
                new Class<?>[] {Driver.class}, (_, method, _) -> {
                    if (!method.getName().equals("connect")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    attempts.incrementAndGet();
                    return connections.get();
                });
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(OracleGeneratedRepositoryRetryTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (_, method, _) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private OracleGeneratedRepositoryDiagnostics diagnostics() {
        return new OracleGeneratedRepositoryDiagnostics(output, () -> {
            throw new SQLException("observer unavailable");
        }, () -> "listener evidence");
    }

    private Path directory() throws IOException {
        try (Stream<Path> directories = Files.list(output)) {
            return directories.findFirst().orElseThrow();
        }
    }
}
