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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/**
 * Creates statement stages for the public {@link JdbcClient} contract.
 * <p>
 * No connection is borrowed until a terminal operation reaches
 * {@link JdbcRunner}. The client is safe to share. Each returned
 * {@link JdbcStatement} is single use and is not safe for concurrent use.
 */
final class JdbcClientImpl implements JdbcClient {
    /** Maximum number of distinct SQL strings retained in the marker-count cache. */
    private static final int MAX_ANALYZED_SQL = 256;

    private final JdbcRunner runner;
    private final ConcurrentHashMap<String, Integer> parameterCounts = new ConcurrentHashMap<>();
    /** Tracks reservations separately so competing insertions cannot exceed the cache limit. */
    private final AtomicInteger parameterCountEntries = new AtomicInteger();

    /**
     * Creates a client that owns connections obtained directly from the datasource.
     *
     * @param dataSource datasource used for terminal operations
     */
    JdbcClientImpl(DataSource dataSource) {
        this(dataSource, JdbcConnectionLease.ownedProvider());
    }

    /**
     * Creates a client with a connection-lease policy.
     *
     * @param dataSource datasource used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider) {
        this.runner = new JdbcRunner(Objects.requireNonNull(dataSource, "DataSource must not be null"),
                                     Objects.requireNonNull(leaseProvider, "Connection lease provider must not be null"));
    }

    /**
     * Creates a new in-memory statement stage.
     *
     * <p>Declarative code has already rewritten named parameters to JDBC
     * positional markers. Counting those markers lets the stage allocate the
     * exact number of bind slots without preparing a JDBC statement. The
     * bounded cache benefits static generated SQL while preventing unbounded
     * retention when callers supply dynamic SQL.</p>
     *
     * @param sql SQL text using positional JDBC markers
     * @return a new single-use statement stage
     */
    @Override
    public Statement create(String sql) {
        Objects.requireNonNull(sql, "SQL must not be null");
        Integer cached = parameterCounts.get(sql);
        int parameterCount;
        if (cached == null) {
            // Marker counting must match code generation without turning this runtime check into a SQL parser.
            parameterCount = JdbcOperation.parameterCount(sql);
            // Generated SQL is reused often, but callers can still supply dynamic SQL.
            if (reserveCacheEntry()) {
                Integer existing = parameterCounts.putIfAbsent(sql, parameterCount);
                if (existing != null) {
                    parameterCountEntries.decrementAndGet();
                    parameterCount = existing;
                }
            }
        } else {
            parameterCount = cached;
        }
        return new JdbcStatement(runner, sql, parameterCount);
    }

    /**
     * Reserves one cache slot without allowing dynamic SQL to exhaust memory.
     *
     * @return {@code true} when a new SQL count may be cached
     */
    private boolean reserveCacheEntry() {
        int current = parameterCountEntries.get();
        while (current < MAX_ANALYZED_SQL) {
            if (parameterCountEntries.compareAndSet(current, current + 1)) {
                return true;
            }
            current = parameterCountEntries.get();
        }
        return false;
    }
}
