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

import java.util.List;

import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedCallableMapperCollisionTest {
    /**
     * Colliding and reused mapper fields preserve their projections while renamed inputs remain bound values.
     */
    @Test
    void executesDistinctAndReusedComponentMappersWithShadowingInputs() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        JdbcClient.Call call = mock(JdbcClient.Call.class);
        JdbcClient.CallResult result = mock(JdbcClient.CallResult.class);
        JdbcClient.Row row = mock(JdbcClient.Row.class);
        JdbcClient.Rows<?> rows = mock(JdbcClient.Rows.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.call()).thenReturn(call);
        when(call.map(any())).thenAnswer(invocation -> {
            JdbcClient.CallMapper<?> mapper = invocation.getArgument(0);
            return mapper.map(result);
        });
        when(result.get("count", Integer.class)).thenReturn(0);
        when(row.get("id", Integer.class)).thenReturn(42);
        when(row.get("value", String.class)).thenReturn("ordinary");
        when(result.cursor(anyString(), any())).thenAnswer(invocation -> {
            JdbcClient.RowMapper<?> mapper = invocation.getArgument(1);
            doReturn(List.of(mapper.map(row))).when(rows).list();
            return rows;
        });
        when(statement.map(ArgumentMatchers.<JdbcClient.RowMapper<Object>>any())).thenAnswer(invocation -> {
            JdbcClient.RowMapper<?> mapper = invocation.getArgument(0);
            doReturn(mapper.map(row)).when(rows).one();
            return rows;
        });
        CallableMapperCollisionRepository repository = new CallableMapperCollisionRepository__Jdbc(client);
        var expected = new CallableMapperCollisionRepository.Outputs(
                List.of(new CallableMapperCollisionRepository.Projection(42)), 0);

        assertThat(repository.aggregate("bound input"), is(expected));
        assertThat(repository.reused("reused input"), is(expected));
        assertThat(repository.aggregate_OUTPUT_0("query input"),
                   is(new CallableMapperCollisionRepository.Other("ordinary")));
        verify(statement).bind(1, "bound input");
        verify(statement).bind(1, "reused input");
        verify(statement).bind(1, "query input");
    }
}
