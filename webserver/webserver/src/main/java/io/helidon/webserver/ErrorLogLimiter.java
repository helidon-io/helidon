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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.RequestException;

final class ErrorLogLimiter {
    private static final System.Logger LOGGER = System.getLogger(ErrorLogLimiter.class.getName());
    private static final int MAX_CAUSES = 4;
    private static final int MAX_FRAMES = 8;
    private static final int MAX_IDENTITY_CALCULATIONS = 4;
    private static final int MAX_REPORTED_ERRORS = 8;

    private final ErrorLogConfig config;
    private final long windowNanos;
    private final ReentrantLock lock = new ReentrantLock();
    private final Semaphore identityPermits = new Semaphore(MAX_IDENTITY_CALCULATIONS);
    private final Map<Long, ErrorState> errors = new LinkedHashMap<>();

    private long windowStart;
    private int logged;
    private long suppressed;
    private long repeated;
    private long overall;
    private long untracked;
    private long identityBusy;

    ErrorLogLimiter(ErrorLogConfig config, long now) {
        this.config = config;
        this.windowNanos = config.window().toNanos();
        this.windowStart = now;
    }

    void log(System.Logger logger, System.Logger.Level level, String message, Throwable failure, long now) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
        if (!logger.isLoggable(level)) {
            return;
        }
        if (!config.enabled()) {
            logger.log(level, message, failure);
            return;
        }
        String summary;
        long identity = 0;
        boolean calculate = false;
        boolean allowed = false;
        lock.lock();
        try {
            summary = rollover(now);
            if (!overallLimitReached()) {
                calculate = identityPermits.tryAcquire();
                if (!calculate) {
                    suppressed = increment(suppressed);
                    identityBusy = increment(identityBusy);
                }
            }
        } finally {
            lock.unlock();
        }
        if (calculate) {
            try {
                // Stack extraction may copy a full trace. Keep it outside the accounting lock and bound concurrent work.
                identity = identity(logger.getName(), message, failure);
                lock.lock();
                try {
                    // A concurrent request or report may have changed the budgets or rolled the window during extraction.
                    allowed = admit(identity);
                } finally {
                    lock.unlock();
                }
            } finally {
                identityPermits.release();
            }
        }
        // Never call a logging backend while holding the accounting lock.
        if (summary != null) {
            LOGGER.log(System.Logger.Level.WARNING, summary);
        }
        if (allowed) {
            logger.log(level, message + " [error-id=" + Long.toUnsignedString(identity, 16) + "]", failure);
        }
    }

    void report(long now) {
        String summary;
        lock.lock();
        try {
            summary = rollover(now);
        } finally {
            lock.unlock();
        }
        if (summary != null) {
            LOGGER.log(System.Logger.Level.WARNING, summary);
        }
    }

    void flush() {
        String summary;
        lock.lock();
        try {
            summary = summary();
            reset();
        } finally {
            lock.unlock();
        }
        if (summary != null) {
            LOGGER.log(System.Logger.Level.WARNING, summary);
        }
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    private static long identity(String logger, String message, Throwable failure) {
        long hash = mix(0xcbf29ce484222325L, logger);
        // Messages at the logging site are fixed diagnostics; exception messages are never inspected.
        hash = mix(hash, message);
        Throwable current = failure instanceof RequestException && failure.getCause() != null
                ? failure.getCause() : failure;
        for (int cause = 0; current != null && cause < MAX_CAUSES; cause++) {
            hash = mix(hash, current.getClass().getName());
            StackTraceElement[] stack = current.getStackTrace();
            for (int frame = 0; frame < Math.min(stack.length, MAX_FRAMES); frame++) {
                StackTraceElement element = stack[frame];
                hash = mix(hash, element.getClassName());
                hash = mix(hash, element.getMethodName());
                hash = (hash ^ element.getLineNumber()) * 0x100000001b3L;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return hash;
    }

    private static long mix(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash = (hash ^ value.charAt(i)) * 0x100000001b3L;
        }
        return (hash ^ 0xff) * 0x100000001b3L;
    }

    private boolean overallLimitReached() {
        if (logged != config.maxTotal()) {
            return false;
        }
        suppressed = increment(suppressed);
        overall = increment(overall);
        return true;
    }

    private boolean admit(long identity) {
        if (overallLimitReached()) {
            return false;
        }
        ErrorState state = errors.get(identity);
        if (state == null && errors.size() == config.maxErrors()) {
            suppressed = increment(suppressed);
            untracked = increment(untracked);
            return false;
        }
        if (state == null) {
            state = new ErrorState();
            errors.put(identity, state);
        }
        if (state.logged == config.maxPerError()) {
            state.suppressed = increment(state.suppressed);
            suppressed = increment(suppressed);
            repeated = increment(repeated);
            return false;
        }
        state.logged++;
        logged++;
        return true;
    }

    private String rollover(long now) {
        long elapsed = now - windowStart;
        if (elapsed < windowNanos) {
            return null;
        }
        String result = summary();
        reset();
        windowStart = now - elapsed % windowNanos;
        return result;
    }

    private String summary() {
        if (suppressed == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder("Suppressed ")
                .append(suppressed).append(" internal server error log entries (per-error limit: ")
                .append(repeated).append(", overall limit: ").append(overall)
                .append(", identity limit: ").append(untracked)
                .append(", identity work limit: ").append(identityBusy).append(").");
        int reported = 0;
        for (Map.Entry<Long, ErrorState> entry : errors.entrySet()) {
            if (entry.getValue().suppressed != 0 && reported < MAX_REPORTED_ERRORS) {
                result.append(" error-id=").append(Long.toUnsignedString(entry.getKey(), 16))
                        .append(": ").append(entry.getValue().suppressed);
                reported++;
            }
        }
        return result.toString();
    }

    private void reset() {
        errors.clear();
        logged = 0;
        suppressed = 0;
        repeated = 0;
        overall = 0;
        untracked = 0;
        identityBusy = 0;
    }

    private static final class ErrorState {
        private int logged;
        private long suppressed;
    }
}
