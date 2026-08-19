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
import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.common.LruCache;

/**
 * Creates statement stages for imperative applications and generated JDBC
 * repository execution.
 * <p>
 * No connection is borrowed until a terminal operation reaches
 * {@link JdbcRunner}. The client is safe to share. Each returned
 * {@link JdbcStatement} is single use and is not safe for concurrent use.
 */
final class JdbcClientImpl implements JdbcClient {

    // Retain marker counts for at most 256 imperative SQL strings per client so repeated dynamic statements
    // benefit from reuse without allowing application SQL to grow the cache without limit.
    private static final int PARAMETER_COUNT_CACHE_CAPACITY = 256;

    // Admit SQL up to 4,096 UTF-16 code units and scan longer SQL without retaining it in the client cache.
    private static final int MAX_CACHEABLE_SQL_LENGTH = 4_096;

    private final JdbcRunner runner;
    private final LruCache<String, Integer> parameterCounts;

    /**
     * Creates a client with a connection-lease policy.
     *
     * @param dataSource datasource used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider) {
        this.runner = new JdbcRunner(Objects.requireNonNull(dataSource, "The datasource must not be null."),
                                     Objects.requireNonNull(leaseProvider,
                                                            "The connection lease provider must not be null."));
        this.parameterCounts = LruCache.create(PARAMETER_COUNT_CACHE_CAPACITY);
    }

    /**
     * Creates a new in-memory statement stage.
     *
     * <p>Imperative SQL uses positional JDBC markers. Counting those markers
     * lets the stage allocate the exact number of bind slots without preparing
     * a JDBC statement. The bounded cache benefits repeated application SQL
     * while preventing unbounded retention when callers supply dynamic SQL.
     * SQL longer than
     * {@value #MAX_CACHEABLE_SQL_LENGTH} UTF-16 code units is still scanned
     * normally but is never retained by the client cache.</p>
     *
     * @param sql SQL text using positional JDBC markers
     * @return a new single-use statement stage
     */
    @Override
    public Statement create(String sql) {
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        int parameterCount = parameterCount(sql);
        return new JdbcStatement(runner, sql, parameterCount);
    }

    /**
     * Creates a statement stage from a code-generation plan whose SQL and
     * physical marker count were validated at compilation time.
     *
     * @param sql validated positional SQL
     * @param parameterCount exact physical marker count
     * @return a new single-use statement stage
     */
    @Override
    public Statement create(String sql, int parameterCount) {
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        // A physical marker occupies at least one code unit. This constant-time
        // guard prevents an invalid internal caller from requesting an
        // unrelated or unbounded bind array without rescanning generated SQL.
        if (parameterCount < 0 || parameterCount > sql.length()) {
            throw new IllegalArgumentException(
                    "The JDBC parameter count must be between zero and the SQL statement length.");
        }
        return new JdbcStatement(runner, sql, parameterCount);
    }

    /**
     * Returns the positional marker count with bounded SQL retention.
     *
     * <p>The constant-time length check intentionally happens before the cache
     * is touched. It does not copy or normalize a potentially large SQL key.
     * Oversized SQL therefore receives the same lexical validation and marker
     * counting as admitted SQL without becoming reachable for the lifetime of
     * this shareable client.</p>
     *
     * <p>The common cache may invoke the supplier concurrently for the same
     * missing key. That is safe because SQL strings are immutable and marker
     * counting is deterministic. Every caller receives the same count even
     * when duplicate scans race.</p>
     *
     * @param sql non-null SQL text
     * @return positional marker count
     */
    private int parameterCount(String sql) {
        if (sql.length() > MAX_CACHEABLE_SQL_LENGTH) {
            return JdbcOperation.parameterCount(sql, JdbcLexicalProfile.PORTABLE);
        }
        return parameterCounts.computeValue(
                        sql,
                        () -> Optional.of(JdbcOperation.parameterCount(sql, JdbcLexicalProfile.PORTABLE)))
                .orElseThrow();
    }
}
