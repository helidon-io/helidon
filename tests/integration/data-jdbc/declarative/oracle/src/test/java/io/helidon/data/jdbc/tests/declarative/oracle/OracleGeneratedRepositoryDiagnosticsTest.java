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
    void retainsSuccessfulRunEvidenceAndClosesTheSingleObserver() throws IOException {
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output, () -> {
            acquisitions.incrementAndGet();
            return connection(closes);
        }, () -> "listener evidence");
        diagnostics.start();
        try {
            diagnostics.finishTest("generated repository", null);
        } finally {
            diagnostics.stop();
        }
        assertThat(acquisitions.get(), is(1));
        assertThat(closes.get(), is(1));
        Path directory = directory();
        String log = Files.readString(directory.resolve("diagnostics.log"));
        assertThat(log, containsString("observerSessions=1"));
        assertThat(log, containsString("MAX_UTILIZATION\t"));
        assertThat(log, containsString("processes\t17\t21\t300\t"));
        assertThat(log, containsString("outcome=passed"));
        assertThat(log, containsString("suite-end"));
        assertThat(Files.readString(directory.resolve("container-suite-start.log")), containsString("listener evidence"));
        assertThat(Files.readString(directory.resolve("container-suite-end.log")), containsString("listener evidence"));
    }

    @Test
    void recordsTheOracleFailureEvenWhenContainerCollectionFails() throws IOException {
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
    void retainsContainerEvidenceWhenTheObserverCannotConnect() throws IOException {
        var diagnostics = new OracleGeneratedRepositoryDiagnostics(output,
                () -> {
                    throw new SQLException("unavailable", "66000", 12516);
                }, () -> "listener evidence");
        diagnostics.start();
        try {
            diagnostics.finishTest("repository", null);
        } finally {
            diagnostics.stop();
        }
        String log = Files.readString(directory().resolve("diagnostics.log"));
        assertThat(log, containsString("initialize collection-unavailable"));
        assertThat(log, containsString("observer-unavailable"));
        assertThat(Files.readString(directory().resolve("container-suite-end.log")), containsString("listener evidence"));
    }

    private static Connection connection(AtomicInteger closes) {
        return (Connection) Proxy.newProxyInstance(OracleGeneratedRepositoryDiagnosticsTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (_, method, _) -> switch (method.getName()) {
                case "createStatement" -> Proxy.newProxyInstance(OracleGeneratedRepositoryDiagnosticsTest.class.getClassLoader(),
                        new Class<?>[] {Statement.class}, (_, statement, _) -> switch (statement.getName()) {
                        case "setQueryTimeout", "setMaxRows", "close" -> null;
                        case "executeQuery" -> rows();
                        default -> throw new UnsupportedOperationException(statement.getName());
                        });
                case "close" -> {
                    closes.incrementAndGet();
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CachedRowSet rows() throws SQLException {
        List<String> columns = List.of("RESOURCE_NAME", "CURRENT_UTILIZATION", "MAX_UTILIZATION", "LIMIT_VALUE");
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
        return rows;
    }

    private Path directory() throws IOException {
        try (Stream<Path> directories = Files.list(output)) {
            return directories.findFirst().orElseThrow();
        }
    }
}
