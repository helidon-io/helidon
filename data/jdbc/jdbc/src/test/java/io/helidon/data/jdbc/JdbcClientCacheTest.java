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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class JdbcClientCacheTest {

    @Test
    void supportsLongSqlWithoutAccessingTheDatasource() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientImpl client = client(dataSource);
        String sql = paddedSql(8_192);

        assertSingleParameter(client.create(sql));
        assertSingleParameter(client.create(sql));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void validationFailuresDoNotAccessTheDatasource() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientImpl client = client(dataSource);
        String malformed = "select 'unterminated";
        String longMalformed = malformed + "x".repeat(8_192);

        assertThrows(IllegalArgumentException.class, () -> client.create(malformed));
        assertThrows(IllegalArgumentException.class, () -> client.create(longMalformed));

        verifyNoMoreInteractions(dataSource);
    }

    @Test
    @SuppressWarnings("helidon:api:internal")
    void createsGeneratedStatementFromTheValidatedPhysicalMarkerCount() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientImpl client = client(dataSource);

        JdbcClient.Statement statement = client.create("select ?, '?' where ? = 1", 2);
        statement.bind(1, 1).bind(2, 2);

        assertThrows(IllegalArgumentException.class, () -> statement.bind(3, 3));
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    @SuppressWarnings("helidon:api:internal")
    void rejectsAnInvalidGeneratedParameterCountWithoutAccessingTheDatasource() {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientImpl client = client(dataSource);

        assertThrows(NullPointerException.class, () -> client.create(null, 0));
        assertThrows(IllegalArgumentException.class, () -> client.create("select 1", -1));
        assertThrows(IllegalArgumentException.class, () -> client.create("?", 2));

        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void concurrentRepeatedSqlPreservesTheShareableClientContract() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientImpl client = client(dataSource);
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

        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void concurrentDistinctSqlPreservesTheShareableClientContract() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        JdbcClientImpl client = client(dataSource);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JdbcClient.Statement>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 512; i++) {
                int statementId = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return client.create("select ? /* " + statementId + " */");
                }));
            }
            start.countDown();

            for (Future<JdbcClient.Statement> future : futures) {
                assertSingleParameter(future.get(10, TimeUnit.SECONDS));
            }
        }

        verifyNoMoreInteractions(dataSource);
    }

    private static JdbcClientImpl client(DataSource dataSource) {
        return new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());
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
