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

import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedCallableNullBoundaryTest {
    private JdbcClient client;
    private JdbcClient.Statement statement;
    private JdbcClient.Call call;
    private JdbcClient.CallResult result;
    private CallableRepository repository;

    @BeforeEach
    void setUp() {
        client = mock(JdbcClient.class);
        statement = mock(JdbcClient.Statement.class);
        call = mock(JdbcClient.Call.class);
        result = mock(JdbcClient.CallResult.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.call()).thenReturn(call);
        when(call.map(any())).thenAnswer(invocation -> {
            JdbcClient.CallMapper<?> mapper = invocation.getArgument(0);
            return mapper.map(result);
        });
        repository = new CallableRepository__Jdbc(client);
    }

    /**
     * A custom provider cannot bypass explicit validation of a generated callable callback argument.
     */
    @Test
    void rejectsNullCallableResultAtCallbackEntry() {
        doAnswer(invocation -> {
            JdbcClient.CallMapper<?> mapper = invocation.getArgument(0);
            return mapper.map(null);
        }).when(call).map(any());

        NullPointerException failure = assertThrows(NullPointerException.class, () -> repository.scalar(1));

        assertThat(failure.getMessage(), is("The callable result must not be null."));
    }

    /**
     * Scalar mapping validates the row before attempting a direct result read.
     */
    @Test
    void rejectsNullDirectScalarRowAtCallbackEntry() {
        when(result.resultKind()).thenReturn(JdbcClient.CallResultKind.ROWS);
        when(result.rows(any())).thenAnswer(invocation -> {
            JdbcClient.RowMapper<?> mapper = invocation.getArgument(0);
            return mapper.map(null);
        });

        NullPointerException failure = assertThrows(NullPointerException.class, () -> repository.direct("input"));

        assertThat(failure.getMessage(), is("The current row must not be null."));
    }

    /**
     * Optional cursor mapping uses the same explicit scalar row boundary as direct results.
     */
    @Test
    void rejectsNullCursorScalarRowAtCallbackEntry() {
        when(result.optionalCursor(anyString(), any())).thenAnswer(invocation -> {
            JdbcClient.RowMapper<?> mapper = invocation.getArgument(1);
            return mapper.map(null);
        });

        NullPointerException failure = assertThrows(NullPointerException.class, () -> repository.cursor("input"));

        assertThat(failure.getMessage(), is("The current row must not be null."));
    }

    /**
     * An output record cursor validates its row before reading any record component.
     */
    @Test
    void rejectsNullCursorRecordRowAtCallbackEntry() {
        when(result.get("value", Integer.class)).thenReturn(1);
        when(result.cursor(anyString(), any())).thenAnswer(invocation -> {
            JdbcClient.RowMapper<?> mapper = invocation.getArgument(1);
            return mapper.map(null);
        });

        NullPointerException failure = assertThrows(NullPointerException.class, () -> repository.aggregate(1));

        assertThat(failure.getMessage(), is("The current row must not be null."));
    }

    /**
     * The shared record mapper also validates rows supplied to an ordinary query callback.
     */
    @Test
    void rejectsNullQueryRecordRowAtCallbackEntry() {
        when(statement.map(ArgumentMatchers.<JdbcClient.RowMapper<Object>>any())).thenAnswer(invocation -> {
            JdbcClient.RowMapper<?> mapper = invocation.getArgument(0);
            return mapper.map(null);
        });
        RecordMapperReuseRepository queryRepository = new RecordMapperReuseRepository__Jdbc(client);

        NullPointerException failure = assertThrows(NullPointerException.class, queryRepository::stringValue);

        assertThat(failure.getMessage(), is("The current row must not be null."));
    }
}
