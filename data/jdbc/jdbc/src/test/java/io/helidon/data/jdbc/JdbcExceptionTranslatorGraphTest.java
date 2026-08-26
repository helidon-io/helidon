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
package io.helidon.data.jdbc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Regression coverage for the bounded provider-owned exception graph produced
 * by {@link JdbcExceptionTranslator}.
 */
class JdbcExceptionTranslatorGraphTest {
    private static final String DRIVER_FAILURE = "The JDBC driver reported a failure.";
    private static final String TRUNCATION_MARKER = "Some JDBC failure relationships were not inspected or were "
            + "omitted to keep diagnostics bounded.";

    static List<Throwable> graph(Throwable root) {
        List<Throwable> result = new ArrayList<>();
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.addLast(root);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (visited.put(current, Boolean.TRUE) != null) {
                continue;
            }
            result.add(current);
            for (Throwable suppressed : current.getSuppressed()) {
                pending.addLast(suppressed);
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            if (current instanceof SQLException sqlException && sqlException.getNextException() != null) {
                pending.addLast(sqlException.getNextException());
            }
        }
        return List.copyOf(result);
    }

    static long truncationMarkers(List<Throwable> graph) {
        return graph.stream()
                .filter(node -> TRUNCATION_MARKER.equals(node.getMessage()))
                .count();
    }

    static String graphMessages(Throwable root) {
        StringBuilder result = new StringBuilder();
        for (Throwable node : graph(root)) {
            result.append(node.getMessage()).append('\n');
        }
        return result.toString();
    }

    /**
     * Proves repeated benign source nodes are represented by one provider-owned
     * copy while retaining both accepted relationships.
     */
    @Test
    void preservesSharedCauseAndNextExceptionRelationships() {
        SQLException root = sql("private root", "42000", 1);
        SQLException shared = sql("private shared", "42001", 2);
        root.initCause(shared);
        root.setNextException(shared);

        SQLException sanitized = safeCause(root);

        assertThat(sanitized.getCause(), sameInstance(sanitized.getNextException()));
        assertSafeSql((SQLException) sanitized.getCause(), "42001", 2);
        assertGraphShape(sanitized, 2, 0);
        assertThat(graphMessages(sanitized), not(containsString("private")));
    }

    /**
     * Proves a source node that is both suppressed and a next exception remains
     * reachable through the accepted next-exception edge without reading the
     * driver-owned suppressed array.
     */
    @Test
    void preservesSharedSuppressedAndNextExceptionThroughTheAcceptedNextEdge() {
        SQLException root = sql("private root", "42000", 1);
        SQLException shared = sql("private shared", "42001", 2);
        root.addSuppressed(shared);
        root.setNextException(shared);

        SQLException sanitized = safeCause(root);

        assertThat(sanitized.getSuppressed().length, is(0));
        assertSafeSql(sanitized.getNextException(), "42001", 2);
        assertGraphShape(sanitized, 2, 0);
        assertThat(graphMessages(sanitized), not(containsString("private")));
    }

    /**
     * Proves next-exception order is stable up to the bounded graph budget and
     * excess next relationships are omitted without leaking driver text.
     */
    @Test
    void preservesNextExceptionOrderUntilTheGraphBudgetIsExhausted() {
        SQLException root = sql("private next 0", "42000", 0);
        SQLException current = root;
        for (int index = 1; index <= 20; index++) {
            SQLException next = sql("private next " + index, "420%02d".formatted(index), index);
            current.setNextException(next);
            current = next;
        }

        SQLException sanitized = safeCause(root);
        List<SQLException> chain = nextChain(sanitized);

        assertThat(chain.size(), is(16));
        for (int index = 0; index < chain.size(); index++) {
            assertSafeSql(chain.get(index), "420%02d".formatted(index), index);
        }
        assertThat(chain.getLast().getNextException(), nullValue());
        assertGraphShape(sanitized, 16, 0);
        assertThat(graphMessages(sanitized), not(containsString("private")));
    }

    /**
     * Proves genuine back-edges are omitted while preserving the forward edge
     * that made the related SQL exception useful.
     */
    @Test
    void omitsBackEdgesThatWouldCreateCycles() {
        SQLException root = sql("private root", "42000", 1);
        SQLException child = sql("private child", "42001", 2);
        root.setNextException(child);
        child.setNextException(root);

        SQLException sanitized = safeCause(root);
        SQLException sanitizedChild = sanitized.getNextException();

        assertSafeSql(sanitizedChild, "42001", 2);
        assertThat(sanitizedChild.getNextException(), nullValue());
        assertGraphShape(sanitized, 2, 0);
        assertThat(graphMessages(sanitized), not(containsString("private")));
    }

    /**
     * Proves SQL and non-SQL relationships are sanitized together without
     * exposing original driver text through any accepted edge.
     */
    @Test
    void sanitizesCauseAndNextExceptionEdgesTogether() {
        SQLException root = sql("private root", "42000", 1);
        IllegalArgumentException cause = new IllegalArgumentException("private runtime cause");
        SQLException next = sql("private next", "42001", 2);
        root.initCause(cause);
        root.setNextException(next);

        SQLException sanitized = safeCause(root);

        assertThat(sanitized.getCause().getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalArgumentException' "
                              + "while processing a related JDBC failure."));
        assertSafeSql(sanitized.getNextException(), "42001", 2);
        assertGraphShape(sanitized, 3, 0);
        assertThat(graphMessages(sanitized), not(containsString("private")));
    }

    /**
     * Proves a very wide driver-owned suppressed list does not impose a heap
     * limit on the parent test JVM. The constrained heap applies only to this
     * forked process and therefore cannot affect later CI tests.
     *
     * @throws Exception when the child JVM cannot be launched or queried
     */
    @Test
    void wideSuppressedSourceCompletesInIsolatedSmallHeapFork() throws Exception {
        ProcessResult result = runWideSuppressedFork();

        assertThat(result.output(), result.exitCode(), is(0));
        assertThat(result.output(), containsString("nodes=1"));
        assertThat(result.output(), containsString("truncationMarkers=0"));
        assertThat(result.output(), containsString("suppressed=0"));
    }

    private static SQLException safeCause(SQLException source) {
        DataException failure = JdbcExceptionTranslator.translate("query", source);
        return (SQLException) failure.getCause();
    }

    private static SQLException sql(String message, String sqlState, int vendorCode) {
        return new SQLException(message, sqlState, vendorCode);
    }

    private static void assertSafeSql(SQLException actual, String sqlState, int vendorCode) {
        assertThat(actual.getMessage(), is(DRIVER_FAILURE));
        assertThat(actual.getSQLState(), is(sqlState));
        assertThat(actual.getErrorCode(), is(vendorCode));
    }

    private static void assertGraphShape(Throwable root, int nodes, int truncationMarkers) {
        List<Throwable> graph = graph(root);
        assertThat(graph.size(), is(nodes));
        assertThat(truncationMarkers(graph), is((long) truncationMarkers));
    }

    private static List<SQLException> nextChain(SQLException root) {
        List<SQLException> result = new ArrayList<>();
        SQLException current = root;
        while (current != null) {
            result.add(current);
            current = current.getNextException();
        }
        return List.copyOf(result);
    }

    private static ProcessResult runWideSuppressedFork() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(javaBinary(),
                                                    "-Xmx32m",
                                                    "-cp",
                                                    testClassPath(),
                                                    JdbcExceptionTranslatorWideSuppressedMain.class.getName());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        assertThat(completed, is(true));
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.exitValue(), output);
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"),
                       "bin",
                       System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
    }

    private static String testClassPath() {
        String classPath = System.getProperty("surefire.test.class.path");
        if (classPath == null || classPath.isBlank()) {
            return System.getProperty("java.class.path");
        }
        return classPath;
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
