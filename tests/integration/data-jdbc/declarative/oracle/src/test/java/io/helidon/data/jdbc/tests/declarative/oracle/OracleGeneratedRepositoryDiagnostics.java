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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

/**
 * Collects the database and listener evidence needed for this suite's intermittent ORA-12516 failures.
 */
final class OracleGeneratedRepositoryDiagnostics
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    private static final System.Logger LOGGER = System.getLogger(OracleGeneratedRepositoryDiagnostics.class.getName());
    private static final List<String> SAMPLES = List.of("""
            SELECT RESOURCE_NAME, CURRENT_UTILIZATION, MAX_UTILIZATION, INITIAL_ALLOCATION, LIMIT_VALUE, CON_ID
            FROM V$RESOURCE_LIMIT WHERE RESOURCE_NAME IN ('processes', 'sessions', 'transactions')
            """, """
            SELECT CON_ID, USERNAME, STATUS, SERVER, COUNT(*) AS SESSION_COUNT
            FROM V$SESSION WHERE SUBSTR(USERNAME, 1, 8) = 'JDBC_IT_'
            GROUP BY CON_ID, USERNAME, STATUS, SERVER
            """, """
            SELECT CASE WHEN BACKGROUND = '1' THEN 'background' ELSE 'foreground' END AS KIND,
                   COUNT(*) AS PROCESS_COUNT, SUM(PGA_ALLOC_MEM) AS PGA_ALLOCATED_BYTES
            FROM V$PROCESS GROUP BY CASE WHEN BACKGROUND = '1' THEN 'background' ELSE 'foreground' END
            """);
    private static final String CONTAINER_REPORT = """
            printf '=== listener services ===\n'
            timeout 2 lsnrctl services
            printf 'lsnrctl services exit=%s\n' "$?"
            printf '=== listener status ===\n'
            timeout 2 lsnrctl status
            printf 'lsnrctl status exit=%s\n' "$?"
            printf '=== process limits and cgroups ===\n'
            cat /proc/1/limits /proc/self/cgroup
            for file in /sys/fs/cgroup/pids.current /sys/fs/cgroup/pids.peak /sys/fs/cgroup/pids.max \
                        /sys/fs/cgroup/pids.events /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.max \
                        /sys/fs/cgroup/memory.events /sys/fs/cgroup/pids/pids.current /sys/fs/cgroup/pids/pids.max \
                        /sys/fs/cgroup/pids/pids.events /sys/fs/cgroup/memory/memory.failcnt
            do
                if test -r "$file"; then printf '\n%s\n' "$file"; cat "$file"; fi
            done
            printf '\n=== listener and alert logs (including Free Lite without ADR) ===\n'
            timeout 2 find /opt/oracle/diag /opt/oracle/product /opt/oracle/oradata \
                -type f \\( -name 'alert_*.log' -o -name 'listener.log' -o -path '*/alert/log.xml' \\) -print \
                | head -n 3 | while IFS= read -r file
            do
                printf '\n%s\n' "$file"
                tail -c 1048576 "$file"
            done
            """;

    private final Path root;
    private final ConnectionSupplier connections;
    private final ReportSupplier reports;
    private final AtomicReference<Connection> observer = new AtomicReference<>();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> periodic;
    private Path directory;
    private volatile String test = "suite-start";
    private volatile boolean closing;
    private int failures;
    private boolean refusalCaptured;

    OracleGeneratedRepositoryDiagnostics(GenericContainer<?> container) {
        this(Path.of("target/failsafe-reports/oracle-generated-repository"),
             () -> connect(container), () -> report(container));
    }

    OracleGeneratedRepositoryDiagnostics(Path root, ConnectionSupplier connections, ReportSupplier reports) {
        this.root = root;
        this.connections = connections;
        this.reports = reports;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        start();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        test = context.getDisplayName();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        finishTest(context.getDisplayName(), context.getExecutionException().orElse(null));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stop();
    }

    void start() {
        closing = false;
        failures = 0;
        refusalCaptured = false;
        directory = root.resolve("OracleGeneratedRepositoryTest-" + ProcessHandle.current().pid() + '-' + UUID.randomUUID());
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                .name("oracle-generated-repository-diagnostics").factory());
        await(executor.submit(() -> {
            try {
                Files.createDirectories(directory);
                note("suite-start jdk=" + Runtime.version() + " jdbc=" + OracleDriver.getDriverVersion());
                observer.set(connections.get());
                note("observerSessions=1 service=FREE user=SYS role=SYSDBA");
                query("suite-start", "SELECT INSTANCE_NAME, STARTUP_TIME, VERSION, STATUS, "
                        + "SYS_CONTEXT('USERENV', 'CON_NAME') AS CONTAINER_NAME FROM V$INSTANCE");
                query("parameters", "SELECT NAME, VALUE FROM V$PARAMETER WHERE NAME IN "
                        + "('processes', 'sessions', 'transactions', 'cpu_count', 'parallel_max_servers', "
                        + "'sga_target', 'pga_aggregate_limit', 'local_listener', 'service_names') ORDER BY NAME");
                sample("suite-start");
            } catch (IOException | SQLException | RuntimeException failure) {
                unavailable("initialize", failure);
            } finally {
                if (closing) {
                    closeObserver();
                }
            }
        }), 5);
        periodic = executor.scheduleWithFixedDelay(() -> sample(test), 500, 500, TimeUnit.MILLISECONDS);
        executor.submit(() -> capture("suite-start"));
    }

    void connectionAttempt(int attempt, SQLException failure) {
        boolean refused = failure != null;
        String event = test + " connection-attempt=" + attempt + " thread=" + Thread.currentThread().threadId()
                + " occurredAt=" + Instant.now()
                + (refused ? " sqlState=" + failure.getSQLState() + " vendorCode=" + failure.getErrorCode() : " recovered");
        executor.submit(() -> {
            note(event);
            if (refused && !refusalCaptured) {
                refusalCaptured = true;
                sample("connection-refused");
                capture("connection-refused");
            }
        });
    }

    void finishTest(String name, Throwable failure) {
        String capture = failure == null ? null : "failed-" + ++failures;
        executor.submit(() -> {
            note(name + " outcome=" + (failure == null ? "passed" : "failed"));
            Throwable current = failure;
            for (int depth = 0; current != null && depth < 16; depth++) {
                // Exception messages may contain SQL or credentials; record only types and Oracle error codes.
                note("exception=" + current.getClass().getName()
                        + (current instanceof SQLException sql ? " sqlState=" + sql.getSQLState()
                        + " vendorCode=" + sql.getErrorCode() : ""));
                current = current.getCause();
            }
            sample(name);
            if (capture != null) {
                capture(capture);
            }
        });
    }

    void stop() {
        closing = true;
        periodic.cancel(false);
        try {
            await(executor.submit(() -> {
                try {
                    sample("suite-end");
                    capture("suite-end");
                } finally {
                    closeObserver();
                }
            }), 15);
        } finally {
            executor.shutdownNow();
            // Also release the observer if the final task could not run before the deadline.
            if (observer.get() != null) {
                Thread.ofPlatform().daemon(true).name("oracle-diagnostic-cleanup").start(this::closeObserver);
            }
        }
    }

    private static Connection connect(GenericContainer<?> container) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", "sys");
        properties.setProperty("password", container.getEnvMap().get("ORACLE_PWD"));
        properties.setProperty("internal_logon", "sysdba");
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "2000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "2000");
        return new OracleDriver().connect("jdbc:oracle:thin:@%s:%s/FREE"
                .formatted(container.getHost(), container.getMappedPort(1521)), properties);
    }

    private static String report(GenericContainer<?> container) throws IOException, InterruptedException {
        Container.ExecResult result = container.execInContainer("timeout", "8", "bash", "-c", CONTAINER_REPORT);
        StringBuilder report = new StringBuilder("image=").append(container.getDockerImageName())
                .append(" imageId=").append(container.getContainerInfo().getImageId())
                .append(" containerId=").append(container.getContainerId()).append('\n');
        for (String name : List.of("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_SHA", "GITHUB_JOB")) {
            report.append(name).append('=').append(System.getenv().getOrDefault(name, "local")).append('\n');
        }
        report.append("exitCode=").append(result.getExitCode()).append('\n')
                .append(result.getStdout()).append(result.getStderr());
        String logs = container.getLogs();
        report.append("\n=== container log (last 1 MiB) ===\n")
                .append(logs.substring(Math.max(0, logs.length() - 1048576)));
        return report.toString().replace(container.getEnvMap().get("ORACLE_PWD"), "<redacted>");
    }

    private void sample(String phase) {
        for (String sql : SAMPLES) {
            query(phase, sql);
        }
    }

    private void query(String phase, String sql) {
        Connection connection = observer.get();
        if (connection == null) {
            note(phase + " observer-unavailable");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            statement.setMaxRows(256);
            try (ResultSet rows = statement.executeQuery(sql)) {
                StringBuilder values = new StringBuilder(phase).append('\n');
                int columns = rows.getMetaData().getColumnCount();
                for (int column = 1; column <= columns; column++) {
                    values.append(rows.getMetaData().getColumnLabel(column)).append('\t');
                }
                while (rows.next()) {
                    values.append('\n');
                    for (int column = 1; column <= columns; column++) {
                        values.append(rows.getObject(column)).append('\t');
                    }
                }
                note(values.toString());
            }
        } catch (SQLException | RuntimeException failure) {
            unavailable(phase, failure);
        }
    }

    private void capture(String phase) {
        try {
            Files.writeString(directory.resolve("container-" + phase + ".log"),
                              Instant.now() + "\n" + reports.get(), StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            unavailable(phase, failure);
        }
    }

    private void note(String text) {
        try {
            Files.writeString(directory.resolve("diagnostics.log"), Instant.now() + " " + text + '\n',
                              StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Oracle diagnostics could not be written: " + failure.getClass().getName());
        }
    }

    private void unavailable(String phase, Throwable failure) {
        note(phase + " collection-unavailable exception=" + failure.getClass().getName()
                + (failure instanceof SQLException sql ? " sqlState=" + sql.getSQLState()
                + " vendorCode=" + sql.getErrorCode() : ""));
    }

    private void await(Future<?> task, int seconds) {
        try {
            task.get(seconds, TimeUnit.SECONDS);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(System.Logger.Level.WARNING, "Oracle diagnostic collection incomplete: " + failure.getClass().getName());
        }
    }

    private void closeObserver() {
        Connection connection = observer.getAndSet(null);
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException | RuntimeException failure) {
                unavailable("close-observer", failure);
            }
        }
    }

    @FunctionalInterface
    interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    @FunctionalInterface
    interface ReportSupplier {
        String get() throws IOException, InterruptedException;
    }
}
