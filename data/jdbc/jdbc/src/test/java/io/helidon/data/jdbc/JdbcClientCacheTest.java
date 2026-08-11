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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import io.helidon.common.LruCache;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class JdbcClientCacheTest {

    @Test
    void admitsOnlySqlWithinTheLengthLimit() {
        DataSource dataSource = mock(DataSource.class);
        LruCache<String, Integer> cache = LruCache.create(4);
        JdbcClientImpl client = client(dataSource, cache);
        String admitted = paddedSql(JdbcClientImpl.MAX_CACHEABLE_SQL_LENGTH);
        String oversized = admitted + "x";

        assertSingleParameter(client.create(admitted));
        assertThat(cache.get(admitted), is(Optional.of(1)));
        assertSingleParameter(client.create(admitted));

        // Repeat the oversized SQL to prove that successful scans do not make it cache-admissible.
        assertSingleParameter(client.create(oversized));
        assertSingleParameter(client.create(oversized));
        assertThat(cache.get(oversized), is(Optional.empty()));
        assertThat(cache.size(), is(1));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void evictsTheLeastRecentlyUsedSql() {
        DataSource dataSource = mock(DataSource.class);
        LruCache<String, Integer> cache = LruCache.create(2);
        JdbcClientImpl client = client(dataSource, cache);
        String first = "select ? /* first */";
        String second = "select ? /* second */";
        String third = "select ? /* third */";

        client.create(first);
        client.create(second);

        // A cache hit makes the first statement most recently used, so the second must be evicted.
        client.create(first);
        client.create(third);

        assertThat(cache.get(first), is(Optional.of(1)));
        assertThat(cache.get(second), is(Optional.empty()));
        assertThat(cache.get(third), is(Optional.of(1)));
        assertThat(cache.size(), is(2));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void validationFailuresNeverPopulateTheCache() {
        DataSource dataSource = mock(DataSource.class);
        LruCache<String, Integer> cache = LruCache.create(4);
        JdbcClientImpl client = client(dataSource, cache);
        String cacheableMalformed = "select 'unterminated";
        String oversizedMalformed = cacheableMalformed
                + "x".repeat(JdbcClientImpl.MAX_CACHEABLE_SQL_LENGTH);

        assertThrows(IllegalArgumentException.class, () -> client.create(cacheableMalformed));
        assertThrows(IllegalArgumentException.class, () -> client.create(oversizedMalformed));

        assertThat(cache.get(cacheableMalformed), is(Optional.empty()));
        assertThat(cache.get(oversizedMalformed), is(Optional.empty()));
        assertThat(cache.size(), is(0));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void concurrentRepeatedSqlPreservesTheShareableClientContract() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        LruCache<String, Integer> cache = LruCache.create(8);
        JdbcClientImpl client = client(dataSource, cache);
        String sql = "select '?', \"?\", ? /* concurrent */";
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JdbcClient.Statement>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 128; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return client.create(sql);
                }));
            }
            start.countDown();

            for (Future<JdbcClient.Statement> future : futures) {
                assertSingleParameter(future.get(10, TimeUnit.SECONDS));
            }
        }

        assertThat(cache.get(sql), is(Optional.of(1)));
        assertThat(cache.size(), is(1));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void concurrentOversizedSqlIsNeverRetained() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        LruCache<String, Integer> cache = LruCache.create(8);
        JdbcClientImpl client = client(dataSource, cache);
        String sql = paddedSql(JdbcClientImpl.MAX_CACHEABLE_SQL_LENGTH) + "x";
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JdbcClient.Statement>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 128; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return client.create(sql);
                }));
            }
            start.countDown();

            for (Future<JdbcClient.Statement> future : futures) {
                assertSingleParameter(future.get(10, TimeUnit.SECONDS));
            }
        }

        assertThat(cache.get(sql), is(Optional.empty()));
        assertThat(cache.size(), is(0));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void concurrentDistinctSqlNeverExceedsCapacity() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        int capacity = 16;
        LruCache<String, Integer> cache = LruCache.create(capacity);
        JdbcClientImpl client = client(dataSource, cache);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(capacity)) {
            for (int i = 0; i < 512; i++) {
                int statementId = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    JdbcClient.Statement statement = client.create("select ? /* " + statementId + " */");
                    statement.bind(1, statementId);
                    return cache.size();
                }));
            }
            start.countDown();

            for (Future<Integer> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS), is(lessThanOrEqualTo(capacity)));
            }
        }

        assertThat(cache.size(), is(capacity));
        verifyNoMoreInteractions(dataSource);
    }

    private static JdbcClientImpl client(DataSource dataSource, LruCache<String, Integer> cache) {
        return new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider(), cache);
    }

    private static String paddedSql(int length) {
        String prefix = "select '?', \"?\", ? -- padding ";
        return prefix + "x".repeat(length - prefix.length());
    }

    private static void assertSingleParameter(JdbcClient.Statement statement) {
        statement.bind(1, 1);
        assertThrows(IllegalArgumentException.class, () -> statement.bind(2, 2));
    }
}
