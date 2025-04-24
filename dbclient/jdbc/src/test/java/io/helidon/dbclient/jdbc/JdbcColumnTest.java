/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.dbclient.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Optional;

import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.Value;
import io.helidon.dbclient.DbColumn;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcColumnTest {
    @Test
    void testNullColumn() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1))
                .thenReturn(null);
        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);
        when(rsmd.getColumnLabel(1))
                .thenReturn("testColumn");
        when(rsmd.getColumnType(1))
                .thenReturn(Types.VARCHAR);
        when(rsmd.getColumnClassName(1))
                .thenReturn(String.class.getName());

        JdbcColumn.MetaData metaData = JdbcColumn.MetaData.create(rsmd, 1);
        DbColumn col = JdbcColumn.create(rs, metaData, MapperManager.global(), 1);

        assertThat(col.asOptional(), is(Optional.empty()));
        assertThat(col.as(String.class).asOptional(), is(Optional.empty()));
        Value<String> value = col.asString();
        assertThat(value.asOptional(), is(Optional.empty()));
    }

    @Test
    void testValueColumn() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1))
                .thenReturn("value");
        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);
        when(rsmd.getColumnLabel(1))
                .thenReturn("testColumn");
        when(rsmd.getColumnType(1))
                .thenReturn(Types.VARCHAR);
        when(rsmd.getColumnClassName(1))
                .thenReturn(String.class.getName());

        JdbcColumn.MetaData metaData = JdbcColumn.MetaData.create(rsmd, 1);
        DbColumn col = JdbcColumn.create(rs, metaData, MapperManager.global(), 1);

        assertThat(col.asOptional(), is(Optional.of("value")));
        assertThat(col.get(), is("value"));
        assertThat(col.as(String.class).get(), is("value"));
        Value<String> value = col.asString();
        assertThat(value.asOptional(), is(Optional.of("value")));
    }
}
