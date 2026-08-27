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
import io.helidon.service.registry.Services;

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

    private final JdbcClientConfig prototype;
    private final JdbcRunner runner;
    private final CachePolicy cachePolicy;
    private final LruCache<String, Integer> parameterCounts;

    /**
     * Creates a client with a connection lease policy.
     *
     * @param dataSource data source used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider) {
        this(defaultPrototype(dataSource, DEFAULT_CACHE_POLICY), dataSource, leaseProvider, DEFAULT_CACHE_POLICY);
    }

    /**
     * Creates a client with connection lease and cache policies.
     *
     * @param dataSource data source used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     * @param cachePolicy parameter count cache policy
     */
    JdbcClientImpl(DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider,
                   CachePolicy cachePolicy) {
        this(defaultPrototype(dataSource, cachePolicy), dataSource, leaseProvider, cachePolicy);
    }

    /**
     * Creates a client with its source configuration and runtime policies.
     *
     * @param prototype immutable source configuration
     * @param dataSource data source used for terminal operations
     * @param leaseProvider provider that decides whether an operation owns or borrows a connection
     * @param cachePolicy parameter count cache policy
     */
    JdbcClientImpl(JdbcClientConfig prototype,
                   DataSource dataSource,
                   JdbcConnectionLease.Provider leaseProvider,
                   CachePolicy cachePolicy) {
        this.prototype = Objects.requireNonNull(prototype, "The JDBC client configuration must not be null.");
        this.runner = new JdbcRunner(Objects.requireNonNull(dataSource, "The data source must not be null."),
                                     Objects.requireNonNull(leaseProvider,
                                                            "The connection lease provider must not be null."));
        this.cachePolicy = Objects.requireNonNull(cachePolicy, "The parameter count cache policy must not be null.");
        this.parameterCounts = cachePolicy.capacity() == 0 ? null : LruCache.create(cachePolicy.capacity());
    }

    /**
     * Creates a standalone client from public configuration.
     *
     * @param config immutable JDBC client configuration
     * @return configured JDBC client
     */
    static JdbcClient create(JdbcClientConfig config) {
        Objects.requireNonNull(config, "The JDBC client configuration must not be null.");
        JdbcClientConfigSupport.validate(config);
        CachePolicy cachePolicy = JdbcProviderPropertiesSupport.create(
                Objects.requireNonNull(config.properties(), "The JDBC client properties must not be null."));
        String clientDescription = JdbcClientConfigSupport.clientDescription(config.name());
        DataSource dataSource;
        if (config.dataSourceInstance().isPresent()) {
            dataSource = config.dataSourceInstance().get();
        } else if (config.dataSource().isPresent()) {
            String dataSourceName = config.dataSource().get();
            String resolutionMessage = clientDescription + " could not resolve SQL data source '"
                    + dataSourceName + "'.";
            Optional<DataSource> resolved;
            try {
                resolved = Services.firstNamed(DataSource.class, dataSourceName);
            } catch (RuntimeException failure) {
                throw new DataException(resolutionMessage,
                                        JdbcExceptionTranslator.sanitize("resolving a SQL data source", failure));
            }
            dataSource = resolved.orElseThrow(() -> new DataException(resolutionMessage));
        } else {
            dataSource = JdbcConnectionSourceSupport.directDataSource(clientDescription,
                                                                      config.connection().orElseThrow());
        }
        return new JdbcClientImpl(config, dataSource, JdbcConnectionLease.ownedProvider(), cachePolicy);
    }

    @Override
    public JdbcClientConfig prototype() {
        return prototype;
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
    Statement createGenerated(String sql, int parameterCount) {
        return new JdbcStatement(runner, sql, parameterCount);
    }

    /**
     * Creates the immutable configuration retained by an internally
     * constructed client.
     *
     * @param dataSource data source used by the client
     * @param cachePolicy parameter count cache policy
     * @return immutable client configuration
     */
    private static JdbcClientConfig defaultPrototype(DataSource dataSource, CachePolicy cachePolicy) {
        Objects.requireNonNull(cachePolicy, "The parameter count cache policy must not be null.");
        JdbcParameterCountCacheConfig parameterCountCache = JdbcParameterCountCacheConfig.builder()
                .capacity(cachePolicy.capacity())
                .maxSqlLength(cachePolicy.maxSqlLength())
                .buildPrototype();
        JdbcProviderPropertiesConfig jdbcProperties = JdbcProviderPropertiesConfig.builder()
                .parameterCountCache(parameterCountCache)
                .buildPrototype();
        JdbcPropertiesConfig properties = JdbcPropertiesConfig.builder()
                .jdbc(jdbcProperties)
                .buildPrototype();
        return JdbcClientConfig.builder()
                .dataSource(Objects.requireNonNull(dataSource, "The data source must not be null."))
                .properties(properties)
                .buildPrototype();
    }

    /**
     * Returns the positional marker count with bounded SQL retention.
     *
     * <p>The constant time length check intentionally happens before the cache
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
     * @param sql non null SQL text
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
