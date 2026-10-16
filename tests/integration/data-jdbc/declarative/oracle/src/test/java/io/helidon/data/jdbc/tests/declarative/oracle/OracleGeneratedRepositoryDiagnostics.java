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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String LISTENER_TIMESTAMP = "[0-9]{2}-[A-Z]{3}-[0-9]{4} "
            + "[0-9]{2}:[0-9]{2}:[0-9]{2}(?::[0-9]{3})?";
    // Extract fields, never matching lines: a listener line may also contain credentials or connection descriptors.
    private static final Pattern SERVICE = Pattern.compile("Service \"(FREEPDB1|FREE)\" has [0-9]+ instance\\(s\\)\\.",
                                                           Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLER = Pattern.compile("\"DEDICATED\" established:([0-9]{1,19}) "
            + "refused:([0-9]{1,19}) state:(ready|blocked|unknown)");
    private static final Pattern TRANSITION = Pattern.compile("\\b(" + LISTENER_TIMESTAMP
            + ") \\* DEDICATED handler (blocked|unblocked)\\b"
            + "[^\\r\\n]*?\\bload = ([0-9]{1,19})(?![0-9])");
    private static final Pattern CONNECTION_ID = Pattern.compile("\\bCONNECTION_ID\\s*=\\s*"
            + "([A-Za-z0-9+/=_-]{1,128})(?=\\s|\\)|$)");
    private static final Pattern LISTENER_CONNECTION = Pattern.compile("\\b(" + LISTENER_TIMESTAMP
            + ") \\* [^\\r\\n]*?\\* establish \\* (FREEPDB1|FREE) \\* (0|12516)(?=\\s|<|$)",
                                                                      Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTER = Pattern.compile("(max|oom|oom_kill|oom_group_kill) ([0-9]{1,19})");
    private static final DateTimeFormatter LISTENER_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("dd-MMM-uuuu HH:mm:ss[:SSS]")
            .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    // High-water marks retain evidence of earlier load when collection begins after a refusal.
    private static final String RESOURCE_QUERY = """
            SELECT RESOURCE_NAME, CURRENT_UTILIZATION, MAX_UTILIZATION, LIMIT_VALUE
            FROM V$RESOURCE_LIMIT WHERE RESOURCE_NAME IN ('processes', 'sessions') AND CON_ID = 1
            """;
    // Raw output stays in memory. Only the structured fields extracted below may reach the artifact.
    private static final String CONTAINER_REPORT = """
            printf '=== listener ===\n'
            timeout 2 lsnrctl services
            printf '=== cgroups ===\n'
            for file in /sys/fs/cgroup/pids.current /sys/fs/cgroup/pids.peak /sys/fs/cgroup/pids.max \
                        /sys/fs/cgroup/pids.events /sys/fs/cgroup/memory.events \
                        /sys/fs/cgroup/pids/pids.current /sys/fs/cgroup/pids/pids.max \
                        /sys/fs/cgroup/pids/pids.events /sys/fs/cgroup/memory/memory.failcnt
            do
                if test -r "$file"; then printf '\n%s\n' "${file##*/}"; cat "$file"; fi
            done
            printf '\n=== history ===\n'
            timeout 2 find /opt/oracle/diag /opt/oracle/product \
                -type f \\( -name 'listener.log' -o -path '*/tnslsnr/*/alert/log.xml' \\) -print \
                | head -n 1 | while IFS= read -r file
            do
                tail -c 65536 "$file"
            done
            """;

    private final Path root = Path.of("target/failsafe-reports/oracle-generated-repository");
    private final GenericContainer<?> container;
    // Raw correlation IDs stay in memory; only generated labels are written. Cleanup can run on another thread.
    private final ConcurrentMap<String, String> connections = new ConcurrentHashMap<>();
    private final AtomicInteger connectionSequence = new AtomicInteger();
    // Final collection and timeout cleanup may race; only one task takes ownership of closing the connection.
    private final AtomicReference<Connection> observer = new AtomicReference<>();

    private ExecutorService executor;
    private Path directory;
    private volatile String test = "suite-start";
    private volatile boolean closing;
    private int failures;
    private boolean refusalCaptured;

    OracleGeneratedRepositoryDiagnostics(GenericContainer<?> container) {
        this.container = container;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        start();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        // Source method names cannot include argument values supplied through JUnit display names.
        test = context.getRequiredTestMethod().getName();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        finishTest(context.getRequiredTestMethod().getName(), context.getExecutionException().orElse(null));
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
        connections.clear();
        connectionSequence.set(0);
        test = "suite-start";
        // Prepare a serial worker; files and the observer connection are created only after a failure.
        executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true)
                .name("oracle-generated-repository-diagnostics").factory());
    }

    void connectionAttempt(int attempt, SQLException failure) {
        boolean refused = failure != null;
        // Extract the one correlation field before the driver failure is translated into a safe exception.
        String connectionId = connectionId(failure);
        // Capture caller identity and occurrence time before asynchronous collection can fall behind retries.
        String event = test + " service=test connection-attempt=" + attempt + " thread=" + Thread.currentThread().threadId()
                + " occurredAt=" + Instant.now()
                + (refused ? " sqlState=" + sqlState(failure) + " vendorCode=" + failure.getErrorCode() : " recovered");
        // The single worker serializes writes and the first-refusal gate across concurrent callers.
        executor.submit(() -> {
            if (!refused && directory == null) {
                return;
            }
            initialize();
            note(event + " connection=" + connectionLabel(connectionId));
            if (refused && !refusalCaptured) {
                refusalCaptured = true;
                collect("connection-refused");
            }
        });
    }

    void finishTest(String name, Throwable failure) {
        executor.submit(() -> {
            if (failure == null && directory == null) {
                return;
            }
            initialize();
            note(name + " outcome=" + (failure == null ? "passed" : "failed"));
            Throwable current = failure;
            for (int depth = 0; current != null && depth < 16; depth++) {
                // Driver messages and exception text are not needed; retain only validated SQL error metadata.
                if (current instanceof SQLException sql) {
                    note("sqlState=" + sqlState(sql) + " vendorCode=" + sql.getErrorCode());
                }
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
            LOGGER.log(System.Logger.Level.WARNING, "Oracle diagnostic collection incomplete");
        } finally {
            closing = true;
            executor.shutdownNow();
            if (observer.get() != null) {
                Thread.ofPlatform().daemon(true).name("oracle-diagnostic-cleanup").start(this::closeObserver);
            }
        }
    }

    private static String numeric(String value) {
        if (value != null) {
            value = value.strip();
            // Limits may use these explicit markers; never copy other database or command text.
            if ("max".equals(value) || "UNLIMITED".equals(value)) {
                return "unlimited";
            }
            if (value.matches("[0-9]{1,19}")) {
                try {
                    return Long.toString(Long.parseLong(value));
                } catch (NumberFormatException _) {
                    // An out-of-range value is unavailable, not a reason to write the raw input.
                }
            }
        }
        return "unavailable";
    }

    private static String sqlState(SQLException failure) {
        String state = failure.getSQLState();
        return state != null && state.matches("[A-Z0-9]{5}") ? state : "unavailable";
    }

    private static String connectionId(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            String message = failure.getMessage();
            if (message != null) {
                Matcher identifier = CONNECTION_ID.matcher(message);
                if (identifier.find()) {
                    return identifier.group(1);
                }
            }
        }
        return null;
    }

    private static String serviceRole(String service) {
        return switch (service.toUpperCase(Locale.ROOT)) {
        case "FREEPDB1" -> "test";
        case "FREE" -> "observer";
        default -> "unavailable";
        };
    }

    private String connectionLabel(String identifier) {
        return identifier == null ? "unavailable"
                : connections.computeIfAbsent(identifier, _ -> "connection-" + connectionSequence.incrementAndGet());
    }

    private void initialize() {
        if (directory != null) {
            return;
        }
        directory = root.resolve("OracleGeneratedRepositoryTest-" + ProcessHandle.current().pid() + '-' + UUID.randomUUID());
        try {
            Files.createDirectories(directory);
            note("diagnostics-start");
        } catch (IOException failure) {
            unavailable("initialize", failure);
        }
    }

    private void collect(String phase) {
        // Capture listener evidence before opening an observer through the possibly blocked handler.
        try {
            Container.ExecResult result = container.execInContainer("timeout", "8", "bash", "-c", CONTAINER_REPORT);
            StringBuilder report = new StringBuilder().append(Instant.now()).append('\n')
                    .append("commandExitCode=").append(result.getExitCode()).append('\n');
            String section = "";
            String metric = "";
            String role = "unavailable";
            LinkedHashSet<String> handlerServices = new LinkedHashSet<>();
            boolean metricFound = false;
            for (String line : result.getStdout().lines().toList()) {
                line = line.strip();
                if (List.of("=== listener ===", "=== cgroups ===", "=== history ===").contains(line)) {
                    section = line;
                    metric = "";
                    role = "unavailable";
                } else if ("=== listener ===".equals(section)) {
                    if (line.startsWith("Service \"")) {
                        Matcher service = SERVICE.matcher(line);
                        role = service.matches() ? serviceRole(service.group(1)) : "unavailable";
                    }
                    Matcher handler = HANDLER.matcher(line);
                    if (!"unavailable".equals(role) && handler.matches()) {
                        report.append("service=").append(role).append(" handler=DEDICATED established=")
                                .append(numeric(handler.group(1)))
                                .append(" refused=").append(numeric(handler.group(2)))
                                .append(" state=").append(handler.group(3)).append('\n');
                        handlerServices.add(role);
                    }
                } else if ("=== cgroups ===".equals(section)) {
                    if (List.of("pids.current", "pids.peak", "pids.max", "pids.events",
                                "memory.events", "memory.failcnt").contains(line)) {
                        metric = line;
                    } else if (List.of("pids.current", "pids.peak", "pids.max", "memory.failcnt").contains(metric)
                            && line.matches("[0-9]{1,19}|max")) {
                        report.append(metric).append('=').append(numeric(line)).append('\n');
                        metricFound = true;
                    } else {
                        Matcher counter = COUNTER.matcher(line);
                        if (counter.matches()
                                && ("pids.events".equals(metric) && "max".equals(counter.group(1))
                                || "memory.events".equals(metric) && !"max".equals(counter.group(1)))) {
                            report.append(metric).append('.').append(counter.group(1)).append('=')
                                    .append(numeric(counter.group(2))).append('\n');
                            metricFound = true;
                        }
                    }
                }
            }
            for (String service : List.of("test", "observer")) {
                if (!handlerServices.contains(service)) {
                    report.append("service=").append(service).append(" listener=unavailable\n");
                }
            }
            if (!metricFound) {
                report.append("cgroups=unavailable\n");
            }
            // Keep only recent transition timestamps, recognized states and numeric load; discard all surrounding text.
            LinkedHashSet<String> history = new LinkedHashSet<>();
            Matcher transition = TRANSITION.matcher(result.getStdout());
            while (transition.find()) {
                try {
                    LocalDateTime time = LocalDateTime.parse(transition.group(1), LISTENER_TIME);
                    history.add("listenerTimeLocal=" + time + " scope=instance event=" + transition.group(2)
                            + " load=" + numeric(transition.group(3)));
                    if (history.size() > 40) {
                        history.removeFirst();
                    }
                } catch (DateTimeParseException _) {
                    // Invalid timestamps are discarded, with no fallback to the matching log line.
                }
            }
            report.append(history.isEmpty() ? "listenerTransitions=unavailable\n" : String.join("\n", history) + '\n');
            // Correlate only known application/observer refusals; unrelated connections are not part of this report.
            LinkedHashSet<String> listenerConnections = new LinkedHashSet<>();
            Matcher entry = LISTENER_CONNECTION.matcher(result.getStdout());
            while (entry.find()) {
                Matcher identifier = CONNECTION_ID.matcher(entry.group());
                String label = identifier.find() ? connections.get(identifier.group(1)) : null;
                if (label != null) {
                    try {
                        LocalDateTime time = LocalDateTime.parse(entry.group(1), LISTENER_TIME);
                        listenerConnections.add("listenerTimeLocal=" + time + " service=" + serviceRole(entry.group(2))
                                + " connection=" + label + " vendorCode=" + entry.group(3));
                        if (listenerConnections.size() > 40) {
                            listenerConnections.removeFirst();
                        }
                    } catch (DateTimeParseException _) {
                        // An invalid event cannot fall back to raw listener text.
                    }
                }
            }
            report.append(listenerConnections.isEmpty() ? "listenerConnections=unavailable\n"
                                  : String.join("\n", listenerConnections) + '\n');
            // Neither stderr nor unrecognized stdout reaches this writer.
            Files.writeString(directory.resolve("container-" + phase + ".log"), report, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            unavailable(phase, failure);
        }
        // Reuse one observer; later snapshots can reopen it if the initial connection was refused.
        if (observer.get() == null) {
            try {
                Properties properties = new Properties();
                properties.setProperty("user", "sys");
                properties.setProperty("password", container.getEnvMap().get("ORACLE_PWD"));
                properties.setProperty("internal_logon", "sysdba");
                properties.setProperty("oracle.net.CONNECT_TIMEOUT", "2000");
                properties.setProperty("oracle.jdbc.ReadTimeout", "2000");
                observer.set(new OracleDriver().connect("jdbc:oracle:thin:@%s:%s/FREE"
                        .formatted(container.getHost(), container.getMappedPort(1521)), properties));
                note("observerSessions=1 service=observer");
            } catch (SQLException | RuntimeException failure) {
                unavailable("initialize-observer", failure);
            } finally {
                if (closing) {
                    closeObserver();
                }
            }
        }
        Connection connection = observer.get();
        if (connection == null) {
            note(phase + " observer-unavailable");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            statement.setMaxRows(2);
            try (ResultSet rows = statement.executeQuery(RESOURCE_QUERY)) {
                boolean sampled = false;
                while (rows.next()) {
                    String resource = rows.getString(1);
                    if (List.of("processes", "sessions").contains(resource)) {
                        note(phase + " resource=" + resource + " current=" + numeric(rows.getString(2))
                                + " maximum=" + numeric(rows.getString(3)) + " limit=" + numeric(rows.getString(4)));
                        sampled = true;
                    }
                }
                if (!sampled) {
                    note(phase + " databaseResources=unavailable");
                }
            }
        } catch (SQLException | RuntimeException failure) {
            unavailable(phase, failure);
        }
    }

    private void note(String text) {
        try {
            Files.writeString(directory.resolve("diagnostics.log"), Instant.now() + " " + text + '\n',
                              StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException _) {
            LOGGER.log(System.Logger.Level.WARNING, "Oracle diagnostics could not be written");
        }
    }

    private void unavailable(String phase, Throwable failure) {
        note(phase + " collection-unavailable"
                + (failure instanceof SQLException sql ? " service=observer connection=" + connectionLabel(connectionId(sql))
                + " sqlState=" + sqlState(sql)
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
}
