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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcRowConcurrencyTest {

    @Test
    void accessFromAnotherThreadFailsBeforeReadingTheDriver() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        JdbcRow row = newRow(resultSet);
        List<Callable<?>> reads = List.of(() -> row.optional(1, byte[].class),
                                          () -> row.optional("value", String.class),
                                          () -> row.required(1, String.class),
                                          () -> row.required("value", String.class));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (Callable<?> read : reads) {
                Future<?> result = executor.submit(read);
                ExecutionException failure = assertThrows(ExecutionException.class,
                                                          () -> result.get(5, TimeUnit.SECONDS));
                assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
                assertThat(failure.getCause().getMessage(),
                           is("A JDBC row can be read only on its mapper callback thread."));
            }

            row.expire();
            resultSet.close();

            verify(resultSet, never()).getObject(1, String.class);
            verify(resultSet, never()).getBytes(1);
            verify(resultSet).close();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expirationIsVisibleToAnotherThread() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        JdbcRow row = newRow(resultSet);
        row.expire();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> read = executor.submit(() -> row.required(1, String.class));
            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> read.get(5, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
            assertThat(failure.getCause().getMessage(),
                       is("A JDBC row is valid only during its mapper callback."));
            verify(resultSet, never()).getObject(1, String.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void callbackThreadCanReadUntilExpiration() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(1, String.class)).thenReturn("value");
        JdbcRow row = newRow(resultSet);

        assertThat(row.required(1, String.class), is("value"));
        row.expire();
        assertThrows(IllegalStateException.class, () -> row.required(1, String.class));

        verify(resultSet).getObject(1, String.class);
    }

    private static JdbcRow newRow(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        JdbcOperation operation = new JdbcOperation("SELECT VALUE",
                                                    new JdbcOperation.Bind[0],
                                                    JdbcPreparationPlan.query());
        return new JdbcRow(resultSet, JdbcColumnLayout.create(metadata, operation), operation);
    }
}
