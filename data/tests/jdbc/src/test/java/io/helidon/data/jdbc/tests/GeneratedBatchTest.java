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
package io.helidon.data.jdbc.tests;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.sql.DataSource;

import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class GeneratedBatchTest {
    /**
     * The generated callback validates both references before reading an item or assigning a binding.
     */
    @Test
    @SuppressWarnings("unchecked")
    void generatedBinderValidatesBothArgumentsBeforeReadingOrBinding() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        JdbcClient.BatchBindings bindings = mock(JdbcClient.BatchBindings.class);
        JdbcClient.BatchResult result = mock(JdbcClient.BatchResult.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.executeBatch(any(), any())).thenAnswer(call -> {
            JdbcClient.BatchBinder<Object[]> binder = call.getArgument(1);
            NullPointerException bothMissing = assertThrows(NullPointerException.class,
                    () -> binder.bind(null, null));
            assertThat(bothMissing.getMessage(), is("The batch bindings must not be null."));
            NullPointerException bindingsMissing = assertThrows(NullPointerException.class,
                    () -> binder.bind(null, new Object[0]));
            assertThat(bindingsMissing.getMessage(), is("The batch bindings must not be null."));
            NullPointerException itemMissing = assertThrows(NullPointerException.class,
                    () -> binder.bind(bindings, null));
            assertThat(itemMissing.getMessage(), is("The batch item must not be null."));
            verifyZeroInteractions(bindings);
            return result;
        });
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        assertThat(repository.array(new int[0], new byte[0]), sameInstance(result));
    }

    /**
     * Generated names retain application bindings and each record accessor runs once despite repeated markers.
     */
    @Test
    @SuppressWarnings("unchecked")
    void executesCapturedNamesRepeatedAndNullableValues() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        JdbcClient.BatchBindings bindings = mock(JdbcClient.BatchBindings.class);
        JdbcClient.BatchResult result = mock(JdbcClient.BatchResult.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.executeBatch(any(), any())).thenAnswer(call -> {
            Iterable<Object[]> source = call.getArgument(0);
            JdbcClient.BatchBinder<Object[]> binder = call.getArgument(1);
            int count = 0;
            for (Object[] row : source) {
                binder.bind(bindings, row);
                count++;
            }
            assertThat(count, is(2));
            return result;
        });
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        BatchCollisionRepository.Item.ACCESSES.set(0);
        assertThat(repository.insert(Arrays.asList("value", null), "constant",
                List.of(new BatchCollisionRepository.Item("first", 1),
                        new BatchCollisionRepository.Item("second", null)).iterator()), is(result));
        verify(client).create("INSERT INTO T VALUES (?, ?, ?, ?, ?, ?)");
        verify(bindings).bind(1, "value");
        verify(bindings).bind(3, "value");
        verify(bindings).bindNull(1, JDBCType.VARCHAR);
        verify(bindings).bindNull(3, JDBCType.VARCHAR);
        verify(bindings).bind(4, "first");
        verify(bindings).bind(6, "first");
        assertThat(BatchCollisionRepository.Item.ACCESSES.get(), is(2));
        verify(bindings).bindNull(5, JDBCType.INTEGER);
    }

    @Test
    void checksSourceNullBeforeStaging() {
        JdbcClient client = mock(JdbcClient.class);
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        assertThrows(NullPointerException.class, () -> repository.array(null, (byte[]) null));
        verifyZeroInteractions(client);
    }
    @Test
    void alignmentAndEmptyCapturePerformNoJdbcWork() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        assertThat(repository.array(new int[0], new byte[0]).entries().size(), is(0));
        assertThrows(IllegalArgumentException.class, () -> repository.array(new int[] {1}, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> repository.array(new int[] {1, 2}, (byte) 3));
        verifyZeroInteractions(source);
    }

    @Test
    void executesAlignedPrimitiveArraysThroughRealCapture() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[] {1, 1});
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        assertThat(repository.array(new int[] {1, 2}, (byte) 3, (byte) 4).entries().size(), is(2));
        verify(statement).setInt(1, 1);
        verify(statement).setInt(1, 2);
        verify(statement).setByte(2, (byte) 3);
        verify(statement).setByte(2, (byte) 4);
        verify(statement).executeBatch();
        verify(connection).close();
    }

    @Test
    void recordAccessorAndNullRecordFailuresPrecedeAcquisition() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        assertThrows(IllegalStateException.class, () -> repository.insert(List.of("value"), "constant",
                List.of(new BatchCollisionRepository.Item("fail", 1)).iterator()));
        assertThrows(NullPointerException.class, () -> repository.insert(List.of("value"), "constant",
                Arrays.asList((BatchCollisionRepository.Item) null).iterator()));
        verifyZeroInteractions(source);
    }

    /**
     * An application iterator failure propagates unchanged before the generated adapter acquires JDBC resources.
     */
    @Test
    void iteratorFailurePreservesIdentityBeforeAcquisition() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcClient.builder().dataSource(source).build();
        BatchCollisionRepository repository = new BatchCollisionRepository__Jdbc(client);
        IllegalStateException failure = new IllegalStateException("The application iterator failed.");
        Iterator<BatchCollisionRepository.Item> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public BatchCollisionRepository.Item next() {
                throw failure;
            }
        };
        assertThat(assertThrows(IllegalStateException.class,
                () -> repository.insert(List.of("value"), "constant", iterator)), sameInstance(failure));
        verifyZeroInteractions(source);
    }

}
