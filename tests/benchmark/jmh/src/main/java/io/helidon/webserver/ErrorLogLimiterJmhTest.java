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
package io.helidon.webserver;

import java.time.Duration;
import java.util.ResourceBundle;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Internal error suppression costs, selectable with {@code ErrorLogLimiterJmhTest.*} at one or eight threads.
 * All workers share each limiter, as requests to one server do. Fixtures are allocated only during setup.
 * The fixed monotonic timestamp keeps every operation in one window without adding clock contention.
 * Loggers accept every level but discard records: results measure accounting and identity allocation,
 * not logging backend formatting, stack-trace rendering or disk I/O. The disabled baseline measures the
 * unrestricted dispatch path to that same discard logger; it does not represent the cost of actual logging.
 */
@State(Scope.Benchmark)
public class ErrorLogLimiterJmhTest {
    private static final String MESSAGE = "Unexpected exception while processing request";
    private static final long NOW = 0;
    private static final int DISTINCT_ERRORS = 64;
    private static final System.Logger.Level LEVEL = System.Logger.Level.ERROR;

    private final DiscardLogger logger = new DiscardLogger();

    private Throwable repeatedFailure;
    private Throwable[] distinctFailures;
    private ErrorLogLimiter repeated;
    private ErrorLogLimiter identityCap;
    private ErrorLogLimiter overallCap;
    private ErrorLogLimiter disabled;

    @Setup
    public void setup() {
        repeatedFailure = failure(1);
        distinctFailures = new Throwable[DISTINCT_ERRORS];
        for (int i = 0; i < distinctFailures.length; i++) {
            distinctFailures[i] = failure(i + 2);
        }
        repeated = new ErrorLogLimiter(config(true, 1000, 100), NOW);
        identityCap = new ErrorLogLimiter(config(true, 1000, 1), NOW);
        overallCap = new ErrorLogLimiter(config(true, 1, 100), NOW);
        disabled = new ErrorLogLimiter(config(false, 1000, 100), NOW);

        // Prefill exactly one accepted identity. Repeated and identity-cap cases stay below the overall cap.
        // A separate counting logger checks setup paths without adding counters to measured logger calls.
        CountingLogger setupLogger = new CountingLogger();
        repeated.log(setupLogger, LEVEL, MESSAGE, repeatedFailure, NOW);
        identityCap.log(setupLogger, LEVEL, MESSAGE, repeatedFailure, NOW);
        overallCap.log(setupLogger, LEVEL, MESSAGE, repeatedFailure, NOW);
        if (setupLogger.logged != 3) {
            throw new IllegalStateException("Expected one initial detailed log per enabled limiter");
        }
        repeated.log(setupLogger, LEVEL, MESSAGE, repeatedFailure, NOW);
        identityCap.log(setupLogger, LEVEL, MESSAGE, distinctFailures[0], NOW);
        overallCap.log(setupLogger, LEVEL, MESSAGE, distinctFailures[0], NOW);
        if (setupLogger.logged != 3) {
            throw new IllegalStateException("Expected all prefilled limiters to suppress their benchmark input");
        }
        disabled.log(setupLogger, LEVEL, MESSAGE, repeatedFailure, NOW);
        disabled.log(setupLogger, LEVEL, MESSAGE, repeatedFailure, NOW);
        if (setupLogger.logged != 5) {
            throw new IllegalStateException("Expected disabled limiter to dispatch every record");
        }
    }

    @Benchmark
    public void repeatedBelowOverallCap() {
        repeated.log(logger, LEVEL, MESSAGE, repeatedFailure, NOW);
    }

    @Benchmark
    public void identityCapRejection(FixtureCursor cursor) {
        identityCap.log(logger, LEVEL, MESSAGE, distinctFailures[cursor.next()], NOW);
    }

    @Benchmark
    public void overallCapRejection(FixtureCursor cursor) {
        overallCap.log(logger, LEVEL, MESSAGE, distinctFailures[cursor.next()], NOW);
    }

    @Benchmark
    public void disabledUnrestrictedLogging() {
        disabled.log(logger, LEVEL, MESSAGE, repeatedFailure, NOW);
    }

    private static ErrorLogConfig config(boolean enabled, int maxTotal, int maxErrors) {
        return ErrorLogConfig.builder()
                .enabled(enabled)
                .window(Duration.ofDays(1))
                .maxPerError(1)
                .maxTotal(maxTotal)
                .maxErrors(maxErrors)
                .build();
    }

    private static Throwable failure(int line) {
        IllegalStateException failure = new IllegalStateException("fixed failure");
        StackTraceElement[] stack = new StackTraceElement[8];
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new StackTraceElement("example.RequestHandler", "handle", "RequestHandler.java", line + i);
        }
        failure.setStackTrace(stack);
        return failure;
    }

    /**
     * Each worker rotates through pre-created distinct identities without a shared atomic cursor.
     * Identities remain distinct from the identity retained during setup; messages never vary.
     */
    @State(Scope.Thread)
    public static class FixtureCursor {
        private int index;

        int next() {
            int result = index;
            index = (index + 1) % DISTINCT_ERRORS;
            return result;
        }
    }

    private static class DiscardLogger implements System.Logger {
        @Override
        public String getName() {
            return "io.helidon.webserver.ErrorLogLimiterJmhTest";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        }
    }

    private static final class CountingLogger extends DiscardLogger {
        private int logged;

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
            logged++;
        }
    }
}
