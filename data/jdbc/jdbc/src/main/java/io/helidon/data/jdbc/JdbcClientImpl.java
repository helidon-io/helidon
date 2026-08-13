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
 * Creates statement stages for generated JDBC repository execution.
 * <p>
 * No connection is borrowed until a terminal operation reaches
 * {@link JdbcRunner}. The client is safe to share. Each returned
 * {@link JdbcStatement} is single use and is not safe for concurrent use.
 */
final class JdbcClientImpl implements JdbcClient {

    // Package access lets focused tests verify both cache boundaries without reflection.
    static final int PARAMETER_COUNT_CACHE_CAPACITY = 256;
    static final int MAX_CACHEABLE_SQL_LENGTH = 4_096;

    private final JdbcRunner runner;
    private final JdbcLexicalProfile lexicalProfile;
    private final LruCache<String, Integer> parameterCounts;

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
        this(dataSource,
             leaseProvider,
             JdbcLexicalProfile.PORTABLE,
             LruCache.create(PARAMETER_COUNT_CACHE_CAPACITY));
    }

    /**
     * Creates a client with an explicit parameter-count cache.
     *
     * <p>This package-private seam allows tests to use a small common LRU cache
     * and observe admission and eviction without exposing cache state through
     * the public client contract.</p>
     *
     * @param dataSource datasource used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     * @param parameterCounts cache of positional marker counts by SQL text
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider,
                   LruCache<String, Integer> parameterCounts) {
        this(dataSource, leaseProvider, JdbcLexicalProfile.PORTABLE, parameterCounts);
    }

    /**
     * Creates a client with an explicit lexical profile and parameter-count
     * cache.
     * <p>
     * The profile is fixed for the lifetime of the client, so SQL text remains
     * a sufficient cache key. This package-private constructor also establishes
     * the propagation point for a future, separately approved profile-selection
     * contract without exposing one through {@link JdbcClient} today.
     *
     * @param dataSource datasource used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     * @param lexicalProfile marker lexical profile
     * @param parameterCounts cache of positional marker counts by SQL text
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider,
                   JdbcLexicalProfile lexicalProfile,
                   LruCache<String, Integer> parameterCounts) {
        this.runner = new JdbcRunner(Objects.requireNonNull(dataSource, "The datasource must not be null."),
                                     Objects.requireNonNull(leaseProvider,
                                                            "The connection lease provider must not be null."));
        this.lexicalProfile = Objects.requireNonNull(lexicalProfile, "The JDBC lexical profile must not be null.");
        this.parameterCounts = Objects.requireNonNull(parameterCounts,
                                                      "The parameter count cache must not be null.");
    }

    /**
     * Creates a new in-memory statement stage.
     *
     * <p>Declarative code has already rewritten named parameters to JDBC
     * positional markers. Counting those markers lets the stage allocate the
     * exact number of bind slots without preparing a JDBC statement. The
     * bounded cache benefits static generated SQL while preventing unbounded
     * retention when callers supply dynamic SQL. SQL longer than
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
     * counting is deterministic; every caller receives the same count even
     * when duplicate scans race.</p>
     *
     * @param sql non-null SQL text
     * @return positional marker count
     */
    private int parameterCount(String sql) {
        if (sql.length() > MAX_CACHEABLE_SQL_LENGTH) {
            return JdbcOperation.parameterCount(sql, lexicalProfile);
        }
        return parameterCounts.computeValue(sql, () -> Optional.of(JdbcOperation.parameterCount(sql, lexicalProfile)))
                .orElseThrow();
    }
}
