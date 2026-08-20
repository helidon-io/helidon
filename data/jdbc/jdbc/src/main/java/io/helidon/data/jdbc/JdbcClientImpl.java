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
import io.helidon.data.DataException;

/**
 * Creates statement stages for imperative applications and generated JDBC
 * repository execution.
 * <p>
 * No connection is borrowed until a terminal operation reaches
 * {@link JdbcRunner}. The client is safe to share. Each returned
 * {@link JdbcStatement} is single use and is not safe for concurrent use.
 */
final class JdbcClientImpl implements JdbcClient {

    private static final CachePolicy DEFAULT_CACHE_POLICY = new CachePolicy(256, 4_096);

    private final JdbcRunner runner;
    private final CachePolicy cachePolicy;
    private final LruCache<String, Integer> parameterCounts;

    /**
     * Creates a client with a connection-lease policy.
     *
     * @param dataSource datasource used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider) {
        this(dataSource, leaseProvider, DEFAULT_CACHE_POLICY);
    }

    /**
     * Creates a client with connection lease and cache policies.
     *
     * @param dataSource datasource used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     * @param cachePolicy parameter count cache policy
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider,
                   CachePolicy cachePolicy) {
        this.runner = new JdbcRunner(Objects.requireNonNull(dataSource, "The datasource must not be null."),
                                     Objects.requireNonNull(leaseProvider,
                                                            "The connection lease provider must not be null."));
        this.cachePolicy = Objects.requireNonNull(cachePolicy, "The parameter count cache policy must not be null.");
        this.parameterCounts = cachePolicy.capacity() == 0 ? null : LruCache.create(cachePolicy.capacity());
    }

    /**
     * Creates a new in-memory statement stage.
     *
     * <p>Imperative SQL uses positional JDBC markers. Counting those markers
     * lets the stage allocate the exact number of bind slots without preparing
     * a JDBC statement. The bounded cache benefits repeated application SQL
     * while preventing unbounded retention when callers supply dynamic SQL.
     * SQL longer than the configured admission length is still scanned
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
        LruCache<String, Integer> cache = parameterCounts;
        if (!cachePolicy.cacheable(sql) || cache == null) {
            return JdbcOperation.parameterCount(sql);
        }
        return cache.computeValue(
                        sql,
                        () -> Optional.of(JdbcOperation.parameterCount(sql)))
                .orElseThrow();
    }

    /**
     * Immutable bounds for one client's positional parameter count cache.
     *
     * @param capacity maximum retained entry count
     * @param maxSqlLength maximum admitted SQL length in UTF-16 code units
     */
    record CachePolicy(int capacity, int maxSqlLength) {

        private static final int MAX_CAPACITY = 4_096;
        private static final long MAX_RETAINED_SQL_CODE_UNITS = 16_777_216L;

        CachePolicy {
            if (capacity < 0 || capacity > MAX_CAPACITY) {
                throw new DataException("The JDBC parameter count cache capacity must be between zero and "
                                                + MAX_CAPACITY + ".");
            }
            if (maxSqlLength < 1) {
                throw new DataException("The JDBC parameter count cache maximum SQL length must be greater than zero.");
            }
            long retainedSqlCodeUnits = Math.multiplyExact((long) capacity, maxSqlLength);
            if (retainedSqlCodeUnits > MAX_RETAINED_SQL_CODE_UNITS) {
                throw new DataException("The JDBC parameter count cache can retain at most "
                                                + MAX_RETAINED_SQL_CODE_UNITS + " UTF-16 code units.");
            }
        }

        /**
         * Returns whether one SQL string may be retained by this policy.
         *
         * @param sql SQL text
         * @return whether the SQL may be retained
         */
        boolean cacheable(String sql) {
            return capacity > 0 && sql.length() <= maxSqlLength;
        }
    }
}
