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
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class OracleGeneratedRepositoryDiagnosticsTest {
    @TempDir
    private Path output;

    @Test
    void capturesFailureEvidence() throws IOException {
        AtomicInteger closes = new AtomicInteger();
        List<String> events = new CopyOnWriteArrayList<>();
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output, () -> {
            events.add("connect");
            return connection(closes);
        }, () -> {
            events.add("report");
            return "listener evidence";
        });
        diagnostics.start();
        try {
            diagnostics.finishTest("generated repository", new SQLException("refused", "66000", 12516));
        } finally {
            diagnostics.stop();
        }
        // Listener evidence must be captured before a blocked observer connection can delay collection.
        assertThat(events, is(List.of("report", "connect", "report")));
        assertThat(closes.get(), is(1));
        Path directory = directory();
        String log = Files.readString(directory.resolve("diagnostics.log"));
        assertThat(log, containsString("observerSessions=1"));
        assertThat(log, containsString("MAX_UTILIZATION\t"));
        assertThat(log, containsString("processes\t17\t21\t300\t"));
        assertThat(log, containsString("outcome=failed"));
        assertThat(log, containsString("suite-end"));
        assertThat(Files.readString(directory.resolve("container-failed-1.log")), containsString("listener evidence"));
        assertThat(Files.readString(directory.resolve("container-suite-end.log")), containsString("listener evidence"));
    }

    @Test
    void handlesReportFailure() throws IOException {
        AtomicInteger closes = new AtomicInteger();
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output, () -> connection(closes),
                () -> {
                    throw new IOException("unavailable");
                });
        diagnostics.start();
        try {
            diagnostics.finishTest("failing repository", new SQLException("secret SQL/password", "66000", 12516));
        } finally {
            diagnostics.stop();
        }
        String log = Files.readString(directory().resolve("diagnostics.log"));
        assertThat(log, containsString("outcome=failed"));
        assertThat(log, containsString("sqlState=66000 vendorCode=12516"));
        assertThat(log, containsString("failed-1 collection-unavailable"));
        assertThat(log, not(containsString("secret")));
        assertThat(closes.get(), is(1));
    }

    @Test
    void handlesObserverFailure() throws IOException {
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output,
                () -> {
                    throw new SQLException("unavailable", "66000", 12516);
                }, () -> "listener evidence");
        diagnostics.start();
        try {
            diagnostics.finishTest("repository", new SQLException("refused", "66000", 12516));
        } finally {
            diagnostics.stop();
        }
        String log = Files.readString(directory().resolve("diagnostics.log"));
        assertThat(log, containsString("initialize-observer collection-unavailable"));
        assertThat(log, containsString("observer-unavailable"));
        assertThat(Files.readString(directory().resolve("container-suite-end.log")), containsString("listener evidence"));
    }

    @Test
    void recoversObserverAtSuiteEnd() throws IOException {
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output, () -> {
            // The diagnostic connection can encounter the same listener blockage as the application.
            if (acquisitions.incrementAndGet() == 1) {
                throw new SQLException("refused", "66000", 12516);
            }
            return connection(closes);
        }, () -> "listener evidence");
        diagnostics.start();
        try {
            diagnostics.connectionAttempt(1, new SQLException("refused", "66000", 12516));
            diagnostics.connectionAttempt(2, null);
            diagnostics.finishTest("repository", null);
        } finally {
            diagnostics.stop();
        }
        assertThat(acquisitions.get(), is(2));
        assertThat(closes.get(), is(1));
        String log = Files.readString(directory().resolve("diagnostics.log"));
        assertThat(log, containsString("initialize-observer collection-unavailable"));
        assertThat(log, containsString("observerSessions=1"));
        assertThat(log, containsString("processes\t17\t21\t300\t"));
        assertThat(log, containsString("outcome=passed"));
    }

    @Test
    void redactsIdentities() throws IOException {
        String report = """
                image=gvenzl/oracle-free:23.26.3.0-lite containerId=container-canary
                2026-10-08T12:08:24.303Z TNS Version 23.26.3.0.0
                (CONNECT_DATA=(SERVICE_NAME=service-canary)(CID=(HOST=host-canary)(USER=user-canary)))
                (ADDRESS=(PROTOCOL=tcp)(HOST=192.0.2.10)(PORT=1521))
                <msg host_id='node-canary' host_addr='2001:db8::10' user='xml-user-canary'>
                <txt>ORA-12516 host-canary node-canary user-canary service-canary</txt>
                </msg>
                Service "summary-service-canary" has 1 instance(s).
                Instance "instance-canary", status READY, has 1 handler(s) for this service...
                Default Service default-service-canary
                Alias listener-canary
                service_name=&quot;escaped-service-canary&quot;
                machine=machine-canary TARGET_LOCAL_INSTANCE=local-instance-canary
                Listening on /opt/oracle/diag/path-canary/listener.log
                peer 198.51.100.20:1521 [2001:db8:1:2:3:4:5:6]:1521 ::1 ::ffff:203.0.113.30
                handler state=blocked load=199 established=107 refused=0
                handler state=ready load=96
                """;
        AtomicInteger closes = new AtomicInteger();
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output, () -> connection(closes), () -> report);
        diagnostics.start();
        try {
            diagnostics.finishTest("repository host=event-host-canary user=event-user-canary",
                                   new SQLException("refused", "66000", 12516));
        } finally {
            diagnostics.stop();
        }
        // Exercise the actual file writers, including the final snapshot and the event log.
        Path directory = directory();
        for (String name : List.of("container-failed-1.log", "container-suite-end.log")) {
            String contents = Files.readString(directory.resolve(name));
            for (String identity : List.of("container-canary", "host-canary", "user-canary", "node-canary",
                                           "service-canary", "instance-canary", "listener-canary", "machine-canary",
                                           "path-canary", "192.0.2.10", "198.51.100.20", "2001:db8", "::1",
                                           "::ffff:203.0.113.30")) {
                assertThat(contents, not(containsString(identity)));
            }
            for (String evidence : List.of("2026-10-08T12:08:24.303Z", "23.26.3.0.0", "23.26.3.0-lite",
                                           "ORA-12516", "state=blocked load=199 established=107 refused=0",
                                           "state=ready load=96", "PORT=1521", "<txt>", "</txt>")) {
                assertThat(contents, containsString(evidence));
            }
        }
        String log = Files.readString(directory.resolve("diagnostics.log"));
        assertThat(log, not(containsString("event-host-canary")));
        assertThat(log, not(containsString("event-user-canary")));
        assertThat(log, containsString("sqlState=66000 vendorCode=12516"));
        assertThat(log, containsString("processes\t17\t21\t300\t"));
        assertThat(closes.get(), is(1));
    }

    private static Connection connection(AtomicInteger closes) {
        return (Connection) Proxy.newProxyInstance(OracleGeneratedRepositoryDiagnosticsTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (_, method, _) -> switch (method.getName()) {
                case "createStatement" -> Proxy.newProxyInstance(OracleGeneratedRepositoryDiagnosticsTest.class.getClassLoader(),
                        new Class<?>[] {Statement.class}, (_, statement, _) -> switch (statement.getName()) {
                        case "setQueryTimeout", "setMaxRows", "close" -> null;
                        case "executeQuery" -> {
                            List<String> columns = List.of("RESOURCE_NAME", "CURRENT_UTILIZATION",
                                                          "MAX_UTILIZATION", "LIMIT_VALUE");
                            RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
                            metadata.setColumnCount(columns.size());
                            for (int index = 0; index < columns.size(); index++) {
                                metadata.setColumnLabel(index + 1, columns.get(index));
                                metadata.setColumnType(index + 1, Types.VARCHAR);
                            }
                            CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
                            rows.setMetaData(metadata);
                            rows.moveToInsertRow();
                            rows.updateString(1, "processes");
                            rows.updateString(2, "17");
                            rows.updateString(3, "21");
                            rows.updateString(4, "300");
                            rows.insertRow();
                            rows.moveToCurrentRow();
                            rows.beforeFirst();
                            yield rows;
                        }
                        default -> throw new UnsupportedOperationException(statement.getName());
                        });
                case "close" -> {
                    closes.incrementAndGet();
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Path directory() throws IOException {
        try (Stream<Path> directories = Files.list(output)) {
            return directories.findFirst().orElseThrow();
        }
    }
}
