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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    // Identity values appear in connection descriptors, XML attributes and listener summaries.
    private static final Pattern IDENTITY = Pattern.compile("(?im)(?:\\b(?:host(?:_id|_addr|name)?|machine|node(?:name)?|"
            + "user(?:name)?|service(?:_name|_names)?|instance(?:_name)?|target_local_instance|sid|db_name|"
            + "db_unique_name|oracle_sid|connection_id|containerId|key|alias)\\s*(?:=|:)\\s*"
            + "|\\b(?:Service|Instance)\\s+(?=[\"'])|^\\h*(?:Default\\h+Service|Alias)\\h+)"
            + "(?:[\"']([^\"'\\r\\n]*)[\"']|&quot;([^\\r\\n]*?)&quot;|([^\\s()<>,\"';]+))");
    // Dotted address boundaries preserve Oracle's five-component versions and image version tags.
    private static final Pattern IP_ADDRESS = Pattern.compile("(?i)(?<![\\w.-])"
            + "(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)"
            + "(?![\\w.-])|(?<![\\w.:-])(?:"
            + "::ffff:(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)"
            + "|(?:[a-f\\d]{1,4}:){7}[a-f\\d]{1,4}"
            + "|(?:[a-f\\d]{1,4}:){0,6}[a-f\\d]{0,4}::(?:[a-f\\d]{1,4}:){0,6}[a-f\\d]{0,4}"
            + ")(?:%[\\w.-]+)?(?![\\w.:-])");
    // Exclude XML closing tags and URL separators from filesystem path matches.
    private static final Pattern FILE_PATH = Pattern.compile("(?<![\\w</])/(?:[^\\s<>\"'(),;]+)");
    // Resource high-water marks retain evidence of earlier load when collection begins after a failure.
    private static final List<String> SAMPLES = List.of("""
            SELECT RESOURCE_NAME, CURRENT_UTILIZATION, MAX_UTILIZATION, INITIAL_ALLOCATION, LIMIT_VALUE, CON_ID
            FROM V$RESOURCE_LIMIT WHERE RESOURCE_NAME IN ('processes', 'sessions', 'transactions')
            """, """
            SELECT CON_ID, STATUS, SERVER, COUNT(*) AS SESSION_COUNT
            FROM V$SESSION WHERE SUBSTR(USERNAME, 1, 8) = 'JDBC_IT_'
            GROUP BY CON_ID, STATUS, SERVER
            """, """
            SELECT CASE WHEN BACKGROUND = '1' THEN 'background' ELSE 'foreground' END AS KIND,
                   COUNT(*) AS PROCESS_COUNT, SUM(PGA_ALLOC_MEM) AS PGA_ALLOCATED_BYTES
            FROM V$PROCESS GROUP BY CASE WHEN BACKGROUND = '1' THEN 'background' ELSE 'foreground' END
            """);
    // Bound command duration and log tails; include both ADR and Free Lite log locations.
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
                if test -r "$file"; then printf '\n%s\n' "${file##*/}"; cat "$file"; fi
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
    // Final collection and timeout cleanup may race; only one task takes ownership of closing the connection.
    private final AtomicReference<Connection> observer = new AtomicReference<>();

    private ExecutorService executor;
    private Path directory;
    private volatile String test = "suite-start";
    private volatile boolean closing;
    private int failures;
    private boolean refusalCaptured;

    OracleGeneratedRepositoryDiagnostics(GenericContainer<?> container) {
        this(Path.of("target/failsafe-reports/oracle-generated-repository"),
             () -> {
                 // Query instance-wide limits through the root service, outside application transactions.
                 Properties properties = new Properties();
                 properties.setProperty("user", "sys");
                 properties.setProperty("password", container.getEnvMap().get("ORACLE_PWD"));
                 properties.setProperty("internal_logon", "sysdba");
                 properties.setProperty("oracle.net.CONNECT_TIMEOUT", "2000");
                 properties.setProperty("oracle.jdbc.ReadTimeout", "2000");
                 return new OracleDriver().connect("jdbc:oracle:thin:@%s:%s/FREE"
                         .formatted(container.getHost(), container.getMappedPort(1521)), properties);
             }, () -> {
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
                 // Remove the configured password before the common identity redaction at the file writers.
                 return report.toString().replace(container.getEnvMap().get("ORACLE_PWD"), "<redacted>");
             });
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
        directory = null;
        test = "suite-start";
        // Prepare a serial worker; files and the observer connection are created only after a failure.
        executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true)
                .name("oracle-generated-repository-diagnostics").factory());
    }

    void connectionAttempt(int attempt, SQLException failure) {
        boolean refused = failure != null;
        // Capture caller identity and occurrence time before asynchronous collection can fall behind retries.
        String event = test + " connection-attempt=" + attempt + " thread=" + Thread.currentThread().threadId()
                + " occurredAt=" + Instant.now()
                + (refused ? " sqlState=" + failure.getSQLState() + " vendorCode=" + failure.getErrorCode() : " recovered");
        // The single worker serializes writes and the first-refusal gate across concurrent callers.
        executor.submit(() -> {
            if (!refused && directory == null) {
                return;
            }
            initialize();
            note(event);
            if (refused && !refusalCaptured) {
                // One initial snapshot limits collection overhead during a burst of refused connections.
                refusalCaptured = true;
                collect("connection-refused");
            }
        });
    }

    void finishTest(String name, Throwable failure) {
        executor.submit(() -> {
            // Passing tests record an outcome only after an earlier failure has triggered collection.
            if (failure == null && directory == null) {
                return;
            }
            initialize();
            note(name + " outcome=" + (failure == null ? "passed" : "failed"));
            Throwable current = failure;
            for (int depth = 0; current != null && depth < 16; depth++) {
                // Exception messages may contain SQL or credentials; record only types and Oracle error codes.
                note("exception=" + current.getClass().getName()
                        + (current instanceof SQLException sql ? " sqlState=" + sql.getSQLState()
                        + " vendorCode=" + sql.getErrorCode() : ""));
                current = current.getCause();
            }
            if (failure != null) {
                collect("failed-" + ++failures);
            }
        });
    }

    void stop() {
        try {
            // Drain queued failure/recovery events before Testcontainers stops the database, with a bounded wait.
            executor.submit(() -> {
                try {
                    if (directory != null) {
                        collect("suite-end");
                    }
                } finally {
                    closeObserver();
                }
            }).get(15, TimeUnit.SECONDS);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(System.Logger.Level.WARNING, "Oracle diagnostic collection incomplete: " + failure.getClass().getName());
        } finally {
            closing = true;
            executor.shutdownNow();
            // Also release the observer if the final task could not run before the deadline.
            if (observer.get() != null) {
                Thread.ofPlatform().daemon(true).name("oracle-diagnostic-cleanup").start(this::closeObserver);
            }
        }
    }

    private static String redact(String text) {
        Set<String> identities = new LinkedHashSet<>();
        Matcher matcher = IDENTITY.matcher(text);
        while (matcher.find()) {
            for (int group = 1; group <= matcher.groupCount(); group++) {
                String identity = matcher.group(group);
                if (identity != null && !identity.isBlank()) {
                    identities.add(identity);
                }
            }
        }
        // Remove repetitions in plain alert text as well as the original labeled fields.
        for (String identity : identities) {
            text = Pattern.compile("(?<![\\p{L}\\p{N}_.-])" + Pattern.quote(identity) + "(?![\\p{L}\\p{N}_.-])",
                                   Pattern.CASE_INSENSITIVE).matcher(text).replaceAll("<redacted>");
        }
        text = IP_ADDRESS.matcher(text).replaceAll("<redacted-ip>");
        return FILE_PATH.matcher(text).replaceAll("<redacted-path>");
    }

    private void initialize() {
        if (directory != null) {
            return;
        }
        directory = root.resolve("OracleGeneratedRepositoryTest-" + ProcessHandle.current().pid() + '-' + UUID.randomUUID());
        try {
            Files.createDirectories(directory);
            note("diagnostics-start jdk=" + Runtime.version() + " jdbc=" + OracleDriver.getDriverVersion());
        } catch (IOException failure) {
            unavailable("initialize", failure);
        }
    }

    private void collect(String phase) {
        // Capture listener evidence before opening an observer through the possibly blocked handler.
        try {
            Files.writeString(directory.resolve("container-" + phase + ".log"),
                              Instant.now() + "\n" + redact(reports.get()), StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            // Preserve partial evidence and report collection failures without replacing the test failure.
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            unavailable(phase, failure);
        }
        // Reuse one observer; later snapshots can reopen it if the initial connection was refused.
        if (observer.get() == null) {
            try {
                observer.set(connections.get());
                note("observerSessions=1 role=SYSDBA");
                query(phase, "SELECT STARTUP_TIME, VERSION, STATUS FROM V$INSTANCE");
                query("parameters", "SELECT NAME, VALUE FROM V$PARAMETER WHERE NAME IN "
                        + "('processes', 'sessions', 'transactions', 'cpu_count', 'parallel_max_servers', "
                        + "'sga_target', 'pga_aggregate_limit') ORDER BY NAME");
            } catch (SQLException | RuntimeException failure) {
                unavailable("initialize-observer", failure);
            } finally {
                if (closing) {
                    // A connection may finish opening after the final collection wait has timed out.
                    closeObserver();
                }
            }
        }
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
            // Keep database sampling bounded even when the failure involves an overloaded instance.
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

    private void note(String text) {
        try {
            Files.writeString(directory.resolve("diagnostics.log"), Instant.now() + " " + redact(text) + '\n',
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
